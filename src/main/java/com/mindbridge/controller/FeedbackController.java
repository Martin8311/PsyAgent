package com.mindbridge.controller;

import com.mindbridge.eval.FeedbackService;
import com.mindbridge.eval.RagFeedback;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

/**
 * RAG 在线反馈接口。
 * <ul>
 *   <li>POST /api/feedback —— 学生提交赞/踩(需登录)</li>
 *   <li>GET  /api/admin/feedback —— 反馈列表(ADMIN)</li>
 *   <li>GET  /api/admin/feedback/stats —— 满意率统计(ADMIN)</li>
 * </ul>
 */
@RestController
public class FeedbackController {

    private static final String ANONYMOUS = "anonymous";

    private final FeedbackService feedbackService;

    public FeedbackController(FeedbackService feedbackService) {
        this.feedbackService = feedbackService;
    }

    @PostMapping("/api/feedback")
    public Mono<Map<String, Object>> submit(@RequestBody Map<String, Object> body) {
        Long sessionId = body.get("sessionId") != null ? ((Number) body.get("sessionId")).longValue() : null;
        String query = str(body.get("query"));
        String answer = str(body.get("answer"));
        String rating = str(body.get("rating"));
        String comment = str(body.get("comment"));
        return currentUserId().flatMap(uid ->
                        feedbackService.saveFeedback(sessionId, uid, query, answer, rating, comment))
                .map(fb -> Map.<String, Object>of("id", fb.getId(), "rating", fb.getRating()));
    }

    @GetMapping("/api/admin/feedback")
    public Mono<List<Map<String, Object>>> list() {
        return feedbackService.list().map(items -> items.stream().map(this::feedbackMap).toList());
    }

    @GetMapping("/api/admin/feedback/stats")
    public Mono<Map<String, Object>> stats() {
        return feedbackService.stats().map(s -> {
            long up = s[0], down = s[1], total = s[2];
            double satisfaction = (up + down) == 0 ? 0.0 : Math.round((double) up / (up + down) * 1000.0) / 1000.0;
            return Map.<String, Object>of("up", up, "down", down, "total", total, "satisfaction", satisfaction);
        });
    }

    private Map<String, Object> feedbackMap(RagFeedback f) {
        return Map.of(
                "id", f.getId(),
                "userId", f.getUserId(),
                "rating", f.getRating(),
                "query", f.getQuery() == null ? "" : f.getQuery(),
                "answer", f.getAnswer() == null ? "" : f.getAnswer(),
                "comment", f.getComment() == null ? "" : f.getComment(),
                "retrievedDocIds", f.getRetrievedDocIds() == null ? "[]" : f.getRetrievedDocIds(),
                "createdAt", f.getCreatedAt().toString());
    }

    private Mono<String> currentUserId() {
        return ReactiveSecurityContextHolder.getContext()
                .map(ctx -> ctx.getAuthentication().getName())
                .defaultIfEmpty(ANONYMOUS);
    }

    private static String str(Object o) {
        return o == null ? null : o.toString();
    }
}
