package com.mindbridge.memory;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mindbridge.ai.AiClient;
import com.mindbridge.ai.ChatMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 用 LLM 从一轮对话中抽取「关于用户的事实」，写入长期记忆。
 *
 * <p>运行在 RabbitListener 消费者线程(阻塞)，因此直接 {@code block()} 调 LLM。
 * 抽取强调「只取明确表达、不推测」以降低幻觉——错误记忆比没记忆更糟。
 */
@Service
public class MemoryExtractionService {

    private static final Logger log = LoggerFactory.getLogger(MemoryExtractionService.class);

    /** 抽取事实的默认置信度(本地小模型，保守取中等值)。 */
    private static final double DEFAULT_CONFIDENCE = 0.7;
    private static final int MAX_FACT_LEN = 200;
    private static final Set<String> ALLOWED_KEYS = Set.of(
            "family", "study", "sleep", "relationship", "health", "interest", "risk", "other");

    private final AiClient aiClient;
    private final LongTermMemoryService memoryService;
    private final ObjectMapper objectMapper;

    public MemoryExtractionService(AiClient aiClient, LongTermMemoryService memoryService,
                                   ObjectMapper objectMapper) {
        this.aiClient = aiClient;
        this.memoryService = memoryService;
        this.objectMapper = objectMapper;
    }

    /** 从一轮对话抽取并写入用户事实(同步)。 */
    public void extract(String userId, Long sessionId, String question, String answer) {
        if (userId == null || userId.isBlank() || question == null || question.isBlank()) {
            return;
        }
        String prompt = "下面是学生(user)与心理 AI(assistant)的一轮对话。请只提取【关于学生本人】的、"
                + "值得长期记住的客观事实(如家庭、学业、睡眠、人际、健康、重要经历)。"
                + "严格要求：①只提取学生明确表达的，不要推测或脑补；②每条简短一句，用第三人称陈述；"
                + "③输出 JSON 数组，每项 {\"key\":分类, \"fact\":事实}，key 从 "
                + "[family,study,sleep,relationship,health,interest,risk,other] 选；④没有可提取的就输出 []。\n\n"
                + "user: " + question + "\nassistant: " + answer;
        String raw;
        try {
            raw = aiClient.chat(List.of(
                    ChatMessage.system("你是信息抽取器，只输出 JSON 数组，不要任何解释。"),
                    ChatMessage.user(prompt))).block();
        } catch (Exception e) {
            log.warn("记忆抽取 LLM 调用失败 user={}: {}", userId, e.toString());
            return;
        }
        int saved = 0;
        for (Map<String, String> f : parseFacts(raw)) {
            String fact = f.get("fact");
            if (fact == null || fact.isBlank() || fact.length() > MAX_FACT_LEN) {
                continue;
            }
            String key = f.getOrDefault("key", "other");
            if (key == null || !ALLOWED_KEYS.contains(key)) {
                key = "other";
            }
            memoryService.upsertFact(userId, key, fact, DEFAULT_CONFIDENCE, sessionId);
            saved++;
        }
        if (saved > 0) {
            log.info("记忆抽取 user={} 写入/更新 {} 条事实", userId, saved);
        }
    }

    /** 从 LLM 输出里截取 JSON 数组并解析，失败返回空。 */
    private List<Map<String, String>> parseFacts(String raw) {
        if (raw == null) {
            return List.of();
        }
        int s = raw.indexOf('[');
        int e = raw.lastIndexOf(']');
        if (s < 0 || e <= s) {
            return List.of();
        }
        try {
            return objectMapper.readValue(raw.substring(s, e + 1),
                    new TypeReference<List<Map<String, String>>>() {
                    });
        } catch (Exception ex) {
            log.warn("记忆抽取 JSON 解析失败: {}", ex.toString());
            return List.of();
        }
    }
}
