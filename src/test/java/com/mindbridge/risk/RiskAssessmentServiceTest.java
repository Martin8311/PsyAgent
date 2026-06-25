package com.mindbridge.risk;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mindbridge.ai.AiClient;
import com.mindbridge.ai.AiProperties;
import com.mindbridge.ai.ChatMessage;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * RiskAssessmentService 三层策略单元测试：硬规则 / LLM 解析 / 关键词兜底。
 * LLM 用 Mockito mock，不依赖真实 Ollama。
 */
class RiskAssessmentServiceTest {

    private RiskAssessmentService service(AiClient client) {
        return new RiskAssessmentService(client, new ObjectMapper(), new AiProperties());
    }

    @Test
    void hardRuleHitsHighRiskWithoutCallingLlm() {
        AiClient client = mock(AiClient.class);
        RiskAssessment r = service(client).assess("我不想活了，想自杀").block();
        assertEquals(RiskLevel.HIGH, r.level());
        assertEquals(EmotionLabel.HIGH_RISK, r.emotion());
        assertTrue(r.byHardRule());
        verifyNoInteractions(client);   // 硬规则优先，零延迟，不调 LLM
    }

    @Test
    void normalTextParsedFromLlmJson() {
        AiClient client = mock(AiClient.class);
        when(client.chat(anyList(), any(), any())).thenReturn(Mono.just(
                "{\"riskLevel\":\"LOW\",\"emotion\":\"NORMAL\",\"reason\":\"日常闲聊\"}"));
        RiskAssessment r = service(client).assess("今天和朋友打球很开心").block();
        assertEquals(RiskLevel.LOW, r.level());
        assertEquals(EmotionLabel.NORMAL, r.emotion());
    }

    @Test
    void llmFailureFallsBackToKeywordRule() {
        AiClient client = mock(AiClient.class);
        when(client.chat(anyList(), any(), any())).thenReturn(Mono.error(new RuntimeException("ollama down")));
        RiskAssessment r = service(client).assess("我最近好焦虑，失眠睡不着").block();
        assertEquals(EmotionLabel.ANXIETY, r.emotion());
        assertEquals(RiskLevel.MEDIUM, r.level());
    }

    @Test
    void blankTextReturnsNormal() {
        AiClient client = mock(AiClient.class);
        RiskAssessment r = service(client).assess("").block();
        assertEquals(RiskLevel.LOW, r.level());
        assertEquals(EmotionLabel.NORMAL, r.emotion());
        verifyNoInteractions(client);
    }

    // 让 ChatMessage 被引用，确保依赖在编译期可见（mock 行为以 anyList 匹配）
    @SuppressWarnings("unused")
    private static final List<ChatMessage> SAMPLE = List.of(ChatMessage.user("x"));
}
