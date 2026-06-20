package com.mindbridge.session;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class SessionService {

    private static final Logger log = LoggerFactory.getLogger(SessionService.class);

    private final ChatSessionRepository sessionRepo;
    private final ChatRecordRepository recordRepo;

    public SessionService(ChatSessionRepository sessionRepo, ChatRecordRepository recordRepo) {
        this.sessionRepo = sessionRepo;
        this.recordRepo = recordRepo;
    }

    /** 创建新会话，标题默认"新对话"，后续由第一条用户消息自动更新。 */
    public Mono<ChatSession> createSession(String userId) {
        return Mono.fromCallable(() -> sessionRepo.save(new ChatSession(userId, "新对话")))
                .subscribeOn(Schedulers.boundedElastic());
    }

    /** 当前用户所有会话，按最近活跃倒序。 */
    public Mono<List<ChatSession>> listSessions(String userId) {
        return Mono.fromCallable(() -> sessionRepo.findByUserIdOrderByUpdatedAtDesc(userId))
                .subscribeOn(Schedulers.boundedElastic());
    }

    /** 某会话的全部消息（鉴权：必须属于该 userId）。 */
    public Mono<List<ChatRecord>> getMessages(Long sessionId, String userId) {
        return Mono.fromCallable(() -> {
            assertOwner(sessionId, userId);
            return recordRepo.findBySessionIdOrderByCreatedAtAsc(sessionId);
        }).subscribeOn(Schedulers.boundedElastic());
    }

    /** 删除会话及所有消息（鉴权）。返回被删除的 sessionId。 */
    public Mono<Long> deleteSession(Long sessionId, String userId) {
        return Mono.fromCallable(() -> {
            assertOwner(sessionId, userId);
            recordRepo.deleteBySessionId(sessionId);
            sessionRepo.deleteById(sessionId);
            log.info("Session deleted: sessionId={}, userId={}", sessionId, userId);
            return sessionId;
        }).subscribeOn(Schedulers.boundedElastic());
    }

    /** 全文搜索当前用户的消息记录，最多返回 30 条。 */
    public Mono<List<Map<String, Object>>> searchMessages(String userId, String query) {
        if (query == null || query.isBlank()) return Mono.just(List.of());
        return Mono.fromCallable(() -> {
            List<ChatRecord> hits = recordRepo
                    .findTop30ByUserIdAndContentContainingOrderByCreatedAtDesc(userId, query);
            return hits.stream().map(r -> {
                Map<String, Object> m = new HashMap<>();
                m.put("sessionId", r.getSessionId());
                m.put("recordId", r.getId());
                m.put("role", r.getRole());
                m.put("content", r.getContent());
                m.put("createdAt", r.getCreatedAt().toString());
                return (Map<String, Object>) m;
            }).toList();
        }).subscribeOn(Schedulers.boundedElastic());
    }

    /**
     * 持久化一条消息到 MySQL，同步更新会话的 messageCount / updatedAt。
     * 首条用户消息自动截取前 20 字作为会话标题。
     */
    public Mono<Void> appendRecord(Long sessionId, String userId, String role, String content) {
        return Mono.fromRunnable(() -> {
            recordRepo.save(new ChatRecord(sessionId, userId, role, content));
            sessionRepo.findById(sessionId).ifPresent(session -> {
                int newCount = session.getMessageCount() + 1;
                session.setMessageCount(newCount);
                session.setUpdatedAt(Instant.now());
                if (newCount == 1 && "user".equals(role)) {
                    String title = content.length() > 20 ? content.substring(0, 20) + "…" : content;
                    session.setTitle(title);
                }
                sessionRepo.save(session);
            });
        }).subscribeOn(Schedulers.boundedElastic()).then();
    }

    private void assertOwner(Long sessionId, String userId) {
        ChatSession session = sessionRepo.findById(sessionId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "会话不存在"));
        if (!session.getUserId().equals(userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "无权操作");
        }
    }
}
