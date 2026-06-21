package com.mindbridge.agent.impl;

import com.mindbridge.agent.Agent;
import com.mindbridge.agent.AgentContext;
import com.mindbridge.agent.Intent;
import com.mindbridge.knowledge.KnowledgeBaseService;
import com.mindbridge.knowledge.RetrievedChunk;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * 知识 Agent：CONSULT/RISK 分支，从心理知识库做语义检索(RAG)，
 * 把 top-k 片段写入 Context，为 CounselorAgent 的专业回复提供依据。
 *
 * <p>检索委托 {@link KnowledgeBaseService}（Chroma 向量库 + bge-m3）。
 * 为保证健壮性：即便知识库为空或 Chroma 暂不可用，也只是不注入片段、
 * 不打断链路（下游风控/回复照常进行）。
 */
@Component
public class KnowledgeAgent implements Agent {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeAgent.class);

    private final KnowledgeBaseService knowledgeBaseService;

    public KnowledgeAgent(KnowledgeBaseService knowledgeBaseService) {
        this.knowledgeBaseService = knowledgeBaseService;
    }

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
        return knowledgeBaseService.searchWithMeta(context.getUserInput())
                .onErrorResume(e -> {
                    log.warn("知识检索失败，跳过 RAG（不影响后续回复）: {}", e.toString());
                    return Mono.just(List.<RetrievedChunk>of());
                })
                .doOnNext(chunks -> {
                    chunks.forEach(c -> {
                        context.getKnowledgeSnippets().add(c.text());
                        if (c.documentId() != null) {
                            context.getRetrievedDocIds().add(c.documentId());
                        }
                    });
                    context.setKnowledgeRetrieved(true);
                    context.trace("knowledge", "retrieved " + chunks.size() + " snippets");
                })
                .then();
    }
}
