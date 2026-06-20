package com.mindbridge.agent.impl;

import com.mindbridge.agent.Agent;
import com.mindbridge.agent.AgentContext;
import com.mindbridge.agent.Intent;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/**
 * 知识 Agent：CONSULT/RISK 分支，从心理知识库检索相关资料(RAG)，
 * 为 CounselorAgent 的专业回复提供依据。
 *
 * <p>Phase 2 占位（不检索，直接放行）；Phase 4 接入向量检索 + 上下文扩展。
 */
@Component
public class KnowledgeAgent implements Agent {

    @Override
    public String name() {
        return "KnowledgeAgent";
    }

    @Override
    public int order() {
        return 40;
    }

    @Override
    public boolean supports(AgentContext context) {
        Intent intent = context.getIntent();
        return (intent == Intent.CONSULT || intent == Intent.RISK)
                && !context.isKnowledgeRetrieved();
    }

    @Override
    public Mono<Void> act(AgentContext context) {
        return Mono.fromRunnable(() -> {
            // TODO Phase 4: 调用 RagService 检索 top-k 片段写入 context.knowledgeSnippets
            context.setKnowledgeRetrieved(true);
            context.trace("knowledge", "retrieved(empty in Phase 2)");
        });
    }
}
