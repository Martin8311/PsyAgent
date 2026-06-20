package com.mindbridge.risk;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * 高风险预警记录（落库台账，供管理员后台查看）。
 */
@Entity
@Table(name = "risk_alert")
public class RiskAlert {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 触发预警的用户（学生用户名）。 */
    @Column(nullable = false, length = 64)
    private String userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private RiskLevel level;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private EmotionLabel emotion;

    /** 触发预警的原话。 */
    @Column(length = 1000)
    private String userMessage;

    /** 研判理由。 */
    @Column(length = 500)
    private String reason;

    @Column(nullable = false)
    private Instant createdAt = Instant.now();

    protected RiskAlert() {
    }

    public RiskAlert(String userId, RiskLevel level, EmotionLabel emotion, String userMessage, String reason) {
        this.userId = userId;
        this.level = level;
        this.emotion = emotion;
        this.userMessage = userMessage;
        this.reason = reason;
        this.createdAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public String getUserId() {
        return userId;
    }

    public RiskLevel getLevel() {
        return level;
    }

    public EmotionLabel getEmotion() {
        return emotion;
    }

    public String getUserMessage() {
        return userMessage;
    }

    public String getReason() {
        return reason;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
