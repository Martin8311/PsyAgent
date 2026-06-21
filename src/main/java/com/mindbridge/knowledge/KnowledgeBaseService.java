package com.mindbridge.knowledge;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 知识库服务：RAG 的入库与检索核心。
 *
 * <p>入库：原文 → {@link TextChunker} 切块 → 包成 Spring AI {@link Document}
 * （带 documentId/seq 元数据）→ {@link VectorStore#add} 自动用 bge-m3 向量化并推入 Chroma；
 * 同时在 MySQL 落一条 {@link KbDocument} 台账。
 *
 * <p>检索：{@link VectorStore#similaritySearch} 走 Chroma 的 HNSW 近似最近邻，
 * 返回 top-k 文本片段，供 KnowledgeAgent 注入咨询回复。
 *
 * <p>Spring AI 的 VectorStore 是阻塞式 API，统一放到 {@code boundedElastic} 线程池，
 * 避免阻塞 WebFlux 的事件循环。
 */
@Service
public class KnowledgeBaseService {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeBaseService.class);

    private final VectorStore vectorStore;
    private final KbDocumentRepository docRepository;
    private final TextChunker chunker;
    private final KnowledgeProperties props;
    private final LexicalIndex lexicalIndex;
    private final RerankService rerankService;

    public KnowledgeBaseService(VectorStore vectorStore,
                                KbDocumentRepository docRepository,
                                TextChunker chunker,
                                KnowledgeProperties props,
                                LexicalIndex lexicalIndex,
                                RerankService rerankService) {
        this.vectorStore = vectorStore;
        this.docRepository = docRepository;
        this.chunker = chunker;
        this.props = props;
        this.lexicalIndex = lexicalIndex;
        this.rerankService = rerankService;
    }

    /** 入库一篇文档：切块 → 向量化推 Chroma → 落 MySQL 台账。 */
    public Mono<KbDocument> ingest(String title, String filename, String content) {
        return Mono.fromCallable(() -> {
            List<String> chunks = chunker.chunk(content);
            if (chunks.isEmpty()) {
                throw new IllegalArgumentException("文档内容为空或无法切块");
            }

            KbDocument doc = docRepository.save(
                    new KbDocument(title, filename, content.length(), chunks.size(), "READY", content));

            vectorStore.add(buildAiDocs(doc.getId(), title, chunks));   // 自动 embedding(bge-m3) 并写入 Chroma
            lexicalIndex.rebuild();                                      // 同步刷新 BM25 词法索引

            log.info("KB ingest done: docId={}, title='{}', chunks={}", doc.getId(), title, chunks.size());
            return doc;
        }).subscribeOn(Schedulers.boundedElastic());
    }

    /**
     * 把切块包成 Spring AI Document（带 documentId/seq/title 元数据）。
     * 当 titleInChunk 开启时，向量化文本前缀「【标题】」，让标题词参与 embedding，
     * 提升"问概念名/标题"类查询的命中率。
     */
    private List<Document> buildAiDocs(Long docId, String title, List<String> chunks) {
        List<Document> aiDocs = new ArrayList<>(chunks.size());
        for (int i = 0; i < chunks.size(); i++) {
            String embedText = props.isTitleInChunk() ? "【" + title + "】\n" + chunks.get(i) : chunks.get(i);
            Map<String, Object> metadata = new HashMap<>();
            metadata.put("documentId", docId);
            metadata.put("seq", i);
            metadata.put("title", title);
            aiDocs.add(new Document(embedText, metadata));
        }
        return aiDocs;
    }

    /**
     * 重建索引：用 MySQL 里已存的原文，按当前切块/标题策略重新切块并替换 Chroma 向量。
     * 改了切块或入库策略后调用，避免手动删文档重传。逐篇「先删旧向量、再写新向量」。
     */
    public Mono<Map<String, Object>> reindexAll() {
        return Mono.fromCallable(() -> {
            List<KbDocument> docs = docRepository.findAll();
            int docCount = 0, chunkTotal = 0;
            for (KbDocument doc : docs) {
                String content = doc.getContent();
                if (content == null || content.isBlank()) {
                    continue;
                }
                List<String> chunks = chunker.chunk(content);
                if (chunks.isEmpty()) {
                    continue;
                }
                var filter = new FilterExpressionBuilder().eq("documentId", doc.getId()).build();
                vectorStore.delete(filter);
                vectorStore.add(buildAiDocs(doc.getId(), doc.getTitle(), chunks));
                doc.setChunkCount(chunks.size());
                doc.setStatus("READY");
                docRepository.save(doc);
                docCount++;
                chunkTotal += chunks.size();
            }
            lexicalIndex.rebuild();   // 同步刷新 BM25 词法索引
            log.info("KB reindex done: docs={}, chunks={}", docCount, chunkTotal);
            Map<String, Object> r = new HashMap<>();
            r.put("docs", docCount);
            r.put("chunks", chunkTotal);
            return r;
        }).subscribeOn(Schedulers.boundedElastic());
    }

    /** 语义检索：返回与 query 最相关的 top-k 文本片段(供 Agent 注入回复)。 */
    public Mono<List<String>> search(String query) {
        return searchWithMeta(query, props.getTopK())
                .map(list -> list.stream().map(RetrievedChunk::text).toList());
    }

    /** 带元数据检索(用配置的默认 topK)。 */
    public Mono<List<RetrievedChunk>> searchWithMeta(String query) {
        return searchWithMeta(query, props.getTopK());
    }

    /**
     * 带元数据的混合检索：向量(bge-m3) + BM25 双路召回，RRF 融合 + 文档多样性降权，
     * 返回片段 + 来源文档ID + 融合分。topK 可外部指定(评测时用较大 K)。
     * 关掉 hybridEnabled 则退回纯向量。
     */
    public Mono<List<RetrievedChunk>> searchWithMeta(String query, int topK) {
        if (query == null || query.isBlank()) {
            return Mono.just(List.of());
        }
        return Mono.fromCallable(() -> {
            // 两路都先在较大候选池里召回，融合/重排后再截断到 topK，避免小 K 截断损失
            int poolK = Math.max(topK, props.getCandidateK());
            List<RetrievedChunk> dense = denseSearch(query, poolK);

            List<RetrievedChunk> ranked;
            if (props.isHybridEnabled()) {
                List<LexicalIndex.LexHit> lex = lexicalIndex.search(query, poolK);
                ranked = fuseAndDiversify(dense, lex);
            } else {
                ranked = dense;
            }
            // LLM 精排：候选多于 topK 时把前 rerankTopN 段交给对话模型重排
            if (props.isRerankEnabled() && ranked.size() > topK) {
                ranked = rerankService.rerank(query, ranked, topK);
            }
            return ranked.size() > topK ? List.copyOf(ranked.subList(0, topK)) : ranked;
        }).subscribeOn(Schedulers.boundedElastic());
    }

    /** 纯向量召回(Chroma + bge-m3)，受 minScore 阈值过滤。 */
    private List<RetrievedChunk> denseSearch(String query, int k) {
        String instr = props.getQueryInstruction();
        String q = (instr != null && !instr.isBlank()) ? instr + query : query;
        SearchRequest request = SearchRequest.builder()
                .query(q)
                .topK(k)
                .similarityThreshold(props.getMinScore())
                .build();
        List<Document> hits = vectorStore.similaritySearch(request);
        if (hits == null || hits.isEmpty()) {
            return List.of();
        }
        return hits.stream().map(d -> {
            Map<String, Object> m = d.getMetadata();
            Long docId = toLong(m.get("documentId"));
            int seq = (m.get("seq") instanceof Number n) ? n.intValue() : -1;
            String title = m.get("title") == null ? "" : m.get("title").toString();
            double score = d.getScore() == null ? 0.0 : d.getScore();
            return new RetrievedChunk(d.getText(), docId, seq, title, score);
        }).toList();
    }

    /**
     * RRF 融合两路检索结果(按 chunk 去重)，再对同源文档的多个 chunk 施加多样性降权，
     * 返回完整排序(由调用方决定重排/截断)。
     * RRF: score = Σ 1/(rrfK + rank)；降权: 同文档第 n 个 chunk × decay^n。
     */
    private List<RetrievedChunk> fuseAndDiversify(List<RetrievedChunk> dense,
                                                  List<LexicalIndex.LexHit> lex) {
        int rrfK = props.getRrfK();
        Map<String, Agg> map = new LinkedHashMap<>();
        for (int i = 0; i < dense.size(); i++) {
            RetrievedChunk c = dense.get(i);
            if (c.documentId() == null) {
                continue;
            }
            Agg a = map.computeIfAbsent(c.documentId() + ":" + c.seq(), k -> new Agg());
            a.rrf += 1.0 / (rrfK + i + 1);
            if (a.text == null) {                       // dense 文本已带【标题】前缀，优先采用
                a.docId = c.documentId();
                a.seq = c.seq();
                a.title = c.title();
                a.text = c.text();
            }
        }
        for (int i = 0; i < lex.size(); i++) {
            LexicalIndex.LexHit h = lex.get(i);
            Agg a = map.computeIfAbsent(h.documentId() + ":" + h.seq(), k -> new Agg());
            a.rrf += 1.0 / (rrfK + i + 1);
            if (a.text == null) {                       // 仅词法命中的 chunk，补上标题前缀保持一致
                a.docId = h.documentId();
                a.seq = h.seq();
                a.title = h.title();
                a.text = props.isTitleInChunk() ? "【" + h.title() + "】\n" + h.text() : h.text();
            }
        }

        List<Agg> list = new ArrayList<>(map.values());
        list.sort((x, y) -> Double.compare(y.rrf, x.rrf));
        Map<Long, Integer> seen = new HashMap<>();
        double decay = props.getDocDiversityDecay();
        for (Agg a : list) {
            int c = seen.getOrDefault(a.docId, 0);
            a.score = a.rrf * Math.pow(decay, c);
            seen.put(a.docId, c + 1);
        }
        list.sort((x, y) -> Double.compare(y.score, x.score));

        List<RetrievedChunk> out = new ArrayList<>(list.size());
        for (Agg a : list) {
            out.add(new RetrievedChunk(a.text, a.docId, a.seq, a.title,
                    Math.round(a.score * 10000.0) / 10000.0));
        }
        return out;
    }

    /** RRF 融合中转：聚合一个 chunk 来自两路的得分。 */
    private static final class Agg {
        Long docId;
        int seq;
        String title;
        String text;
        double rrf;
        double score;
    }

    private static Long toLong(Object o) {
        if (o instanceof Number n) {
            return n.longValue();
        }
        if (o instanceof String s) {
            try {
                return Long.parseLong(s.trim());
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    /** 返回单篇文档的切块列表（管理后台查看向量化内容）。 */
    public Mono<Map<String, Object>> getDocumentChunks(Long id) {
        return Mono.fromCallable(() -> {
            KbDocument doc = docRepository.findById(id)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "文档不存在"));
            List<String> chunks = (doc.getContent() == null || doc.getContent().isBlank())
                    ? List.of()
                    : chunker.chunk(doc.getContent());
            Map<String, Object> result = new HashMap<>();
            result.put("id", doc.getId());
            result.put("title", doc.getTitle());
            result.put("chunkCount", chunks.size());
            result.put("chunks", chunks);
            return result;
        }).subscribeOn(Schedulers.boundedElastic());
    }

    /** 返回单篇文档的原始内容（管理后台预览）。 */
    public Mono<Map<String, Object>> getDocumentContent(Long id) {
        return Mono.fromCallable(() -> {
            KbDocument doc = docRepository.findById(id)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "文档不存在"));
            Map<String, Object> result = new HashMap<>();
            result.put("id", doc.getId());
            result.put("title", doc.getTitle());
            result.put("content", doc.getContent() == null ? "" : doc.getContent());
            return result;
        }).subscribeOn(Schedulers.boundedElastic());
    }

    /** 文档列表（管理员后台）。 */
    public Mono<List<KbDocument>> listDocuments() {
        return Mono.fromCallable(docRepository::findAllByOrderByCreatedAtDesc)
                .subscribeOn(Schedulers.boundedElastic());
    }

    /** 删除一篇文档：先删 Chroma 中该文档的全部向量，再删 MySQL 台账。 */
    public Mono<Void> deleteDocument(Long id) {
        return Mono.fromRunnable(() -> {
            var filter = new FilterExpressionBuilder().eq("documentId", id).build();
            vectorStore.delete(filter);
            docRepository.deleteById(id);
            lexicalIndex.rebuild();   // 同步刷新 BM25 词法索引
            log.info("KB document deleted: docId={}", id);
        }).subscribeOn(Schedulers.boundedElastic()).then();
    }
}
