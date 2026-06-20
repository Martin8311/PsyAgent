package com.mindbridge.risk;

/**
 * 心理风险等级。
 */
public enum RiskLevel {
    LOW,
    MEDIUM,
    HIGH;

    /** 取两者中更高的等级（用于硬规则与 LLM 结果合并，取最坏情况）。 */
    public RiskLevel max(RiskLevel other) {
        return this.ordinal() >= other.ordinal() ? this : other;
    }
}
