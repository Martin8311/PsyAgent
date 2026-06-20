package com.mindbridge.knowledge;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 知识库 / RAG 配置，对应 application.yml 中的 mindbridge.knowledge.*
 */
@ConfigurationProperties(prefix = "mindbridge.knowledge")
public class KnowledgeProperties {

    /** 检索返回的片段数(top-k)。 */
    private int topK = 3;

    /** 相似度阈值：低于此分数的片段视为不相关，不注入回答。 */
    private double minScore = 0.3;

    /** 文本切块：每块约多少字。 */
    private int chunkSize = 400;

    /** 文本切块：相邻块的重叠字数，保留上下文连续性。 */
    private int chunkOverlap = 80;

    public int getTopK() {
        return topK;
    }

    public void setTopK(int topK) {
        this.topK = topK;
    }

    public double getMinScore() {
        return minScore;
    }

    public void setMinScore(double minScore) {
        this.minScore = minScore;
    }

    public int getChunkSize() {
        return chunkSize;
    }

    public void setChunkSize(int chunkSize) {
        this.chunkSize = chunkSize;
    }

    public int getChunkOverlap() {
        return chunkOverlap;
    }

    public void setChunkOverlap(int chunkOverlap) {
        this.chunkOverlap = chunkOverlap;
    }
}
