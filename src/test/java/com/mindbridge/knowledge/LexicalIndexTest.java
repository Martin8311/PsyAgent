package com.mindbridge.knowledge;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * LexicalIndex 中文 bi-gram 分词单元测试。tokenize 为包级静态方法，同包可测。
 */
class LexicalIndexTest {

    @Test
    void cjkSplitIntoBigrams() {
        List<String> t = LexicalIndex.tokenize("焦虑症");
        assertTrue(t.contains("焦虑"));
        assertTrue(t.contains("虑症"));
    }

    @Test
    void singleCjkCharKept() {
        List<String> t = LexicalIndex.tokenize("我");
        assertTrue(t.contains("我"));
    }

    @Test
    void asciiTokenizedAndLowercased() {
        List<String> t = LexicalIndex.tokenize("SAS量表 test");
        assertTrue(t.contains("sas"), "英文应小写");
        assertTrue(t.contains("test"));
        assertTrue(t.contains("量表"), "中英混合中文段仍按 bigram");
    }

    @Test
    void emptyOrNullReturnsEmpty() {
        assertTrue(LexicalIndex.tokenize("").isEmpty());
        assertTrue(LexicalIndex.tokenize(null).isEmpty());
    }
}
