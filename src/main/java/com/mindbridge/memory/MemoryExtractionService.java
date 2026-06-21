package com.mindbridge.memory;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mindbridge.ai.AiClient;
import com.mindbridge.ai.ChatMessage;
import com.mindbridge.ai.LlmCallMeta;
import com.mindbridge.session.ChatRecord;
import com.mindbridge.session.ChatRecordRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

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

    /** 会话摘要：至少这么多条消息才值得摘要(太短没内容)。 */
    private static final int SUMMARY_MIN_MESSAGES = 4;
    /** 降频：每新增这么多条消息(约 2 轮)才重新摘要一次，避免每轮都跑 LLM。 */
    private static final int SUMMARY_EVERY = 4;
    /** 送进摘要 LLM 的对话文本上限，超长取末尾(关注近期)。 */
    private static final int MAX_CONVO_LEN = 1500;

    private final AiClient aiClient;
    private final LongTermMemoryService memoryService;
    private final ObjectMapper objectMapper;
    private final ChatRecordRepository chatRecordRepo;

    public MemoryExtractionService(AiClient aiClient, LongTermMemoryService memoryService,
                                   ObjectMapper objectMapper, ChatRecordRepository chatRecordRepo) {
        this.aiClient = aiClient;
        this.memoryService = memoryService;
        this.objectMapper = objectMapper;
        this.chatRecordRepo = chatRecordRepo;
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
                    ChatMessage.user(prompt)),
                    LlmCallMeta.of(LlmCallMeta.Purpose.MEMORY_EXTRACT, userId, sessionId)).block();
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

    /**
     * 按需更新会话摘要：消息数达阈值、且每隔若干轮才生成一次(降频)，
     * 一个会话维护一条摘要(覆盖更新)。运行在消费者线程(同步)。
     */
    public void summarizeIfDue(String userId, Long sessionId) {
        if (userId == null || userId.isBlank() || sessionId == null) {
            return;
        }
        List<ChatRecord> records = chatRecordRepo.findBySessionIdOrderByCreatedAtAsc(sessionId);
        int n = records.size();
        if (n < SUMMARY_MIN_MESSAGES || n % SUMMARY_EVERY != 0) {
            return;   // 太短或未到更新节奏
        }
        String convo = records.stream()
                .map(r -> ("user".equals(r.getRole()) ? "学生" : "小桥") + "：" + r.getContent())
                .collect(Collectors.joining("\n"));
        if (convo.length() > MAX_CONVO_LEN) {
            convo = convo.substring(convo.length() - MAX_CONVO_LEN);
        }
        String raw;
        try {
            raw = aiClient.chat(List.of(
                    ChatMessage.system("你是心理咨询记录员。用 1~3 句话客观概括这次对话："
                            + "学生聊了什么困扰/话题、情绪状态、以及给到的建议或约定。"
                            + "第三人称陈述，简洁，不要寒暄、不要编号。"),
                    ChatMessage.user(convo)),
                    LlmCallMeta.of(LlmCallMeta.Purpose.MEMORY_SUMMARY, userId, sessionId)).block();
        } catch (Exception e) {
            log.warn("会话摘要 LLM 调用失败 session={}: {}", sessionId, e.toString());
            return;
        }
        if (raw != null && !raw.isBlank()) {
            memoryService.upsertSummary(userId, sessionId, raw.trim());
            log.info("会话摘要更新 user={} session={} (基于 {} 条消息)", userId, sessionId, n);
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
