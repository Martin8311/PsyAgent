package com.mindbridge.knowledge;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 文本切块器：把长文档切成适合向量化的小块。
 *
 * <p>策略：先按句子边界（。！？；和换行）切句，再贪心地把句子拼进块，
 * 直到接近 {@code chunkSize}；相邻块保留 {@code chunkOverlap} 字的重叠，
 * 避免把一个完整语义从中间劈开。纯 Java 实现，零外部依赖。
 */
@Component
public class TextChunker {

    private final int chunkSize;
    private final int overlap;

    public TextChunker(KnowledgeProperties props) {
        this.chunkSize = props.getChunkSize();
        this.overlap = props.getChunkOverlap();
    }

    public List<String> chunk(String text) {
        List<String> result = new ArrayList<>();
        if (text == null || text.isBlank()) {
            return result;
        }
        String normalized = text.replace("\r\n", "\n").replace('\r', '\n').strip();

        StringBuilder buf = new StringBuilder();
        for (String sentence : splitSentences(normalized)) {
            // 当前块已有内容且再加这句会超长 → 先落一块，再带 overlap 续写
            if (buf.length() > 0 && buf.length() + sentence.length() > chunkSize) {
                result.add(buf.toString().strip());
                buf = new StringBuilder(tail(buf.toString(), overlap));
            }
            buf.append(sentence);
        }
        if (buf.length() > 0) {
            result.add(buf.toString().strip());
        }
        return result.stream().filter(s -> !s.isBlank()).toList();
    }

    /**
     * 按句末标点与换行切句；超长无标点的片段再按定长硬切，保证每句不超过 chunkSize。
     */
    private List<String> splitSentences(String text) {
        List<String> sentences = new ArrayList<>();
        StringBuilder s = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            s.append(c);
            if (isBoundary(c)) {
                sentences.add(s.toString());
                s = new StringBuilder();
            }
        }
        if (s.length() > 0) {
            sentences.add(s.toString());
        }

        List<String> normalized = new ArrayList<>();
        for (String frag : sentences) {
            if (frag.length() <= chunkSize) {
                normalized.add(frag);
            } else {
                for (int i = 0; i < frag.length(); i += chunkSize) {
                    normalized.add(frag.substring(i, Math.min(frag.length(), i + chunkSize)));
                }
            }
        }
        return normalized;
    }

    private boolean isBoundary(char c) {
        return c == '。' || c == '！' || c == '？' || c == '；'
                || c == '!' || c == '?' || c == ';' || c == '\n';
    }

    private String tail(String s, int n) {
        return s.length() <= n ? s : s.substring(s.length() - n);
    }
}
