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

    public KnowledgeBaseService(VectorStore vectorStore,
                                KbDocumentRepository docRepository,
                                TextChunker chunker,
                                KnowledgeProperties props) {
        this.vectorStore = vectorStore;
        this.docRepository = docRepository;
        this.chunker = chunker;
        this.props = props;
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

            List<Document> aiDocs = new ArrayList<>(chunks.size());
            for (int i = 0; i < chunks.size(); i++) {
                Map<String, Object> metadata = new HashMap<>();
                metadata.put("documentId", doc.getId());
                metadata.put("seq", i);
                metadata.put("title", title);
                aiDocs.add(new Document(chunks.get(i), metadata));
            }
            vectorStore.add(aiDocs);   // 自动 embedding(bge-m3) 并写入 Chroma

            log.info("KB ingest done: docId={}, title='{}', chunks={}", doc.getId(), title, chunks.size());
            return doc;
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
     * 带元数据的语义检索：返回片段 + 来源文档ID + 分数，供 RAG 评测与在线反馈使用。
     * topK 可外部指定(评测时常用较大 K)，相似度阈值沿用配置。
     */
    public Mono<List<RetrievedChunk>> searchWithMeta(String query, int topK) {
        if (query == null || query.isBlank()) {
            return Mono.just(List.of());
        }
        return Mono.fromCallable(() -> {
            SearchRequest request = SearchRequest.builder()
                    .query(query)
                    .topK(topK)
                    .similarityThreshold(props.getMinScore())
                    .build();
            List<Document> hits = vectorStore.similaritySearch(request);
            if (hits == null || hits.isEmpty()) {
                return List.<RetrievedChunk>of();
            }
            return hits.stream().map(d -> {
                Map<String, Object> m = d.getMetadata();
                Long docId = toLong(m.get("documentId"));
                int seq = (m.get("seq") instanceof Number n) ? n.intValue() : -1;
                String title = m.get("title") == null ? "" : m.get("title").toString();
                double score = d.getScore() == null ? 0.0 : d.getScore();
                return new RetrievedChunk(d.getText(), docId, seq, title, score);
            }).toList();
        }).subscribeOn(Schedulers.boundedElastic());
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
            log.info("KB document deleted: docId={}", id);
        }).subscribeOn(Schedulers.boundedElastic()).then();
    }
}
