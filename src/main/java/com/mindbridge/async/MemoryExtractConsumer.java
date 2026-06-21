package com.mindbridge.async;

import com.mindbridge.memory.MemoryExtractionService;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * 长期记忆抽取消费者：串行消费 {@code q.memory.extract}，
 * 用 LLM 从这轮对话抽取用户事实写入长期记忆。抛异常经重试后转入 DLQ。
 */
@Component
public class MemoryExtractConsumer {

    private final MemoryExtractionService extractionService;

    public MemoryExtractConsumer(MemoryExtractionService extractionService) {
        this.extractionService = extractionService;
    }

    @RabbitListener(queues = RabbitConfig.Q_MEMORY_EXTRACT, concurrency = "1")
    public void onMemoryExtract(MemoryExtractMessage message) {
        extractionService.extract(message.userId(), message.sessionId(),
                message.question(), message.answer());
        // 抽完事实后按需更新该会话摘要(内部有降频与最短长度判断)
        extractionService.summarizeIfDue(message.userId(), message.sessionId());
    }
}
