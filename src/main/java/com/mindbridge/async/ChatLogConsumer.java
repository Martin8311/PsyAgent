package com.mindbridge.async;

import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * 对话台账消费者：串行消费 {@code q.chat.log}，写入 Excel。
 * 抛异常将经 3 次重试后转入 DLQ（见 application.yml listener 配置）。
 */
@Component
public class ChatLogConsumer {

    private final ExcelLogService excelLogService;

    public ChatLogConsumer(ExcelLogService excelLogService) {
        this.excelLogService = excelLogService;
    }

    @RabbitListener(queues = RabbitConfig.Q_CHAT_LOG, concurrency = "1")
    public void onChatLog(ChatLogMessage message) {
        excelLogService.append(message);
    }
}
