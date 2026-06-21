package com.mindbridge.knowledge;

import com.mindbridge.ai.AiClient;
import com.mindbridge.ai.ChatMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * LLM 重排（listwise rerank）——RAG 检索的精排层。
 *
 * <p>混合检索保证了「答案在候选池里」(召回)，但 ANN/BM25 的排序精度有限，
 * 正确答案常排在第 4~N 名挤不进 topK。本服务把候选交给对话模型(qwen2.5:3b)，
 * 让它按与 query 的相关性重新挑出最相关的 topK 段，直接提升 Hit@topK 与 MRR。
 *
 * <p>采用 listwise：一次调用列出全部候选让 LLM 输出排序编号，比 pointwise 逐段打分
 * 省 N-1 次调用。健壮性优先：解析失败/超时/输出不全，一律回退到原融合顺序，
 * 绝不因重排失败而让检索链路中断。
 */
@Component
public class RerankService {

    private static final Logger log = LoggerFactory.getLogger(RerankService.class);

    /** 每个候选送进 LLM 的文本截断长度，控制 prompt 体积。 */
    private static final int SNIPPET = 160;

    private final Pattern numberPattern = Pattern.compile("\\d+");

    private final AiClient aiClient;
    private final KnowledgeProperties props;

    public RerankService(AiClient aiClient, KnowledgeProperties props) {
        this.aiClient = aiClient;
        this.props = props;
    }

    /**
     * 对候选列表做 LLM 重排，返回最相关的 topK 段。
     * 候选数 ≤ topK 时无需重排直接返回；任何异常都回退原顺序。
     */
    public List<RetrievedChunk> rerank(String query, List<RetrievedChunk> candidates, int topK) {
        if (candidates == null || candidates.size() <= topK) {
            return candidates;
        }
        int n = Math.min(props.getRerankTopN(), candidates.size());
        List<RetrievedChunk> pool = candidates.subList(0, n);
        try {
            String raw = aiClient.chat(List.of(
                    ChatMessage.system("你是检索结果重排助手，只输出编号，不要任何解释。"),
                    ChatMessage.user(buildPrompt(query, pool, topK)))).block();
            List<Integer> order = parseOrder(raw, n);
            if (order.isEmpty()) {
                log.warn("LLM 重排未解析到有效编号，回退融合顺序。raw='{}'", abbreviate(raw));
                return candidates;
            }
            List<RetrievedChunk> reranked = new ArrayList<>(topK);
            boolean[] used = new boolean[n];
            for (int idx : order) {
                reranked.add(pool.get(idx));
                used[idx] = true;
                if (reranked.size() >= topK) {
                    break;
                }
            }
            // LLM 选不够 topK 时，用未选中的候选按原融合序补齐
            for (int i = 0; i < n && reranked.size() < topK; i++) {
                if (!used[i]) {
                    reranked.add(pool.get(i));
                }
            }
            return reranked;
        } catch (Exception e) {
            log.warn("LLM 重排失败，回退融合顺序: {}", e.toString());
            return candidates;
        }
    }

    private String buildPrompt(String query, List<RetrievedChunk> pool, int topK) {
        StringBuilder sb = new StringBuilder();
        sb.append("根据【问题】，从下列候选资料中挑出最相关的 ").append(topK)
                .append(" 段，按相关性从高到低输出它们的编号，用逗号分隔(例如 3,1,5)。")
                .append("只输出编号，不要其他文字。\n\n");
        sb.append("【问题】").append(query).append("\n\n【候选资料】\n");
        for (int i = 0; i < pool.size(); i++) {
            String t = pool.get(i).text();
            t = (t == null) ? "" : t.replace('\n', ' ');
            if (t.length() > SNIPPET) {
                t = t.substring(0, SNIPPET);
            }
            sb.append('[').append(i + 1).append("] ").append(t).append('\n');
        }
        sb.append("\n最相关的 ").append(topK).append(" 段编号：");
        return sb.toString();
    }

    /** 从 LLM 输出提取候选编号(1-based→0-based)，去重、越界丢弃。 */
    private List<Integer> parseOrder(String raw, int n) {
        List<Integer> order = new ArrayList<>();
        if (raw == null) {
            return order;
        }
        Matcher m = numberPattern.matcher(raw);
        Set<Integer> seen = new HashSet<>();
        while (m.find()) {
            int v = Integer.parseInt(m.group()) - 1;
            if (v >= 0 && v < n && seen.add(v)) {
                order.add(v);
            }
        }
        return order;
    }

    private static String abbreviate(String s) {
        if (s == null) {
            return "null";
        }
        s = s.replace('\n', ' ');
        return s.length() > 60 ? s.substring(0, 60) + "…" : s;
    }
}
