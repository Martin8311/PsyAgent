package com.mindbridge.async;

import java.time.Instant;

/**
 * 长期记忆抽取异步消息：每轮对话结束后投递到 {@code memory.extract} 队列，
 * 由消费者用 LLM 从这轮问答里抽取「关于用户的事实」并写入长期记忆。
 * fire-and-forget，失败不影响对话主流程。
 */
public record MemoryExtractMessage(
        String userId,
        Long sessionId,
        String question,
        String answer,
        Instant timestamp) {
}
