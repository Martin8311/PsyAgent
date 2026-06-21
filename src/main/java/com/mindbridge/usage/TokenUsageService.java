package com.mindbridge.usage;

import com.mindbridge.ai.LlmCallMeta;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Token 用量记录与统计。
 *
 * <p>记录走 {@code boundedElastic} 异步落库——统计是观测性数据，
 * 丢失可容忍，不上 MQ/DLQ，也绝不拖慢对话主流程。
 */
@Service
public class TokenUsageService {

    private static final Logger log = LoggerFactory.getLogger(TokenUsageService.class);

    private final TokenUsageRepository repo;

    public TokenUsageService(TokenUsageRepository repo) {
        this.repo = repo;
    }

    /** 异步记录一次调用的 token 用量。 */
    public void record(LlmCallMeta meta, String model, int promptTokens, int completionTokens) {
        LlmCallMeta m = (meta == null) ? LlmCallMeta.UNKNOWN : meta;
        Mono.fromRunnable(() -> {
            try {
                repo.save(new TokenUsage(m.userId(), m.sessionId(), m.purpose().name(),
                        model, promptTokens, completionTokens));
            } catch (Exception e) {
                log.warn("token 用量落库失败(忽略): {}", e.toString());
            }
        }).subscribeOn(Schedulers.boundedElastic()).subscribe();
    }

    /** 后台统计：全局合计 + 按功能 / 用户 / 天 的聚合。 */
    public Map<String, Object> stats() {
        Map<String, Object> result = new LinkedHashMap<>();

        Object[] t = repo.totals().isEmpty() ? new Object[]{0L, 0L, 0L, 0L} : repo.totals().get(0);
        Map<String, Object> total = new LinkedHashMap<>();
        total.put("promptTokens", num(t[0]));
        total.put("completionTokens", num(t[1]));
        total.put("totalTokens", num(t[2]));
        total.put("calls", num(t[3]));
        result.put("total", total);

        result.put("byPurpose", map3(repo.sumByPurpose(), "purpose"));
        result.put("byUser", map3(repo.sumByUser(), "userId"));
        result.put("byDay", map3(repo.sumByDay(), "day"));
        return result;
    }

    /** 把 [key, totalTokens, calls] 行列表转成 map 列表。 */
    private List<Map<String, Object>> map3(List<Object[]> rows, String keyName) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (Object[] r : rows) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put(keyName, r[0] == null ? "" : r[0].toString());
            m.put("totalTokens", num(r[1]));
            m.put("calls", num(r[2]));
            out.add(m);
        }
        return out;
    }

    private static long num(Object o) {
        return (o instanceof Number n) ? n.longValue() : 0L;
    }
}
