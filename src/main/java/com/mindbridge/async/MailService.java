package com.mindbridge.async;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;

/**
 * 邮件发送服务（QQ SMTP）。
 *
 * <p>注意：QQ 邮箱要求发件人地址必须等于授权账号 {@code spring.mail.username}，
 * 否则被拒（550）。故 from 固定取配置账号。
 */
@Service
public class MailService {

    private static final Logger log = LoggerFactory.getLogger(MailService.class);

    private final JavaMailSender mailSender;
    private final String from;
    private final List<String> adminRecipients;

    public MailService(JavaMailSender mailSender,
                       @Value("${spring.mail.username}") String from,
                       @Value("${mindbridge.mail.admin-recipients:}") String admins) {
        this.mailSender = mailSender;
        this.from = from;
        this.adminRecipients = Arrays.stream(admins.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }

    public List<String> getAdminRecipients() {
        return adminRecipients;
    }

    /** 发给单个收件人。 */
    public void send(String to, String subject, String body) {
        SimpleMailMessage msg = new SimpleMailMessage();
        msg.setFrom(from);
        msg.setTo(to);
        msg.setSubject(subject);
        msg.setText(body);
        mailSender.send(msg);
        log.info("邮件已发送 → {} | {}", to, subject);
    }

    /** 群发给所有管理员/心理老师。 */
    public void sendToAdmins(String subject, String body) {
        if (adminRecipients.isEmpty()) {
            log.warn("未配置 mindbridge.mail.admin-recipients，跳过管理员预警邮件");
            return;
        }
        SimpleMailMessage msg = new SimpleMailMessage();
        msg.setFrom(from);
        msg.setTo(adminRecipients.toArray(new String[0]));
        msg.setSubject(subject);
        msg.setText(body);
        mailSender.send(msg);
        log.info("管理员预警邮件已发送 → {} | {}", adminRecipients, subject);
    }
}
