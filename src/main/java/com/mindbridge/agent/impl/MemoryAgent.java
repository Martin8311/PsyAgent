package com.mindbridge.agent.impl;

import com.mindbridge.agent.Agent;
import com.mindbridge.agent.AgentContext;
import com.mindbridge.memory.ChatMemoryService;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/**
 * 记忆 Agent：链路第一站，从 Redis 加载用户的会话记录(短期记忆)到上下文，
 * 供后续 Companion/Counselor 拼进 prompt，实现多轮对话。
 */
@Component
public class MemoryAgent implements Agent {

    private final ChatMemoryService chatMemoryService;

    public MemoryAgent(ChatMemoryService chatMemoryService) {
        this.chatMemoryService = chatMemoryService;
    }

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
        return chatMemoryService.loadHistory(context.getSessionId())
                .doOnNext(history -> {
                    context.getHistory().addAll(history);
                    context.setMemoryLoaded(true);
                    context.trace("memory", "loaded " + history.size() + " msgs");
                })
                .then();
    }
}
