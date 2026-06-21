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

        // 以「完整句子」为单位累积成块；落块后用末尾若干整句作为重叠续写，
        // 避免旧实现的字符级硬截断把句子从中间劈开、污染向量。
        List<String> cur = new ArrayList<>();
        int curLen = 0;
        for (String sentence : splitSentences(normalized)) {
            if (curLen > 0 && curLen + sentence.length() > chunkSize) {
                result.add(String.join("", cur).strip());
                List<String> carry = tailSentences(cur, overlap);
                cur = new ArrayList<>(carry);
                curLen = carry.stream().mapToInt(String::length).sum();
            }
            cur.add(sentence);
            curLen += sentence.length();
        }
        if (curLen > 0) {
            result.add(String.join("", cur).strip());
        }
        return result.stream().filter(s -> !s.isBlank()).toList();
    }

    /**
     * 取句子列表末尾、累计长度约为 overlap 的若干「完整句子」作为下一块的重叠开头。
     * 上限封顶为半个 chunkSize，并跳过超长硬切句，避免超长无标点文本下重叠退化/重复落块。
     */
    private List<String> tailSentences(List<String> sentences, int targetOverlap) {
        List<String> carry = new ArrayList<>();
        int len = 0;
        int cap = Math.max(1, Math.min(chunkSize / 2, targetOverlap));
        for (int i = sentences.size() - 1; i >= 0; i--) {
            String s = sentences.get(i);
            if (s.length() >= chunkSize) {
                break;                 // 超长硬切句不参与重叠
            }
            if (len > 0 && len + s.length() > cap) {
                break;
            }
            carry.add(0, s);
            len += s.length();
            if (len >= targetOverlap) {
                break;
            }
        }
        return carry;
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
}
