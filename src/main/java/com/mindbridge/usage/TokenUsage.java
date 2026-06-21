package com.mindbridge.usage;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * 一次 LLM 调用的 token 用量记录，按「用户 + 功能 + 会话」归因。
 * 数据来自 Ollama 响应的真实计数(prompt_eval_count / eval_count)，非估算。
 */
@Entity
@Table(name = "token_usage", indexes = {
        @Index(name = "idx_tu_purpose", columnList = "purpose"),
        @Index(name = "idx_tu_user", columnList = "userId"),
        @Index(name = "idx_tu_created", columnList = "createdAt")
})
public class TokenUsage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(length = 64)
    private String userId;

    private Long sessionId;

    @Column(nullable = false, length = 24)
    private String purpose;

    @Column(nullable = false, length = 64)
    private String model;

    @Column(nullable = false)
    private int promptTokens;

    @Column(nullable = false)
    private int completionTokens;

    @Column(nullable = false)
    private int totalTokens;

    @Column(nullable = false)
    private Instant createdAt;

    protected TokenUsage() {
    }

    public TokenUsage(String userId, Long sessionId, String purpose, String model,
                      int promptTokens, int completionTokens) {
        this.userId = userId;
        this.sessionId = sessionId;
        this.purpose = purpose;
        this.model = model;
        this.promptTokens = promptTokens;
        this.completionTokens = completionTokens;
        this.totalTokens = promptTokens + completionTokens;
        this.createdAt = Instant.now();
    }

    public Long getId() { return id; }
    public String getUserId() { return userId; }
    public Long getSessionId() { return sessionId; }
    public String getPurpose() { return purpose; }
    public String getModel() { return model; }
    public int getPromptTokens() { return promptTokens; }
    public int getCompletionTokens() { return completionTokens; }
    public int getTotalTokens() { return totalTokens; }
    public Instant getCreatedAt() { return createdAt; }
}
