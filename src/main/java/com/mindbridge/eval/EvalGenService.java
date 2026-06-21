package com.mindbridge.eval;

import com.mindbridge.ai.AiClient;
import com.mindbridge.ai.ChatMessage;
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

    /** 为每篇文档生成 perDoc 个问题，返回新增样例总数。 */
    public Mono<Integer> generate(int perDoc) {
        return Mono.fromCallable(docRepo::findAll)
                .subscribeOn(Schedulers.boundedElastic())
                .flatMapMany(Flux::fromIterable)
                .concatMap(doc -> genForDoc(doc, perDoc))
                .reduce(0, Integer::sum);
    }

    private Mono<Integer> genForDoc(KbDocument doc, int perDoc) {
        String content = doc.getContent();
        if (content == null || content.isBlank()) {
            return Mono.just(0);
        }
        String snippet = content.length() > MAX_CONTENT ? content.substring(0, MAX_CONTENT) : content;
        List<ChatMessage> messages = List.of(
                ChatMessage.system("你是一名学生。根据给定的心理知识内容，提出 " + perDoc
                        + " 个你可能会向心理 AI 询问、且能用这段内容回答的问题。"
                        + "每行输出一个问题，不要编号、不要任何多余文字。"),
                ChatMessage.user(snippet));
        return aiClient.chat(messages)
                .publishOn(Schedulers.boundedElastic())
                .map(raw -> parseAndSave(raw, doc, perDoc))
                .onErrorResume(e -> {
                    log.warn("为文档 {} 生成评测问题失败: {}", doc.getId(), e.toString());
                    return Mono.just(0);
                });
    }

    private int parseAndSave(String raw, KbDocument doc, int perDoc) {
        int saved = 0;
        for (String line : raw.split("\\r?\\n")) {
            String q = line.replaceAll("^[0-9.、)\\-\\s]+", "").trim();
            if (q.length() < 5) {
                continue;
            }
            queryRepo.save(new EvalQuery(q, doc.getId(), doc.getTitle(), "GENERATED", false));
            saved++;
            if (saved >= perDoc) {
                break;
            }
        }
        log.info("文档 {} 生成评测问题 {} 条", doc.getId(), saved);
        return saved;
    }
}
