package com.mindbridge.memory;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * 长期记忆条目（跨会话，按用户聚合）。
 *
 * <p>区别于 {@code chat_record}(逐条对话归档，仅供回看/台账)，本表是会被
 * <b>召回并注入 prompt</b> 的真正「记忆」：让 AI 在新会话里依然记得这个人。
 *
 * <ul>
 *   <li>{@code FACT}    稳定的个人事实(家庭/学业/睡眠/人际…)，同 key 覆盖更新</li>
 *   <li>{@code SUMMARY} 一次会话的要点摘要(带来源会话)</li>
 * </ul>
 *
 * <p>隐私优先：软删除({@code status=DELETED})而非物理删除，学生可在「我的记忆」自助查看/删除。
 */
@Entity
@Table(name = "user_memory", indexes = {
        @Index(name = "idx_um_user_status", columnList = "userId,status")
})
public class UserMemory {

    public static final String TYPE_FACT = "FACT";
    public static final String TYPE_SUMMARY = "SUMMARY";
    public static final String STATUS_ACTIVE = "ACTIVE";
    public static final String STATUS_DELETED = "DELETED";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 64)
    private String userId;

    /** FACT / SUMMARY */
    @Column(nullable = false, length = 16)
    private String type;

    /** 事实分类(family/study/sleep/relationship/health/interest/risk/other)，用于去重覆盖。 */
    @Column(length = 32)
    private String memoryKey;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    private Long sourceSessionId;

    /** 置信度 0~1，抽取可能有幻觉，低置信不注入。 */
    private double confidence;

    @Column(nullable = false, length = 16)
    private String status;

    @Column(nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    protected UserMemory() {
    }

    public UserMemory(String userId, String type, String memoryKey, String content,
                      Long sourceSessionId, double confidence) {
        this.userId = userId;
        this.type = type;
        this.memoryKey = memoryKey;
        this.content = content;
        this.sourceSessionId = sourceSessionId;
        this.confidence = confidence;
        this.status = STATUS_ACTIVE;
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    public Long getId() { return id; }
    public String getUserId() { return userId; }
    public String getType() { return type; }
    public String getMemoryKey() { return memoryKey; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public Long getSourceSessionId() { return sourceSessionId; }
    public void setSourceSessionId(Long sourceSessionId) { this.sourceSessionId = sourceSessionId; }
    public double getConfidence() { return confidence; }
    public void setConfidence(double confidence) { this.confidence = confidence; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
