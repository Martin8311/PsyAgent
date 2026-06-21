package com.mindbridge.async;

import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

/**
 * 异步消息发布器：业务侧只管发，写 Excel / 发邮件交给消费者异步处理。
 * 失败不影响主流程（fire-and-forget）。
 */
@Component
public class AsyncPublisher {

    private final RabbitTemplate rabbitTemplate;

    public AsyncPublisher(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    /** 对话记录 → 写 Excel 台账。 */
    public void publishChatLog(ChatLogMessage message) {
        rabbitTemplate.convertAndSend(RabbitConfig.EXCHANGE, RabbitConfig.RK_CHAT_LOG, message);
    }

    /** 高危预警(第一跳) → 发邮件给管理员。 */
    public void publishRiskAlert(RiskAlertMessage message) {
        rabbitTemplate.convertAndSend(RabbitConfig.EXCHANGE, RabbitConfig.RK_RISK_ALERT, message);
    }

    /** 通知监护人(第二跳) → 发邮件给监护人。 */
    public void publishGuardianNotify(GuardianNotifyMessage message) {
        rabbitTemplate.convertAndSend(RabbitConfig.EXCHANGE, RabbitConfig.RK_GUARDIAN_NOTIFY, message);
    }
}
