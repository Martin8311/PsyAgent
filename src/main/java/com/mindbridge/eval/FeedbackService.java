package com.mindbridge.eval;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.Duration;
import java.util.List;

/**
 * 在线 RAG 反馈服务。回答时把检索到的文档 ID 暂存 Redis(短 TTL)，
 * 学生提交反馈时取出关联，落库 {@link RagFeedback}。
 */
@Service
public class FeedbackService {

    private static final Logger log = LoggerFactory.getLogger(FeedbackService.class);
    private static final String DOCS_KEY = "rag:docs:";
    private static final Duration DOCS_TTL = Duration.ofHours(2);
    private static final int ANSWER_MAX = 2000;

    private final ReactiveStringRedisTemplate redis;
    private final RagFeedbackRepository repo;
    private final ObjectMapper objectMapper;

    public FeedbackService(ReactiveStringRedisTemplate redis, RagFeedbackRepository repo, ObjectMapper objectMapper) {
        this.redis = redis;
        this.repo = repo;
        this.objectMapper = objectMapper;
    }

    /** 回答完成后记住本次检索命中的文档(供随后的反馈关联)。 */
    public Mono<Void> rememberRetrievedDocs(Long sessionId, List<Long> docIds) {
        if (sessionId == null || docIds == null || docIds.isEmpty()) {
            return Mono.empty();
        }
        return redis.opsForValue().set(DOCS_KEY + sessionId, toJson(docIds), DOCS_TTL)
                .then()
                .onErrorResume(e -> {
                    log.warn("暂存检索文档失败: {}", e.getMessage());
                    return Mono.empty();
                });
    }

    /** 提交反馈：从 Redis 取最近检索文档并落库。 */
    public Mono<RagFeedback> saveFeedback(Long sessionId, String userId, String query,
                                          String answer, String rating, String comment) {
        Mono<String> docsMono = (sessionId == null)
                ? Mono.just("[]")
                : redis.opsForValue().get(DOCS_KEY + sessionId).defaultIfEmpty("[]").onErrorReturn("[]");
        return docsMono.flatMap(docsJson -> Mono.fromCallable(() ->
                repo.save(new RagFeedback(sessionId, userId, query, truncate(answer),
                        docsJson, normalizeRating(rating), comment)))
                .subscribeOn(Schedulers.boundedElastic()));
    }

    public Mono<List<RagFeedback>> list() {
        return Mono.fromCallable(repo::findTop200ByOrderByCreatedAtDesc)
                .subscribeOn(Schedulers.boundedElastic());
    }

    public Mono<long[]> stats() {
        // 返回 [up, down, total]
        return Mono.fromCallable(() -> new long[]{
                repo.countByRating("UP"), repo.countByRating("DOWN"), repo.count()
        }).subscribeOn(Schedulers.boundedElastic());
    }

    private static String normalizeRating(String r) {
        return "UP".equalsIgnoreCase(r) ? "UP" : "DOWN";
    }

    private static String truncate(String s) {
        if (s == null) {
            return "";
        }
        return s.length() > ANSWER_MAX ? s.substring(0, ANSWER_MAX) : s;
    }

    private String toJson(List<Long> ids) {
        try {
            return objectMapper.writeValueAsString(ids);
        } catch (Exception e) {
            return "[]";
        }
    }
}
