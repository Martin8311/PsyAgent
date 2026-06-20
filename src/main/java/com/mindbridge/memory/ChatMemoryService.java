package com.mindbridge.memory;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mindbridge.ai.ChatMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.List;

/**
 * 会话记录（短期记忆）服务，存储在 Redis。
 *
 * <p>结构：Redis List，key = {@code chat:history:{userId}}，
 * 每个元素是一条 {@link ChatMessage} 的 JSON。保留最近 N 条并设置 TTL 自动过期。
 * 全程使用响应式 Redis，与 WebFlux 非阻塞一致。
 */
@Service
public class ChatMemoryService {

    private static final Logger log = LoggerFactory.getLogger(ChatMemoryService.class);
    private static final String KEY_PREFIX = "chat:history:";

    private final ReactiveStringRedisTemplate redis;
    private final ObjectMapper objectMapper;
    private final int maxMessages;
    private final Duration ttl;

    public ChatMemoryService(ReactiveStringRedisTemplate redis,
                             ObjectMapper objectMapper,
                             @Value("${mindbridge.memory.max-messages:20}") int maxMessages,
                             @Value("${mindbridge.memory.ttl-days:7}") long ttlDays) {
        this.redis = redis;
        this.objectMapper = objectMapper;
        this.maxMessages = maxMessages;
        this.ttl = Duration.ofDays(ttlDays);
    }

    private String key(String userId) {
        return KEY_PREFIX + userId;
    }

    /** 读取该用户的历史消息（按时间顺序）。Redis 异常时降级为空历史。 */
    public Mono<List<ChatMessage>> loadHistory(String userId) {
        return redis.opsForList().range(key(userId), 0, -1)
                .map(this::deserialize)
                .collectList()
                .onErrorResume(e -> {
                    log.warn("加载会话记录失败，降级为空历史: {}", e.getMessage());
                    return Mono.just(List.of());
                });
    }

    /** 追加一轮对话(用户问 + AI 答)，裁剪到最近 N 条并续期 TTL。 */
    public Mono<Void> appendTurn(String userId, ChatMessage userMsg, ChatMessage assistantMsg) {
        String k = key(userId);
        return redis.opsForList()
                .rightPushAll(k, serialize(userMsg), serialize(assistantMsg))
                .flatMap(n -> redis.opsForList().trim(k, -maxMessages, -1))
                .then(redis.expire(k, ttl))
                .then()
                .onErrorResume(e -> {
                    log.warn("写入会话记录失败: {}", e.getMessage());
                    return Mono.empty();
                });
    }

    private String serialize(ChatMessage msg) {
        try {
            return objectMapper.writeValueAsString(msg);
        } catch (Exception e) {
            throw new IllegalStateException("序列化消息失败", e);
        }
    }

    private ChatMessage deserialize(String json) {
        try {
            return objectMapper.readValue(json, ChatMessage.class);
        } catch (Exception e) {
            throw new IllegalStateException("反序列化消息失败", e);
        }
    }
}
