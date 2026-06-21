package com.mindbridge.agent.impl;

import com.mindbridge.agent.Agent;
import com.mindbridge.agent.AgentContext;
import com.mindbridge.memory.LongTermMemoryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.List;

/**
 * 长期记忆 Agent：在短期记忆加载后、意图路由前，按 userId 召回跨会话的用户画像，
 * 写入 Context 供 Companion/Counselor 注入 prompt——让 AI 在新会话里仍记得这个人。
 *
 * <p>与意图无关(闲聊也该记得人)，故所有分支都加载。召回失败不打断链路。
 */
@Component
public class LongMemoryAgent implements Agent {

    private static final Logger log = LoggerFactory.getLogger(LongMemoryAgent.class);

    private final LongTermMemoryService memoryService;

    public LongMemoryAgent(LongTermMemoryService memoryService) {
        this.memoryService = memoryService;
    }

    @Override
    public String name() {
        return "LongMemoryAgent";
    }

    @Override
    public int order() {
        return 15;
    }

    @Override
    public boolean supports(AgentContext context) {
        return !context.isLongMemoryLoaded();
    }

    @Override
    public Mono<Void> act(AgentContext context) {
        return Mono.fromCallable(() -> memoryService.recall(context.getUserId()))
                .subscribeOn(Schedulers.boundedElastic())
                .onErrorResume(e -> {
                    log.warn("长期记忆召回失败，跳过(不影响回复): {}", e.toString());
                    return Mono.just(List.<String>of());
                })
                .doOnNext(mem -> {
                    context.getLongMemory().addAll(mem);
                    context.setLongMemoryLoaded(true);
                    context.trace("longMemory", "recalled " + mem.size() + " items");
                })
                .then();
    }
}
