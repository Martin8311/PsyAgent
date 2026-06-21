package com.mindbridge.eval;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * 评测样例(golden set 一条)：一个问题 + 期望命中的文档(文档级判命中)。
 * 来源 GENERATED(LLM 生成) 或 MANUAL(人工)，经管理员校正后 reviewed=true。
 */
@Entity
@Table(name = "eval_query")
public class EvalQuery {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 500)
    private String query;

    /** 期望命中的知识文档 ID(文档级)。 */
    @Column(nullable = false)
    private Long expectedDocId;

    /** 冗余的期望文档标题，便于后台展示。 */
    @Column(length = 200)
    private String expectedTitle;

    /** GENERATED / MANUAL */
    @Column(nullable = false, length = 16)
    private String source;

    /** 是否经人工校正确认。 */
    @Column(nullable = false)
    private boolean reviewed;

    @Column(nullable = false)
    private Instant createdAt = Instant.now();

    protected EvalQuery() {
    }

    public EvalQuery(String query, Long expectedDocId, String expectedTitle, String source, boolean reviewed) {
        this.query = query;
        this.expectedDocId = expectedDocId;
        this.expectedTitle = expectedTitle;
        this.source = source;
        this.reviewed = reviewed;
        this.createdAt = Instant.now();
    }

    public Long getId() { return id; }
    public String getQuery() { return query; }
    public void setQuery(String query) { this.query = query; }
    public Long getExpectedDocId() { return expectedDocId; }
    public void setExpectedDocId(Long expectedDocId) { this.expectedDocId = expectedDocId; }
    public String getExpectedTitle() { return expectedTitle; }
    public void setExpectedTitle(String expectedTitle) { this.expectedTitle = expectedTitle; }
    public String getSource() { return source; }
    public boolean isReviewed() { return reviewed; }
    public void setReviewed(boolean reviewed) { this.reviewed = reviewed; }
    public Instant getCreatedAt() { return createdAt; }
}
