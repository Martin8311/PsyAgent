package com.mindbridge.agent;

import reactor.core.publisher.Mono;

/**
 * Agent 统一接口（Supervisor 路由式有限状态多 Agent 协作的基本单元）。
 *
 * <p>每个 Agent 通过 {@link #supports(AgentContext)} 自判当前阶段能否处理，
 * 能处理就由 {@link AgentRuntimeService} 调用 {@link #act(AgentContext)}，
 * 处理结果写回共享 {@link AgentContext}。
 */
public interface Agent {

    /** Agent 名称，用于日志与执行轨迹。 */
    String name();

    /**
     * 在多个可用 Agent 中的优先级，数值越小越先被检查。
     * 用于保证记忆→路由→检索→风控→回复的推进顺序。
     */
    int order();

    /** 当前上下文是否应由本 Agent 处理。 */
    boolean supports(AgentContext context);

    /**
     * 执行本 Agent 的逻辑，可异步；通过修改 context 推进状态。
     * 终结型 Agent 会在 context 上设置 responseStream。
     */
    Mono<Void> act(AgentContext context);
}
