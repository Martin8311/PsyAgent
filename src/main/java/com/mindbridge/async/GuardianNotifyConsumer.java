package com.mindbridge.async;

import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * 通知监护人消费者（第二跳）：管理员核实后发邮件给监护人。
 * 措辞温和、负责任,引导监护人联系学校心理老师,避免制造恐慌。
 */
@Component
public class GuardianNotifyConsumer {

    private final MailService mailService;

    public GuardianNotifyConsumer(MailService mailService) {
        this.mailService = mailService;
    }

    @RabbitListener(queues = RabbitConfig.Q_GUARDIAN_NOTIFY)
    public void onGuardianNotify(GuardianNotifyMessage m) {
        String guardian = (m.guardianName() == null || m.guardianName().isBlank()) ? "家长" : m.guardianName();
        String subject = "【MindBridge】关于 " + m.studentName() + " 同学的心理关怀提醒";
        String body = """
                %s 您好：

                我们是学校心理健康支持中心。在与 %s 同学的近期交流中,
                我们注意到 TA 可能正经历较大的心理压力,需要家庭的关注与陪伴。

                这是一封善意的提醒,并非要让您担忧。建议您近期多与孩子沟通,
                给予理解和支持;如有需要,欢迎随时联系学校心理老师,我们会与您一同帮助孩子。

                经办人：%s
                —— MindBridge 校园心理健康支持中心
                """.formatted(guardian, m.studentName(),
                m.handledBy() == null ? "心理老师" : m.handledBy());
        mailService.send(m.guardianEmail(), subject, body);
    }
}
