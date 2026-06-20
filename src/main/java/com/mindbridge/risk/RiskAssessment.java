package com.mindbridge.risk;

import java.util.List;

/**
 * 一次心理风险评估的结果。
 *
 * @param level     风险等级
 * @param emotion   情绪标签
 * @param reason    研判理由（简短中文）
 * @param hitWords  命中的高风险关键词（硬规则触发时非空）
 * @param byHardRule 是否由高风险词硬规则判定（硬规则优先，保证不漏报）
 */
public record RiskAssessment(
        RiskLevel level,
        EmotionLabel emotion,
        String reason,
        List<String> hitWords,
        boolean byHardRule) {

    public boolean isHigh() {
        return level == RiskLevel.HIGH;
    }

    public static RiskAssessment normal() {
        return new RiskAssessment(RiskLevel.LOW, EmotionLabel.NORMAL, "未见明显风险信号", List.of(), false);
    }
}
