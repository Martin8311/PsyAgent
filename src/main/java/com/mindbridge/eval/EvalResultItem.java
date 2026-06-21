package com.mindbridge.eval;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * 单条评测样例在某次 run 下的结果明细，供下钻分析(哪些 query 没命中、排名多少)。
 */
@Entity
@Table(name = "eval_result")
public class EvalResultItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long runId;

    private Long queryId;

    @Column(length = 500)
    private String query;

    private Long expectedDocId;

    /** 检索回的文档 ID(去重保序)，JSON 数组字符串。 */
    @Column(columnDefinition = "TEXT")
    private String retrievedDocIds;

    private boolean hit;

    /** 期望文档在检索结果中的排名(1-based)，未命中为 0。rank 是 MySQL 保留字，改列名。 */
    @Column(name = "hit_rank")
    private int rank;

    /** precision 是 MySQL 保留字，改列名。 */
    @Column(name = "precision_score")
    private double precision;

    @Column(name = "recall_score")
    private double recall;

    private double reciprocalRank;

    protected EvalResultItem() {
    }

    public EvalResultItem(Long runId, Long queryId, String query, Long expectedDocId,
                          String retrievedDocIds, boolean hit, int rank,
                          double precision, double recall, double reciprocalRank) {
        this.runId = runId;
        this.queryId = queryId;
        this.query = query;
        this.expectedDocId = expectedDocId;
        this.retrievedDocIds = retrievedDocIds;
        this.hit = hit;
        this.rank = rank;
        this.precision = precision;
        this.recall = recall;
        this.reciprocalRank = reciprocalRank;
    }

    public Long getId() { return id; }
    public Long getRunId() { return runId; }
    public Long getQueryId() { return queryId; }
    public String getQuery() { return query; }
    public Long getExpectedDocId() { return expectedDocId; }
    public String getRetrievedDocIds() { return retrievedDocIds; }
    public boolean isHit() { return hit; }
    public int getRank() { return rank; }
    public double getPrecision() { return precision; }
    public double getRecall() { return recall; }
    public double getReciprocalRank() { return reciprocalRank; }
}
