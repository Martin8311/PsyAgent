package com.mindbridge.agent.impl;

import com.mindbridge.agent.Agent;
import com.mindbridge.agent.AgentContext;
import com.mindbridge.agent.Intent;
import com.mindbridge.ai.AiClient;
import com.mindbridge.ai.ChatMessage;
import com.mindbridge.ai.ContextBudgetService;
import com.mindbridge.ai.LlmCallMeta;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.List;

/**
 * 陪伴 Agent：CHAT 分支的终结节点，提供温暖的日常陪伴式流式回复。
 */
@Component
public class CompanionAgent implements Agent {

    private static final String SYSTEM_PROMPT = """
            你是 MindBridge 校园心理助手，名字叫"小晕"。
            请用温暖、共情、口语化的中文陪伴学生聊天，像一个耐心的朋友。
            回应简洁自然，不评判、不说教，可以适当反问以延续对话。
            """;

    private final AiClient aiClient;
    private final ContextBudgetService budgetService;

    public CompanionAgent(AiClient aiClient, ContextBudgetService budgetService) {
        this.aiClient = aiClient;
        this.budgetService = budgetService;
    }

    @Override
    public String name() {
        return "CompanionAgent";
    }

    @Override
    public int order() {
        return 30;
    }

    @Override
    public boolean supports(AgentContext context) {
        return context.getIntent() == Intent.CHAT && !context.hasResponse();
    }

    @Override
    public Mono<Void> act(AgentContext context) {
        return Mono.fromRunnable(() -> {
            // 固定前置(必保)：系统提示 + 长期记忆
            List<ChatMessage> head = new ArrayList<>();
            head.add(ChatMessage.system(SYSTEM_PROMPT));
            // 注入长期记忆（跨会话用户画像），让陪伴更连续、更懂这个人
            if (!context.getLongMemory().isEmpty()) {
                String mem = String.join("\n", context.getLongMemory());
                head.add(ChatMessage.system("关于这位同学，你在过往交流中已了解(仅供参考，"
                        + "请自然地体现关心，不要生硬复述这些条目)：\n" + mem));
            }
            // 上下文护栏：在预算内保留尽量多的近期历史，超出从最旧裁剪
            ContextBudgetService.Fit fit = budgetService.fit(head, context.getHistory(),
                    ChatMessage.user(context.getUserInput()));
            context.setResponseStream(aiClient.streamChat(fit.messages(),
                    LlmCallMeta.of(LlmCallMeta.Purpose.CHAT, context.getUserId(), context.getSessionId())));
            context.trace("companion", "responded est=" + fit.estTokens() + "/" + fit.budget()
                    + " droppedHist=" + fit.droppedHistory());
        });
    }
}
