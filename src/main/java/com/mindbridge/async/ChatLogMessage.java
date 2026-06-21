package com.mindbridge.async;

import java.time.Instant;

/**
 * 对话记录异步消息：投递到 {@code chat.log} 队列，由消费者写入 Excel 台账。
 * 台账按「用户 + 会话」分文件，故 username 与 sessionId 共同决定目标文件。
 */
public record ChatLogMessage(
        String username,
        Long sessionId,
        String question,
        String answer,
        String riskLevel,
        Instant timestamp) {
}
