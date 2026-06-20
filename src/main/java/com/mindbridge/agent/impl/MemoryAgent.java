package com.mindbridge.agent.impl;

import com.mindbridge.agent.Agent;
import com.mindbridge.agent.AgentContext;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/**
 * 记忆 Agent：链路第一站，加载用户的短期/长期历史记忆到上下文。
 *
 * <p>Phase 2 先占位（标记记忆已加载）；Phase 3 接入 Redis 短期会话记忆
 * 与 JPA 长期消息持久化。
 */
@Component
public class MemoryAgent implements Agent {

    @Override
    public String name() {
        return "MemoryAgent";
    }

    @Override
    public int order() {
        return 10;
    }

    @Override
    public boolean supports(AgentContext context) {
        return !context.isMemoryLoaded();
    }

    @Override
    public Mono<Void> act(AgentContext context) {
        return Mono.fromRunnable(() -> {
            // TODO Phase 3: 从 Redis 加载短期记忆、从 DB 加载长期消息写入 history
            context.setMemoryLoaded(true);
            context.trace("memory", "loaded(empty in Phase 2)");
        });
    }
}
