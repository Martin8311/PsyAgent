package com.mindbridge.agent.impl;

import com.mindbridge.agent.Agent;
import com.mindbridge.agent.AgentContext;
import com.mindbridge.agent.Intent;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/**
 * 风控 Agent：对 CONSULT/RISK 场景进行心理风险评估
 * (风险等级 LOW/MEDIUM/HIGH + 情绪标签)，高风险触发预警。
 *
 * <p>Phase 2 占位（标记已评估）；Phase 5 接入「高风险词硬规则 + LLM 结构化
 * JSON 输出」，并联动 MCP 写 Excel 台账 / 发邮件预警。
 */
@Component
public class RiskGuardianAgent implements Agent {

    @Override
    public String name() {
        return "RiskGuardianAgent";
    }

    @Override
    public int order() {
        return 50;
    }

    @Override
    public boolean supports(AgentContext context) {
        Intent intent = context.getIntent();
        return (intent == Intent.CONSULT || intent == Intent.RISK)
                && context.isKnowledgeRetrieved()
                && context.getRiskResult() == null;
    }

    @Override
    public Mono<Void> act(AgentContext context) {
        return Mono.fromRunnable(() -> {
            // TODO Phase 5: 硬规则 + LLM 结构化风险评估，高风险触发 MCP 预警
            context.setRiskResult("UNASSESSED(Phase 2 placeholder)");
            context.trace("risk", "assessed(placeholder)");
        });
    }
}
