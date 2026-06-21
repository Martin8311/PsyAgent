package com.mindbridge.async;

/**
 * 通知监护人消息（第二跳）：管理员后台核实后触发，投递到 {@code guardian.notify} 队列，
 * 消费者据此发邮件给学生监护人。
 */
public record GuardianNotifyMessage(
        Long alertId,
        String studentName,
        String guardianName,
        String guardianEmail,
        String level,
        String userMessage,
        String handledBy) {
}
