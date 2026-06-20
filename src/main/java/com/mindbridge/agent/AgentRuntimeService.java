package com.mindbridge.agent;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Comparator;
import java.util.List;

/**
 * 多 Agent 统一调度器。
 *
 * <p>路由式有限状态机：每一步从所有 Agent 中挑出第一个 {@code supports(ctx)}
 * 为 true 的 Agent 执行 {@code act(ctx)}，由其修改共享 Context 推进状态；
 * 直到产生回复流(responseStream) 或没有 Agent 可处理。
 *
 * <p>循环最多 {@link #MAX_STEPS} 步，防止 Agent 间相互触发导致失控。
 */
@Service
public class AgentRuntimeService {

    private static final Logger log = LoggerFactory.getLogger(AgentRuntimeService.class);

    /** 单次会话最多调度步数，避免死循环。 */
    public static final int MAX_STEPS = 8;

    private final List<Agent> agents;

    public AgentRuntimeService(List<Agent> agents) {
        // 按 order 排序，保证记忆→路由→检索→风控→回复的推进顺序
        this.agents = agents.stream()
                .sorted(Comparator.comparingInt(Agent::order))
                .toList();
        log.info("AgentRuntime loaded {} agents: {}",
                this.agents.size(), this.agents.stream().map(Agent::name).toList());
    }

    /**
     * 运行一次完整的多 Agent 协作，返回最终的流式回复。
     */
    public Flux<String> run(AgentContext context) {
        return driveLoop(context, 0)
                .thenMany(Flux.defer(() -> {
                    Flux<String> response = context.getResponseStream();
                    if (response == null) {
                        log.warn("No agent produced a response. trace={}", context.getTrace());
                        return Flux.just("抱歉，我现在有点不知道怎么回应，可以换个说法再和我说说吗？");
                    }
                    return response;
                }));
    }

    /** 递归驱动调度循环。 */
    private Mono<Void> driveLoop(AgentContext context, int step) {
        if (step >= MAX_STEPS || context.hasResponse()) {
            if (step >= MAX_STEPS) {
                log.warn("Reached MAX_STEPS={}, stop. trace={}", MAX_STEPS, context.getTrace());
            }
            return Mono.empty();
        }

        Agent next = agents.stream()
                .filter(a -> a.supports(context))
                .findFirst()
                .orElse(null);

        if (next == null) {
            log.debug("No agent supports current context at step {}. Stop.", step);
            return Mono.empty();
        }

        log.debug("Step {} -> {}", step, next.name());
        context.trace("step-" + step, next.name());
        return next.act(context)
                .then(Mono.defer(() -> driveLoop(context, step + 1)));
    }
}
