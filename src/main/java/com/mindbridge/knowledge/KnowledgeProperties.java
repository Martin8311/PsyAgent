package com.mindbridge.knowledge;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 知识库 / RAG 配置，对应 application.yml 中的 mindbridge.knowledge.*
 */
@ConfigurationProperties(prefix = "mindbridge.knowledge")
public class KnowledgeProperties {

    /** 检索返回的片段数(top-k)，重排后的最终注入数量。 */
    private int topK = 3;

    /** 召回候选数：混合检索/评测先用大 K 召回，再由重排截断到 topK。 */
    private int candidateK = 20;

    /** 相似度阈值：低于此分数的片段视为不相关，不注入回答。 */
    private double minScore = 0.3;

    /** 文本切块：每块约多少字。 */
    private int chunkSize = 500;

    /** 文本切块：相邻块的重叠字数，保留上下文连续性。 */
    private int chunkOverlap = 100;

    /** 入库时把文档标题拼进 chunk 正文一起向量化，提升"问概念名"类检索命中。 */
    private boolean titleInChunk = true;

    /**
     * 检索时给 query 加的指令前缀。bge-m3 为对称编码，通常留空；
     * 仅 bge-large-zh 等需要 "为这个句子生成表示以用于检索相关文章：" 这类前缀。
     */
    private String queryInstruction = "";

    /** 混合检索：向量(bge-m3) + BM25 关键词双路召回 + RRF 融合。关掉则退回纯向量。 */
    private boolean hybridEnabled = true;

    /** RRF 融合常数 k：分数 = Σ 1/(k + rank)，经验值 60，越大越平滑。 */
    private int rrfK = 60;

    /** 文档多样性降权：同一文档第 n 个 chunk 分数乘 decay^n，破除单文档霸榜(如 [25,26])。 */
    private double docDiversityDecay = 0.5;

    /** LLM 重排：把混合检索候选交给对话模型精排，提升 topK 命中。关掉则用融合顺序。 */
    private boolean rerankEnabled = true;

    /** 送进 LLM 重排的候选数(从融合结果取前 N 段)，越大越准但 prompt 越长越慢。 */
    private int rerankTopN = 10;

    public int getTopK() {
        return topK;
    }

    public void setTopK(int topK) {
        this.topK = topK;
    }

    public int getCandidateK() {
        return candidateK;
    }

    public void setCandidateK(int candidateK) {
        this.candidateK = candidateK;
    }

    public boolean isTitleInChunk() {
        return titleInChunk;
    }

    public void setTitleInChunk(boolean titleInChunk) {
        this.titleInChunk = titleInChunk;
    }

    public String getQueryInstruction() {
        return queryInstruction;
    }

    public void setQueryInstruction(String queryInstruction) {
        this.queryInstruction = queryInstruction;
    }

    public boolean isHybridEnabled() {
        return hybridEnabled;
    }

    public void setHybridEnabled(boolean hybridEnabled) {
        this.hybridEnabled = hybridEnabled;
    }

    public int getRrfK() {
        return rrfK;
    }

    public void setRrfK(int rrfK) {
        this.rrfK = rrfK;
    }

    public double getDocDiversityDecay() {
        return docDiversityDecay;
    }

    public void setDocDiversityDecay(double docDiversityDecay) {
        this.docDiversityDecay = docDiversityDecay;
    }

    public boolean isRerankEnabled() {
        return rerankEnabled;
    }

    public void setRerankEnabled(boolean rerankEnabled) {
        this.rerankEnabled = rerankEnabled;
    }

    public int getRerankTopN() {
        return rerankTopN;
    }

    public void setRerankTopN(int rerankTopN) {
        this.rerankTopN = rerankTopN;
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
