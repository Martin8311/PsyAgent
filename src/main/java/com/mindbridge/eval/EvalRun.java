package com.mindbridge.eval;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * 一次评测运行的指标汇总。带检索参数快照(topK/minScore/chunkSize)，
 * 便于改参数重跑后对比指标趋势，指导调参。
 */
@Entity
@Table(name = "eval_run")
public class EvalRun {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Instant runAt = Instant.now();

    // ===== 参数快照 =====
    private int topK;
    private double minScore;
    private int chunkSize;

    private int queryCount;

    // ===== 文档级指标 =====
    private double avgPrecision;
    private double avgRecall;
    private double mrr;
    private double hitRate;

    @Column(length = 500)
    private String note;

    protected EvalRun() {
    }

    public EvalRun(int topK, double minScore, int chunkSize, String note) {
        this.runAt = Instant.now();
        this.topK = topK;
        this.minScore = minScore;
        this.chunkSize = chunkSize;
        this.note = note;
    }

    public Long getId() { return id; }
    public Instant getRunAt() { return runAt; }
    public int getTopK() { return topK; }
    public double getMinScore() { return minScore; }
    public int getChunkSize() { return chunkSize; }
    public int getQueryCount() { return queryCount; }
    public void setQueryCount(int queryCount) { this.queryCount = queryCount; }
    public double getAvgPrecision() { return avgPrecision; }
    public void setAvgPrecision(double avgPrecision) { this.avgPrecision = avgPrecision; }
    public double getAvgRecall() { return avgRecall; }
    public void setAvgRecall(double avgRecall) { this.avgRecall = avgRecall; }
    public double getMrr() { return mrr; }
    public void setMrr(double mrr) { this.mrr = mrr; }
    public double getHitRate() { return hitRate; }
    public void setHitRate(double hitRate) { this.hitRate = hitRate; }
    public String getNote() { return note; }
}
