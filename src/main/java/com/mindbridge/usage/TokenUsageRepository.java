package com.mindbridge.usage;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface TokenUsageRepository extends JpaRepository<TokenUsage, Long> {

    /** 全局合计：[promptTokens, completionTokens, totalTokens, calls]。 */
    @Query("SELECT COALESCE(SUM(t.promptTokens),0), COALESCE(SUM(t.completionTokens),0), "
            + "COALESCE(SUM(t.totalTokens),0), COUNT(t) FROM TokenUsage t")
    List<Object[]> totals();

    /** 按功能聚合：[purpose, totalTokens, calls]。 */
    @Query("SELECT t.purpose, SUM(t.totalTokens), COUNT(t) FROM TokenUsage t "
            + "GROUP BY t.purpose ORDER BY SUM(t.totalTokens) DESC")
    List<Object[]> sumByPurpose();

    /** 按用户聚合(Top)：[userId, totalTokens, calls]。 */
    @Query("SELECT t.userId, SUM(t.totalTokens), COUNT(t) FROM TokenUsage t "
            + "WHERE t.userId IS NOT NULL GROUP BY t.userId ORDER BY SUM(t.totalTokens) DESC")
    List<Object[]> sumByUser();

    /** 按天聚合(近 14 天)：[yyyy-MM-dd, totalTokens, calls]。 */
    @Query(value = "SELECT DATE(created_at) d, SUM(total_tokens), COUNT(*) FROM token_usage "
            + "GROUP BY DATE(created_at) ORDER BY d DESC LIMIT 14", nativeQuery = true)
    List<Object[]> sumByDay();
}
