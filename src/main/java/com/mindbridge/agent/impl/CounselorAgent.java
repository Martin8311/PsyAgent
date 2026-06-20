package com.mindbridge.agent.impl;

import com.mindbridge.agent.Agent;
import com.mindbridge.agent.AgentContext;
import com.mindbridge.agent.Intent;
import com.mindbridge.ai.AiClient;
import com.mindbridge.ai.ChatMessage;
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
            你是 MindBridge 校园心理咨询助手"小桥"，具备心理咨询基本素养。
            请遵循：先共情接纳情绪，再温和澄清，给出可操作的小建议，避免评判与说教。
            若识别到自伤/自杀等高风险信号，务必认真对待，温柔而坚定地引导其联系
            学校心理老师、家人或拨打心理援助热线，并表达你会一直陪着他。
            """;

    private final AiClient aiClient;

    public CounselorAgent(AiClient aiClient) {
        this.aiClient = aiClient;
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
                && context.getRiskResult() != null
                && !context.hasResponse();
    }

    @Override
    public Mono<Void> act(AgentContext context) {
        return Mono.fromRunnable(() -> {
            List<ChatMessage> messages = new ArrayList<>();
            messages.add(ChatMessage.system(SYSTEM_PROMPT));

            // 注入知识库片段（Phase 4 后非空）
            if (!context.getKnowledgeSnippets().isEmpty()) {
                String knowledge = String.join("\n---\n", context.getKnowledgeSnippets());
                messages.add(ChatMessage.system("可参考的心理知识资料：\n" + knowledge));
            }

            messages.addAll(context.getHistory());
            messages.add(ChatMessage.user(context.getUserInput()));
            context.setResponseStream(aiClient.streamChat(messages));
            context.trace("counselor", "responded");
        });
    }
}
