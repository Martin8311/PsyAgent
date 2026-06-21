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
 * 咨询 Agent：CONSULT/RISK 分支的终结节点。
 * 结合知识库片段与风险评估，给出更专业、更稳妥的心理咨询式流式回复。
 */
@Component
public class CounselorAgent implements Agent {

    private static final String SYSTEM_PROMPT = """
            你是 MindBridge 校园心理咨询助手"小晕"，具备心理咨询基本素养。
            请遵循：先共情接纳情绪，再温和澄清，给出可操作的小建议，避免评判与说教。
            若识别到自伤/自杀等高风险信号，务必认真对待，温柔而坚定地引导其联系
            学校心理老师、家人或拨打心理援助热线，并表达你会一直陪着他。
            """;

    private final AiClient aiClient;
    private final ContextBudgetService budgetService;

    public CounselorAgent(AiClient aiClient, ContextBudgetService budgetService) {
        this.aiClient = aiClient;
        this.budgetService = budgetService;
    }

    @Override
    public String name() {
        return "CounselorAgent";
    }

    @Override
    public int order() {
        return 60;
    }

    @Override
    public boolean supports(AgentContext context) {
        Intent intent = context.getIntent();
        return (intent == Intent.CONSULT || intent == Intent.RISK)
                && context.getRisk() != null
                && !context.hasResponse();
    }

    @Override
    public Mono<Void> act(AgentContext context) {
        return Mono.fromRunnable(() -> {
            // 固定前置(必保)：系统提示 + 风险研判 + 知识 + 长期记忆
            List<ChatMessage> head = new ArrayList<>();
            head.add(ChatMessage.system(SYSTEM_PROMPT));

            // 注入风险研判，让回复更有针对性（尤其高风险时务必稳妥引导）
            var risk = context.getRisk();
            if (risk != null) {
                String hint;
                if (risk.isHigh()) {
                    hint = "【风险研判】等级=HIGH，情绪=" + risk.emotion()
                            + "。这是高风险信号，请高度重视：先稳稳接住情绪，温柔而坚定地引导其立即联系"
                            + "学校心理老师/信任的家人，或拨打心理援助热线（如全国24小时热线 400-161-9995），"
                            + "并明确表达你会一直陪着他，绝不评判。";
                } else {
                    hint = "【风险研判】等级=" + risk.level() + "，情绪=" + risk.emotion()
                            + "。请基于此给予针对性的共情与可操作建议。";
                }
                head.add(ChatMessage.system(hint));
            }

            // 注入知识库片段（Phase 4 后非空）
            if (!context.getKnowledgeSnippets().isEmpty()) {
                String knowledge = String.join("\n---\n", context.getKnowledgeSnippets());
                head.add(ChatMessage.system("可参考的心理知识资料：\n" + knowledge));
            }

            // 注入长期记忆（跨会话用户画像），让回复延续过往的了解与关系
            if (!context.getLongMemory().isEmpty()) {
                String mem = String.join("\n", context.getLongMemory());
                head.add(ChatMessage.system("关于这位同学，你在过往交流中已了解(仅供参考，"
                        + "请自然地体现关心与连续性，不要生硬复述这些条目)：\n" + mem));
            }

            // 上下文护栏：在预算内保留尽量多的近期历史，超出从最旧裁剪
            ContextBudgetService.Fit fit = budgetService.fit(head, context.getHistory(),
                    ChatMessage.user(context.getUserInput()));
            context.setResponseStream(aiClient.streamChat(fit.messages(),
                    LlmCallMeta.of(LlmCallMeta.Purpose.CHAT, context.getUserId(), context.getSessionId())));
            context.trace("counselor", "responded est=" + fit.estTokens() + "/" + fit.budget()
                    + " droppedHist=" + fit.droppedHistory());
        });
    }
}
