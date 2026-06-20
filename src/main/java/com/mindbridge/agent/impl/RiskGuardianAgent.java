package com.mindbridge.agent.impl;

import com.mindbridge.agent.Agent;
import com.mindbridge.agent.AgentContext;
import com.mindbridge.agent.Intent;
import com.mindbridge.risk.AlertService;
import com.mindbridge.risk.RiskAssessment;
import com.mindbridge.risk.RiskAssessmentService;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/**
 * 风控 Agent：对 CONSULT/RISK 场景进行心理风险评估
 * （风险等级 LOW/MEDIUM/HIGH + 情绪标签），HIGH 触发自动预警。
 *
 * <p>评估委托 {@link RiskAssessmentService}（硬规则优先 + LLM 结构化 + 关键词兜底）；
 * 高风险时由 {@link AlertService} 落库台账并告警。结果写回 Context，
 * 供 CounselorAgent 注入 prompt。
 */
@Component
public class RiskGuardianAgent implements Agent {

    private final RiskAssessmentService riskAssessmentService;
    private final AlertService alertService;

    public RiskGuardianAgent(RiskAssessmentService riskAssessmentService, AlertService alertService) {
        this.riskAssessmentService = riskAssessmentService;
        this.alertService = alertService;
    }

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
                && context.getRisk() == null;
    }

    @Override
    public Mono<Void> act(AgentContext context) {
        return riskAssessmentService.assess(context.getUserInput())
                .flatMap(risk -> {
                    context.setRisk(risk);
                    context.trace("risk", risk.level() + "/" + risk.emotion()
                            + (risk.byHardRule() ? "(硬规则)" : ""));
                    if (risk.isHigh()) {
                        return alertService.raise(context.getUserId(), risk, context.getUserInput())
                                .thenReturn(risk);
                    }
                    return Mono.just(risk);
                })
                .then();
    }
}
