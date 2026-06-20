package com.mindbridge.agent.impl;

import com.mindbridge.agent.Agent;
import com.mindbridge.agent.AgentContext;
import com.mindbridge.agent.Intent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * 调度 Agent：识别用户意图(CHAT/CONSULT/RISK)，决定后续分支路由。
 *
 * <p>Phase 2 采用「高风险词硬规则优先 + 关键词匹配」的快速判定，
 * 符合简历「高风险词硬规则优先」的设计；后续可叠加 LLM 结构化判定。
 */
@Component
public class SupervisorAgent implements Agent {

    private static final Logger log = LoggerFactory.getLogger(SupervisorAgent.class);

    /** 高风险关键词：命中即判为 RISK，硬规则优先。 */
    private static final List<String> RISK_WORDS = List.of(
            "自杀", "想死", "不想活", "活不下去", "结束生命", "自残", "自伤",
            "割腕", "跳楼", "轻生", "没有意义", "解脱"
    );

    /** 心理咨询类关键词：命中判为 CONSULT。 */
    private static final List<String> CONSULT_WORDS = List.of(
            "焦虑", "抑郁", "压力", "失眠", "情绪", "难过", "痛苦", "孤独",
            "崩溃", "心理", "咨询", "烦", "emo", "考试", "人际", "恋爱", "家庭"
    );

    @Override
    public String name() {
        return "SupervisorAgent";
    }

    @Override
    public int order() {
        return 20;
    }

    @Override
    public boolean supports(AgentContext context) {
        return context.isMemoryLoaded() && context.getIntent() == null;
    }

    @Override
    public Mono<Void> act(AgentContext context) {
        return Mono.fromRunnable(() -> {
            Intent intent = classify(context.getUserInput());
            context.setIntent(intent);
            context.trace("intent", intent);
            log.debug("Supervisor classified intent={} for input='{}'", intent, context.getUserInput());
        });
    }

    private Intent classify(String input) {
        if (input == null || input.isBlank()) {
            return Intent.CHAT;
        }
        String text = input.toLowerCase();
        if (RISK_WORDS.stream().anyMatch(text::contains)) {
            return Intent.RISK;
        }
        if (CONSULT_WORDS.stream().anyMatch(text::contains)) {
            return Intent.CONSULT;
        }
        return Intent.CHAT;
    }
}
