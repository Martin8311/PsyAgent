package com.mindbridge.async;

import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * 高危预警消费者（第一跳）：发邮件通知管理员/心理老师,提示登录后台核实。
 * 此跳绝不触达监护人——监护人由管理员核实后人工触发第二跳。
 */
@Component
public class RiskAlertConsumer {

    private final MailService mailService;

    public RiskAlertConsumer(MailService mailService) {
        this.mailService = mailService;
    }

    @RabbitListener(queues = RabbitConfig.Q_RISK_ALERT)
    public void onRiskAlert(RiskAlertMessage m) {
        String subject = "【MindBridge 高危预警】学生 " + m.userId() + " 触发心理风险";
        String body = """
                检测到一条高危心理风险信号,请尽快登录管理后台核实处置。

                学生账号：%s
                风险等级：%s
                情绪标签：%s
                触发原话：%s
                研判理由：%s
                预警编号：#%d

                核实属实后,可在后台「一键通知监护人」;若为误报,请标记忽略。
                —— MindBridge 校园心理 AI 预警系统
                """.formatted(m.userId(), m.level(), m.emotion(),
                m.userMessage(), m.reason() == null ? "" : m.reason(), m.alertId());
        mailService.sendToAdmins(subject, body);
    }
}
