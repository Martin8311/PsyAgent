package com.mindbridge.knowledge;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * TextChunker 切块逻辑单元测试（纯逻辑，无外部依赖）。
 */
class TextChunkerTest {

    private TextChunker chunker(int size, int overlap) {
        KnowledgeProperties p = new KnowledgeProperties();
        p.setChunkSize(size);
        p.setChunkOverlap(overlap);
        return new TextChunker(p);
    }

    @Test
    void emptyOrBlankReturnsEmpty() {
        assertTrue(chunker(100, 20).chunk("").isEmpty());
        assertTrue(chunker(100, 20).chunk(null).isEmpty());
        assertTrue(chunker(100, 20).chunk("   ").isEmpty());
    }

    @Test
    void shortTextStaysSingleChunk() {
        List<String> c = chunker(100, 20).chunk("我今天很开心。");
        assertEquals(1, c.size());
        assertTrue(c.get(0).contains("开心"));
    }

    @Test
    void longTextSplitsIntoMultipleChunks() {
        String s = "第一句话内容。第二句话内容。第三句话内容。第四句话内容。";
        List<String> c = chunker(12, 4).chunk(s);
        assertTrue(c.size() > 1, "超过 chunkSize 的多句应被切成多块");
    }

    @Test
    void noBlankChunksProduced() {
        List<String> c = chunker(10, 3).chunk("啊。。。哦。");
        assertTrue(c.stream().noneMatch(String::isBlank));
    }
}
