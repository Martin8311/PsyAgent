package com.mindbridge.async;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RabbitMQ 拓扑：一个 Topic Exchange + 3 个业务队列，每队列挂死信队列(DLQ)。
 *
 * <p>队列/交换机均带 {@code mindbridge.} 前缀，且运行在独立 vhost {@code mindbridge}，
 * 与同实例上的其他应用（如 poker）彻底隔离。
 *
 * <ul>
 *   <li>chat.log       → 对话记录写 Excel 台账</li>
 *   <li>risk.alert     → 高危第一跳：发邮件给管理员</li>
 *   <li>guardian.notify→ 高危第二跳：管理员核实后通知监护人</li>
 * </ul>
 */
@Configuration
public class RabbitConfig {

    public static final String EXCHANGE = "mindbridge.async";
    public static final String DLX = "mindbridge.async.dlx";

    public static final String Q_CHAT_LOG = "mindbridge.q.chat.log";
    public static final String Q_RISK_ALERT = "mindbridge.q.risk.alert";
    public static final String Q_GUARDIAN_NOTIFY = "mindbridge.q.guardian.notify";

    public static final String RK_CHAT_LOG = "chat.log";
    public static final String RK_RISK_ALERT = "risk.alert";
    public static final String RK_GUARDIAN_NOTIFY = "guardian.notify";

    @Bean
    public TopicExchange asyncExchange() {
        return new TopicExchange(EXCHANGE, true, false);
    }

    @Bean
    public DirectExchange deadLetterExchange() {
        return new DirectExchange(DLX, true, false);
    }

    private Queue durableWithDlx(String name) {
        return QueueBuilder.durable(name)
                .withArgument("x-dead-letter-exchange", DLX)
                .withArgument("x-dead-letter-routing-key", name + ".dlq")
                .build();
    }

    @Bean
    public Queue chatLogQueue() {
        return durableWithDlx(Q_CHAT_LOG);
    }

    @Bean
    public Queue riskAlertQueue() {
        return durableWithDlx(Q_RISK_ALERT);
    }

    @Bean
    public Queue guardianNotifyQueue() {
        return durableWithDlx(Q_GUARDIAN_NOTIFY);
    }

    @Bean
    public Queue chatLogDlq() {
        return QueueBuilder.durable(Q_CHAT_LOG + ".dlq").build();
    }

    @Bean
    public Queue riskAlertDlq() {
        return QueueBuilder.durable(Q_RISK_ALERT + ".dlq").build();
    }

    @Bean
    public Queue guardianNotifyDlq() {
        return QueueBuilder.durable(Q_GUARDIAN_NOTIFY + ".dlq").build();
    }

    @Bean
    public Binding bindChatLog() {
        return BindingBuilder.bind(chatLogQueue()).to(asyncExchange()).with(RK_CHAT_LOG);
    }

    @Bean
    public Binding bindRiskAlert() {
        return BindingBuilder.bind(riskAlertQueue()).to(asyncExchange()).with(RK_RISK_ALERT);
    }

    @Bean
    public Binding bindGuardianNotify() {
        return BindingBuilder.bind(guardianNotifyQueue()).to(asyncExchange()).with(RK_GUARDIAN_NOTIFY);
    }

    @Bean
    public Binding bindChatLogDlq() {
        return BindingBuilder.bind(chatLogDlq()).to(deadLetterExchange()).with(Q_CHAT_LOG + ".dlq");
    }

    @Bean
    public Binding bindRiskAlertDlq() {
        return BindingBuilder.bind(riskAlertDlq()).to(deadLetterExchange()).with(Q_RISK_ALERT + ".dlq");
    }

    @Bean
    public Binding bindGuardianNotifyDlq() {
        return BindingBuilder.bind(guardianNotifyDlq()).to(deadLetterExchange()).with(Q_GUARDIAN_NOTIFY + ".dlq");
    }

    /**
     * JSON 消息转换器：RabbitTemplate 发送与 @RabbitListener 消费均自动采用
     * （Spring Boot 检测到唯一 MessageConverter Bean 后会注入两端）。
     */
    @Bean
    public MessageConverter jacksonMessageConverter(ObjectMapper objectMapper) {
        return new Jackson2JsonMessageConverter(objectMapper);
    }
}
