package com.mindbridge.agent;

import com.mindbridge.ai.ChatMessage;
import com.mindbridge.risk.RiskAssessment;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 多 Agent 协作的共享上下文。
 *
 * <p>所有 Agent 读写同一个 Context：上游写入状态，下游据此决策。
 * 通过一组"阶段标志"驱动有限状态路由（见各 Agent 的 supports 实现）。
 */
public class AgentContext {

    // ===== 输入 =====
    private final String userId;
    private final String userInput;

    // ===== 记忆 / 历史 =====
    private final List<ChatMessage> history = new ArrayList<>();
    private boolean memoryLoaded = false;

    // ===== 意图路由 =====
    private Intent intent;

    // ===== 知识检索(RAG, Phase 4) =====
    private boolean knowledgeRetrieved = false;
    private final List<String> knowledgeSnippets = new ArrayList<>();

    // ===== 风险评估(Phase 5) =====
    private RiskAssessment risk;

    // ===== 最终回复流 =====
    private Flux<String> responseStream;

    /** 调试用：记录执行轨迹。 */
    private final Map<String, Object> trace = new LinkedHashMap<>();

    public AgentContext(String userId, String userInput) {
        this.userId = userId;
        this.userInput = userInput;
    }

    public String getUserId() {
        return userId;
    }

    public String getUserInput() {
        return userInput;
    }

    public List<ChatMessage> getHistory() {
        return history;
    }

    public boolean isMemoryLoaded() {
        return memoryLoaded;
    }

    public void setMemoryLoaded(boolean memoryLoaded) {
        this.memoryLoaded = memoryLoaded;
    }

    public Intent getIntent() {
        return intent;
    }

    public void setIntent(Intent intent) {
        this.intent = intent;
    }

    public boolean isKnowledgeRetrieved() {
        return knowledgeRetrieved;
    }

    public void setKnowledgeRetrieved(boolean knowledgeRetrieved) {
        this.knowledgeRetrieved = knowledgeRetrieved;
    }

    public List<String> getKnowledgeSnippets() {
        return knowledgeSnippets;
    }

    public RiskAssessment getRisk() {
        return risk;
    }

    public void setRisk(RiskAssessment risk) {
        this.risk = risk;
    }

    public Flux<String> getResponseStream() {
        return responseStream;
    }

    public void setResponseStream(Flux<String> responseStream) {
        this.responseStream = responseStream;
    }

    public boolean hasResponse() {
        return responseStream != null;
    }

    public Map<String, Object> getTrace() {
        return trace;
    }

    public void trace(String key, Object value) {
        this.trace.put(key, value);
    }
}
