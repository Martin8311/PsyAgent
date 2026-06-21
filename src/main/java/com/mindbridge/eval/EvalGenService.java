package com.mindbridge.eval;

import com.mindbridge.ai.AiClient;
import com.mindbridge.ai.ChatMessage;
import com.mindbridge.ai.LlmCallMeta;
import com.mindbridge.knowledge.KbDocument;
import com.mindbridge.knowledge.KbDocumentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.List;

/**
 * 用 LLM 为知识库每篇文档自动生成典型用户提问，构建评测集(golden set)。
 * 生成的样例 source=GENERATED、reviewed=false，需管理员后台校正确认后参与评测。
 */
@Service
public class EvalGenService {

    private static final Logger log = LoggerFactory.getLogger(EvalGenService.class);
    private static final int MAX_CONTENT = 800;

    private final KbDocumentRepository docRepo;
    private final AiClient aiClient;
    private final EvalQueryRepository queryRepo;

    public EvalGenService(KbDocumentRepository docRepo, AiClient aiClient, EvalQueryRepository queryRepo) {
        this.docRepo = docRepo;
        this.aiClient = aiClient;
        this.queryRepo = queryRepo;
    }

    /**
     * 为每篇文档生成 perDoc 个问题，返回新增样例总数。
     *
     * @param style {@code factual} 事实型(照文档提问，措辞重合，偏简单)；
     *              {@code scenario} 场景型(扮演困扰学生，口语化、避开原文术语，更贴近真实)。
     */
    public Mono<Integer> generate(int perDoc, String style) {
        boolean scenario = "scenario".equalsIgnoreCase(style);
        return Mono.fromCallable(docRepo::findAll)
                .subscribeOn(Schedulers.boundedElastic())
                .flatMapMany(Flux::fromIterable)
                .concatMap(doc -> genForDoc(doc, perDoc, scenario))
                .reduce(0, Integer::sum);
    }

    private Mono<Integer> genForDoc(KbDocument doc, int perDoc, boolean scenario) {
        String content = doc.getContent();
        if (content == null || content.isBlank()) {
            return Mono.just(0);
        }
        String raw = content.length() > MAX_CONTENT ? content.substring(0, MAX_CONTENT) : content;
        // 先净化掉 Markdown 符号(表格 | / 引用 > / 标题 # / 加粗 *)，避免 3b 照抄原文片段
        String snippet = stripMarkdown(raw);
        String systemPrompt = scenario
                ? "你是一名正在经历心理困扰的学生。阅读给定的心理知识内容后，想象一个你真实会遇到的、"
                + "与之相关的困扰场景，用口语、第一人称向心理 AI 倾诉或提问，共 " + perDoc + " 条。要求："
                + "①像真人说话，可带情绪、口语；②用你自己的大白话，不要照抄原文句子，不要使用专业术语；"
                + "③每条是完整通顺的一句话(疑问或倾诉)，必须出现\"我\"或问号；"
                + "④禁止包含 | > * # 等任何符号和编号；每行一条，不要引号、不要多余文字。"
                : "你是一名学生。根据给定的心理知识内容，提出 " + perDoc
                + " 个你可能会向心理 AI 询问、且能用这段内容回答的问题。要求："
                + "每条是完整通顺的问句，禁止包含任何符号或编号，禁止照抄原文句子；"
                + "每行输出一个问题，不要多余文字。";
        List<ChatMessage> messages = List.of(
                ChatMessage.system(systemPrompt),
                ChatMessage.user(snippet));
        return aiClient.chat(messages, LlmCallMeta.of(LlmCallMeta.Purpose.EVAL_GEN))
                .publishOn(Schedulers.boundedElastic())
                .map(text -> parseAndSave(text, doc, perDoc, scenario))
                .onErrorResume(e -> {
                    log.warn("为文档 {} 生成评测问题失败: {}", doc.getId(), e.toString());
                    return Mono.just(0);
                });
    }

    private int parseAndSave(String text, KbDocument doc, int perDoc, boolean scenario) {
        String source = scenario ? "SCENARIO" : "GENERATED";
        int saved = 0;
        for (String line : text.split("\\r?\\n")) {
            String q = cleanQuestion(line);
            if (!isValidQuestion(q, scenario)) {
                continue;
            }
            queryRepo.save(new EvalQuery(q, doc.getId(), doc.getTitle(), source, false));
            saved++;
            if (saved >= perDoc) {
                break;
            }
        }
        log.info("文档 {} 生成评测问题 {} 条(style={})", doc.getId(), saved, source);
        return saved;
    }

    /** 清洗一行 LLM 输出：去行内加粗、开头编号、首尾符号(引号/>/|/#)。 */
    private static String cleanQuestion(String line) {
        String q = (line == null) ? "" : line.trim();
        q = q.replace("**", "").replace("*", "");
        q = q.replaceAll("^[0-9.、)\\-\\s>#|\"'“”‘’]+", "");
        q = q.replaceAll("[\\s|>#\"'“”‘’]+$", "");
        return q.trim();
    }

    /** 质量过滤：剔除原文残片/破碎句。场景型还要求像倾诉/提问(含"我"或问号)。 */
    private static boolean isValidQuestion(String q, boolean scenario) {
        if (q.length() < 8) {
            return false;                                 // 太短多为破碎片段
        }
        if (q.contains("|") || q.contains(">")) {
            return false;                                 // 表格/引用残留
        }
        if (!q.matches(".*[一-鿿].*")) {
            return false;                                 // 必须含中文
        }
        if (scenario) {
            return q.contains("？") || q.contains("?") || q.contains("我");
        }
        return true;
    }

    /** 喂给 LLM 前净化文本：去 Markdown 结构符号，减少模型照抄原文。 */
    private static String stripMarkdown(String s) {
        return s.replaceAll("(?m)^[#>*\\-\\s]+", "")     // 行首 标题/引用/列表
                .replace("|", " ")                         // 表格分隔
                .replace("**", "")
                .replace("*", "")
                .replace("`", "");
    }
}
