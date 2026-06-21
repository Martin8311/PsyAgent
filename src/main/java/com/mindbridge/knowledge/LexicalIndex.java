package com.mindbridge.knowledge;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 词法检索索引（BM25）——RAG 混合检索的「关键词」一路，兜底纯向量召回不到的查询。
 *
 * <p>纯向量(bge-m3)擅长语义近似，但对专有名词/罕见词(如"SAS 量表""述情障碍")
 * 召回不稳；BM25 基于词频精确匹配，正好互补。两路结果在
 * {@link KnowledgeBaseService} 用 RRF 融合。
 *
 * <p>实现：零外部依赖的内存倒排索引。中文用 <b>bi-gram(二元)</b> 分词
 * (如"焦虑症"→"焦虑","虑症")，英文/数字按词。chunk 与向量库同源——都来自
 * MySQL 原文经同一个 {@link TextChunker} 切块，保证两路检索的 chunk 边界一致。
 * 文档量级小(数十篇/数百块)，全量重建只需毫秒级。
 */
@Component
public class LexicalIndex implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(LexicalIndex.class);

    private static final double K1 = 1.5;
    private static final double B = 0.75;

    private final KbDocumentRepository docRepo;
    private final TextChunker chunker;

    /** 不可变快照，重建时整体替换，检索无需加锁。 */
    private volatile Index index = new Index();

    public LexicalIndex(KbDocumentRepository docRepo, TextChunker chunker) {
        this.docRepo = docRepo;
        this.chunker = chunker;
    }

    @Override
    public void run(ApplicationArguments args) {
        // 应用就绪后预热一次，避免首个查询冷启动
        try {
            rebuild();
        } catch (Exception e) {
            log.warn("词法索引预热失败(忽略，首次检索会重试): {}", e.toString());
        }
    }

    public record LexHit(long documentId, int seq, String title, String text, double score) {
    }

    private record Posting(int chunkId, int tf) {
    }

    private record ChunkMeta(long documentId, int seq, String title, String text) {
    }

    private static final class Index {
        Map<String, List<Posting>> inverted = Map.of();
        List<ChunkMeta> chunks = List.of();
        double[] docLen = new double[0];
        double avgLen = 1;
        int n = 0;
    }

    /** 从 MySQL 原文重建整个词法索引(入库/重建索引/删除文档后调用)。 */
    public synchronized void rebuild() {
        List<KbDocument> docs = docRepo.findAll();
        List<ChunkMeta> chunks = new ArrayList<>();
        Map<String, Map<Integer, Integer>> termChunkTf = new HashMap<>();
        List<Integer> lens = new ArrayList<>();

        for (KbDocument d : docs) {
            String content = d.getContent();
            if (content == null || content.isBlank()) {
                continue;
            }
            List<String> cks = chunker.chunk(content);
            for (int s = 0; s < cks.size(); s++) {
                int chunkId = chunks.size();
                String text = cks.get(s);
                chunks.add(new ChunkMeta(d.getId(), s, d.getTitle(), text));
                // 标题也纳入词法匹配，与向量侧 title-in-chunk 保持一致
                List<String> toks = tokenize(d.getTitle() + " " + text);
                lens.add(toks.size());
                Map<String, Integer> tf = new HashMap<>();
                for (String t : toks) {
                    tf.merge(t, 1, Integer::sum);
                }
                for (Map.Entry<String, Integer> en : tf.entrySet()) {
                    termChunkTf.computeIfAbsent(en.getKey(), k -> new HashMap<>()).put(chunkId, en.getValue());
                }
            }
        }

        Map<String, List<Posting>> inverted = new HashMap<>();
        for (Map.Entry<String, Map<Integer, Integer>> en : termChunkTf.entrySet()) {
            List<Posting> ps = new ArrayList<>(en.getValue().size());
            for (Map.Entry<Integer, Integer> ce : en.getValue().entrySet()) {
                ps.add(new Posting(ce.getKey(), ce.getValue()));
            }
            inverted.put(en.getKey(), ps);
        }

        int n = chunks.size();
        double[] docLen = new double[n];
        double total = 0;
        for (int i = 0; i < n; i++) {
            docLen[i] = lens.get(i);
            total += docLen[i];
        }

        Index ni = new Index();
        ni.inverted = inverted;
        ni.chunks = chunks;
        ni.docLen = docLen;
        ni.avgLen = (n == 0) ? 1 : total / n;
        ni.n = n;
        this.index = ni;
        log.info("词法索引重建完成: chunks={}, terms={}", n, inverted.size());
    }

    /** BM25 检索，返回 top-N 命中 chunk。 */
    public List<LexHit> search(String query, int topN) {
        Index idx = this.index;
        if (idx.n == 0) {
            return List.of();
        }
        List<String> qTokens = tokenize(query);
        if (qTokens.isEmpty()) {
            return List.of();
        }
        Map<Integer, Double> scores = new HashMap<>();
        for (String t : qTokens) {
            List<Posting> postings = idx.inverted.get(t);
            if (postings == null) {
                continue;
            }
            int df = postings.size();
            double idf = Math.log(1.0 + (idx.n - df + 0.5) / (df + 0.5));
            for (Posting p : postings) {
                double dl = idx.docLen[p.chunkId()];
                double denom = p.tf() + K1 * (1 - B + B * dl / idx.avgLen);
                double s = idf * (p.tf() * (K1 + 1)) / denom;
                scores.merge(p.chunkId(), s, Double::sum);
            }
        }
        if (scores.isEmpty()) {
            return List.of();
        }
        return scores.entrySet().stream()
                .sorted((a, b) -> Double.compare(b.getValue(), a.getValue()))
                .limit(topN)
                .map(e -> {
                    ChunkMeta m = idx.chunks.get(e.getKey());
                    return new LexHit(m.documentId(), m.seq(), m.title(), m.text(),
                            Math.round(e.getValue() * 10000.0) / 10000.0);
                })
                .toList();
    }

    /**
     * 分词：中文按 bi-gram(相邻二字)，英文/数字按连续 token，其余作分隔。
     * bi-gram 对中文检索召回友好，且零依赖、无需词典。
     */
    static List<String> tokenize(String text) {
        List<String> tokens = new ArrayList<>();
        if (text == null || text.isEmpty()) {
            return tokens;
        }
        StringBuilder ascii = new StringBuilder();
        StringBuilder cjk = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            char c = Character.toLowerCase(text.charAt(i));
            if (isCjk(c)) {
                if (ascii.length() > 0) {
                    tokens.add(ascii.toString());
                    ascii.setLength(0);
                }
                cjk.append(c);
            } else if (Character.isLetterOrDigit(c)) {
                if (cjk.length() > 0) {
                    addCjkGrams(cjk.toString(), tokens);
                    cjk.setLength(0);
                }
                ascii.append(c);
            } else {
                if (ascii.length() > 0) {
                    tokens.add(ascii.toString());
                    ascii.setLength(0);
                }
                if (cjk.length() > 0) {
                    addCjkGrams(cjk.toString(), tokens);
                    cjk.setLength(0);
                }
            }
        }
        if (ascii.length() > 0) {
            tokens.add(ascii.toString());
        }
        if (cjk.length() > 0) {
            addCjkGrams(cjk.toString(), tokens);
        }
        return tokens;
    }

    private static void addCjkGrams(String s, List<String> out) {
        if (s.length() == 1) {
            out.add(s);
            return;
        }
        for (int i = 0; i + 1 < s.length(); i++) {
            out.add(s.substring(i, i + 2));
        }
    }

    private static boolean isCjk(char c) {
        return c >= 0x4E00 && c <= 0x9FFF;
    }
}
