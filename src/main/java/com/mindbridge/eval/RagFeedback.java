package com.mindbridge.eval;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * 学生对一次 RAG 回答的反馈(赞/踩 + 备注)，并关联本次检索到的来源文档，
 * 用于统计满意率、定位知识盲区(踩的案例用了哪些知识却不给力)。
 */
@Entity
@Table(name = "rag_feedback")
public class RagFeedback {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long sessionId;

    @Column(nullable = false, length = 64)
    private String userId;

    @Column(length = 500)
    private String query;

    @Column(columnDefinition = "TEXT")
    private String answer;

    /** 本次检索命中的文档 ID，JSON 数组字符串。 */
    @Column(columnDefinition = "TEXT")
    private String retrievedDocIds;

    /** UP(赞) / DOWN(踩) */
    @Column(nullable = false, length = 16)
    private String rating;

    @Column(length = 500)
    private String comment;

    @Column(nullable = false)
    private Instant createdAt = Instant.now();

    protected RagFeedback() {
    }

    public RagFeedback(Long sessionId, String userId, String query, String answer,
                       String retrievedDocIds, String rating, String comment) {
        this.sessionId = sessionId;
        this.userId = userId;
        this.query = query;
        this.answer = answer;
        this.retrievedDocIds = retrievedDocIds;
        this.rating = rating;
        this.comment = comment;
        this.createdAt = Instant.now();
    }

    public Long getId() { return id; }
    public Long getSessionId() { return sessionId; }
    public String getUserId() { return userId; }
    public String getQuery() { return query; }
    public String getAnswer() { return answer; }
    public String getRetrievedDocIds() { return retrievedDocIds; }
    public String getRating() { return rating; }
    public String getComment() { return comment; }
    public Instant getCreatedAt() { return createdAt; }
}
