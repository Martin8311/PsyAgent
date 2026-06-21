package com.mindbridge.knowledge;

/**
 * 一条检索命中片段(带元数据)，供 RAG 评测与在线反馈判定命中、定位来源文档。
 *
 * @param text       片段文本
 * @param documentId 所属知识文档 ID(文档级判命中的依据)
 * @param seq        片段在文档内的序号
 * @param title      文档标题(冗余，展示用)
 * @param score      相似度分数(越高越相关)
 */
public record RetrievedChunk(String text, Long documentId, int seq, String title, double score) {
}
