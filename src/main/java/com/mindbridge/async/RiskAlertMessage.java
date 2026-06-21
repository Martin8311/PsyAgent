package com.mindbridge.async;

/**
 * 高危预警消息（第一跳）：投递到 {@code risk.alert} 队列，
 * 消费者据此发邮件给管理员/心理老师。
 */
public record RiskAlertMessage(
        Long alertId,
        String userId,
        String level,
        String emotion,
        String userMessage,
        String reason) {
}
