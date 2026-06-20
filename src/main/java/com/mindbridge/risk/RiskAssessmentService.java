package com.mindbridge.risk;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mindbridge.ai.AiClient;
import com.mindbridge.ai.ChatMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.List;

/**
 * 心理风险评估服务。
 *
 * <p>三层策略，体现「高风险词硬规则优先」：
 * <ol>
 *   <li>硬规则：命中高风险词 → 直接 HIGH，保证不漏报、零延迟；</li>
 *   <li>LLM 结构化：让模型输出 JSON 细化风险等级与情绪；</li>
 *   <li>关键词兜底：LLM 不可用/解析失败时按焦虑/抑郁词规则降级。</li>
 * </ol>
 */
@Service
public class RiskAssessmentService {

    private static final Logger log = LoggerFactory.getLogger(RiskAssessmentService.class);

    /** 高风险词：命中即判 HIGH（自伤/自杀倾向）。 */
    private static final List<String> HIGH_RISK_WORDS = List.of(
            "自杀", "想死", "不想活", "活不下去", "活着没意思", "结束生命", "了结自己",
            "自残", "自伤", "割腕", "跳楼", "轻生", "解脱", "不如死了", "消失算了"
    );

    /** 抑郁相关词。 */
    private static final List<String> DEPRESSED_WORDS = List.of(
            "抑郁", "绝望", "没意义", "空虚", "麻木", "崩溃", "撑不下去", "好累", "没希望", "孤独"
    );

    /** 焦虑相关词。 */
    private static final List<String> ANXIETY_WORDS = List.of(
            "焦虑", "紧张", "害怕", "压力", "失眠", "睡不着", "心慌", "担心", "考试", "烦躁"
    );

    private final AiClient aiClient;
    private final ObjectMapper objectMapper;

    public RiskAssessmentService(AiClient aiClient, ObjectMapper objectMapper) {
        this.aiClient = aiClient;
        this.objectMapper = objectMapper;
    }

    /**
     * 评估一段文本的心理风险。
     */
    public Mono<RiskAssessment> assess(String text) {
        if (text == null || text.isBlank()) {
            return Mono.just(RiskAssessment.normal());
        }
        // ① 硬规则优先
        List<String> hits = scan(text, HIGH_RISK_WORDS);
        if (!hits.isEmpty()) {
            log.debug("硬规则命中高风险词: {}", hits);
            return Mono.just(new RiskAssessment(
                    RiskLevel.HIGH, EmotionLabel.HIGH_RISK,
                    "命中高风险词：" + String.join("、", hits), hits, true));
        }
        // ② LLM 结构化评估，失败则 ③ 关键词兜底
        return llmAssess(text)
                .onErrorResume(e -> {
                    log.warn("LLM 风险评估失败，降级关键词规则: {}", e.getMessage());
                    return Mono.just(ruleBasedAssess(text));
                });
    }

    /** ② LLM 结构化 JSON 评估。 */
    private Mono<RiskAssessment> llmAssess(String text) {
        String sys = """
                你是校园心理风险评估器。只输出一个 JSON 对象，不要任何多余文字或解释。
                根据学生这句话，评估心理风险，字段：
                - riskLevel: LOW / MEDIUM / HIGH
                - emotion: NORMAL / ANXIETY / DEPRESSED / HIGH_RISK
                - reason: 不超过30字的中文理由
                输出示例：{"riskLevel":"LOW","emotion":"NORMAL","reason":"日常闲聊"}
                """;
        List<ChatMessage> messages = List.of(
                ChatMessage.system(sys),
                ChatMessage.user("学生的话：「" + text + "」")
        );
        return aiClient.chat(messages).map(this::parse);
    }

    /** 从模型输出里提取 JSON 并解析为 RiskAssessment。 */
    private RiskAssessment parse(String raw) {
        try {
            int s = raw.indexOf('{');
            int e = raw.lastIndexOf('}');
            if (s < 0 || e <= s) {
                throw new IllegalArgumentException("无 JSON: " + raw);
            }
            JsonNode node = objectMapper.readTree(raw.substring(s, e + 1));
            RiskLevel level = RiskLevel.valueOf(node.path("riskLevel").asText("LOW").trim().toUpperCase());
            EmotionLabel emotion = EmotionLabel.valueOf(node.path("emotion").asText("NORMAL").trim().toUpperCase());
            String reason = node.path("reason").asText("");
            return new RiskAssessment(level, emotion, reason, List.of(), false);
        } catch (Exception ex) {
            throw new IllegalStateException("解析风险 JSON 失败: " + ex.getMessage(), ex);
        }
    }

    /** ③ 关键词规则兜底。 */
    private RiskAssessment ruleBasedAssess(String text) {
        List<String> dep = scan(text, DEPRESSED_WORDS);
        if (!dep.isEmpty()) {
            return new RiskAssessment(RiskLevel.MEDIUM, EmotionLabel.DEPRESSED,
                    "关键词提示抑郁情绪：" + String.join("、", dep), dep, false);
        }
        List<String> anx = scan(text, ANXIETY_WORDS);
        if (!anx.isEmpty()) {
            return new RiskAssessment(RiskLevel.MEDIUM, EmotionLabel.ANXIETY,
                    "关键词提示焦虑情绪：" + String.join("、", anx), anx, false);
        }
        return RiskAssessment.normal();
    }

    private List<String> scan(String text, List<String> words) {
        String lower = text.toLowerCase();
        List<String> hit = new ArrayList<>();
        for (String w : words) {
            if (lower.contains(w)) {
                hit.add(w);
            }
        }
        return hit;
    }
}
