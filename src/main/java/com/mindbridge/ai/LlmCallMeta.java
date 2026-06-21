package com.mindbridge.ai;

/**
 * LLM 调用的元信息，用于 token 用量按「用户 + 功能 + 会话」归因统计。
 * userId / sessionId 可为 null（如评测生成、重排等非用户直接触发的调用）。
 */
public record LlmCallMeta(Purpose purpose, String userId, Long sessionId) {

    public enum Purpose {
        /** 学生对话(陪伴/咨询回复)。 */
        CHAT,
        /** RAG 检索结果的 LLM 重排。 */
        RERANK,
        /** 长期记忆：事实抽取。 */
        MEMORY_EXTRACT,
        /** 长期记忆：会话摘要。 */
        MEMORY_SUMMARY,
        /** 评测集问题生成。 */
        EVAL_GEN,
        /** 未标注来源。 */
        UNKNOWN
    }

    public static final LlmCallMeta UNKNOWN = new LlmCallMeta(Purpose.UNKNOWN, null, null);

    public static LlmCallMeta of(Purpose purpose) {
        return new LlmCallMeta(purpose, null, null);
    }

    public static LlmCallMeta of(Purpose purpose, String userId, Long sessionId) {
        return new LlmCallMeta(purpose, userId, sessionId);
    }
}
