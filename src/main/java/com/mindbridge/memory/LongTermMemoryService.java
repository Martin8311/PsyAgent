package com.mindbridge.memory;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 长期记忆的存储 / 召回 / 去重 / 隐私治理。
 *
 * <p>JPA 阻塞 API，调用方(消费者线程同步用，Agent/Controller 用
 * {@code Mono.fromCallable(...).subscribeOn(boundedElastic)} 包裹)。
 */
@Service
public class LongTermMemoryService {

    /** 召回注入 prompt 的事实条数上限(控 token)。 */
    private static final int RECALL_FACTS = 12;
    /** 低于此置信度的事实不注入(防幻觉污染回复)。 */
    private static final double MIN_CONFIDENCE = 0.4;

    private final UserMemoryRepository repo;

    public LongTermMemoryService(UserMemoryRepository repo) {
        this.repo = repo;
    }

    /** 保存/更新一条事实：同 userId+key 覆盖更新，无 key 按内容去重后新增。 */
    @Transactional
    public void upsertFact(String userId, String memoryKey, String content, double confidence, Long sessionId) {
        if (content == null || content.isBlank()) {
            return;
        }
        String fact = content.trim();
        String key = (memoryKey == null || memoryKey.isBlank()) ? null : memoryKey.trim();
        if (key != null) {
            Optional<UserMemory> existing = repo.findFirstByUserIdAndTypeAndMemoryKeyAndStatus(
                    userId, UserMemory.TYPE_FACT, key, UserMemory.STATUS_ACTIVE);
            if (existing.isPresent()) {
                UserMemory m = existing.get();
                m.setContent(fact);
                m.setConfidence(confidence);
                m.setSourceSessionId(sessionId);
                m.setUpdatedAt(Instant.now());
                repo.save(m);
                return;
            }
        } else {
            boolean dup = repo.findByUserIdAndTypeAndStatusOrderByUpdatedAtDesc(
                            userId, UserMemory.TYPE_FACT, UserMemory.STATUS_ACTIVE).stream()
                    .anyMatch(m -> m.getContent().equalsIgnoreCase(fact));
            if (dup) {
                return;
            }
        }
        repo.save(new UserMemory(userId, UserMemory.TYPE_FACT, key, fact, sessionId, confidence));
    }

    /** 保存/更新某会话的摘要：一个会话维护一条(按 sourceSessionId 覆盖)，随对话推进更新。 */
    @Transactional
    public void upsertSummary(String userId, Long sessionId, String content) {
        if (content == null || content.isBlank() || sessionId == null) {
            return;
        }
        String summary = content.trim();
        Optional<UserMemory> existing = repo.findFirstByUserIdAndTypeAndSourceSessionIdAndStatus(
                userId, UserMemory.TYPE_SUMMARY, sessionId, UserMemory.STATUS_ACTIVE);
        if (existing.isPresent()) {
            UserMemory m = existing.get();
            m.setContent(summary);
            m.setUpdatedAt(Instant.now());
            repo.save(m);
        } else {
            repo.save(new UserMemory(userId, UserMemory.TYPE_SUMMARY, null, summary, sessionId, 1.0));
        }
    }

    /** 召回用于注入 prompt 的记忆文本(置信度达标的事实 + 最近一条会话摘要)。 */
    @Transactional(readOnly = true)
    public List<String> recall(String userId) {
        List<String> out = new ArrayList<>();
        repo.findByUserIdAndTypeAndStatusOrderByUpdatedAtDesc(
                        userId, UserMemory.TYPE_FACT, UserMemory.STATUS_ACTIVE).stream()
                .filter(m -> m.getConfidence() >= MIN_CONFIDENCE)
                .limit(RECALL_FACTS)
                .forEach(m -> out.add("· " + m.getContent()));
        repo.findByUserIdAndTypeAndStatusOrderByUpdatedAtDesc(
                        userId, UserMemory.TYPE_SUMMARY, UserMemory.STATUS_ACTIVE).stream()
                .limit(2)
                .forEach(m -> out.add("最近交流回顾：" + m.getContent()));
        return out;
    }

    /** 隐私页：本人全部有效记忆。 */
    @Transactional(readOnly = true)
    public List<UserMemory> listForUser(String userId) {
        return repo.findByUserIdAndStatusOrderByUpdatedAtDesc(userId, UserMemory.STATUS_ACTIVE);
    }

    /** 软删除一条(校验归属，仅能删自己的)。 */
    @Transactional
    public boolean softDelete(String userId, Long id) {
        return repo.findById(id)
                .filter(m -> m.getUserId().equals(userId) && UserMemory.STATUS_ACTIVE.equals(m.getStatus()))
                .map(m -> {
                    m.setStatus(UserMemory.STATUS_DELETED);
                    m.setUpdatedAt(Instant.now());
                    repo.save(m);
                    return true;
                })
                .orElse(false);
    }
}
