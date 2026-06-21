package com.mindbridge.ai;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 上下文护栏：在调用 LLM 前估算 prompt token，控制在预算内，超出则裁剪。
 *
 * <p>Ollama 的 num_ctx 是 prompt+生成的总窗口，prompt 占满会让回复被截断、
 * 甚至最前面的 system 提示被丢。本服务事前用字符启发式估算 token（事后有
 * {@code token_usage} 的真实计数可校准），按优先级保留：
 * 固定前置(system/风险/知识/记忆) + 当前用户输入必保，历史对话从最旧开始裁剪。
 */
@Component
public class ContextBudgetService {

    private static final Logger log = LoggerFactory.getLogger(ContextBudgetService.class);

    /** prompt 占 num_ctx 的比例上限，剩余留给模型生成。 */
    private static final double BUDGET_RATIO = 0.75;
    /** 每条消息的角色/格式固定开销(token)。 */
    private static final int MSG_OVERHEAD = 4;

    private final int numCtx;

    public ContextBudgetService(AiProperties props) {
        this.numCtx = props.getOllama().getNumCtx();
    }

    /** 估算一段文本的 token：CJK≈0.75，ASCII≈0.3，其余≈0.6（偏保守，宁可高估早裁剪）。 */
    public int estimate(String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        double t = 0;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c <= 0x7F) {
                t += 0.3;
            } else if (c >= 0x4E00 && c <= 0x9FFF) {
                t += 0.75;
            } else {
                t += 0.6;
            }
        }
        return (int) Math.ceil(t);
    }

    public int estimate(ChatMessage m) {
        return estimate(m.content()) + MSG_OVERHEAD;
    }

    public int estimate(List<ChatMessage> ms) {
        int s = 0;
        for (ChatMessage m : ms) {
            s += estimate(m);
        }
        return s;
    }

    /**
     * 在预算内拼装最终消息：fixedHead(必保) + 尽量多的近期 history + tail(当前用户输入)。
     * history 从最旧开始裁剪。返回拼好的消息与裁剪信息。
     */
    public Fit fit(List<ChatMessage> fixedHead, List<ChatMessage> history, ChatMessage tail) {
        int budget = (int) (numCtx * BUDGET_RATIO);
        int fixed = estimate(fixedHead) + estimate(tail);
        int avail = budget - fixed;

        List<ChatMessage> kept = new ArrayList<>();
        int used = 0;
        for (int i = history.size() - 1; i >= 0 && avail > 0; i--) {
            int cost = estimate(history.get(i));
            if (used + cost > avail) {
                break;
            }
            kept.add(0, history.get(i));
            used += cost;
        }
        int dropped = history.size() - kept.size();

        List<ChatMessage> out = new ArrayList<>(fixedHead.size() + kept.size() + 1);
        out.addAll(fixedHead);
        out.addAll(kept);
        out.add(tail);

        boolean overflow = avail <= 0;   // 仅固定部分已超预算(注入太多)
        if (dropped > 0 || overflow) {
            log.warn("上下文护栏触发: 预算={} tok, 固定部分≈{} tok, 裁掉最旧 {} 条历史{}",
                    budget, fixed, dropped, overflow ? " [固定部分已超预算，建议减少知识/记忆注入]" : "");
        }
        return new Fit(out, dropped, fixed + used, budget, overflow);
    }

    /** 裁剪结果：拼好的消息、裁掉的历史条数、估算 token、预算、是否固定部分溢出。 */
    public record Fit(List<ChatMessage> messages, int droppedHistory, int estTokens,
                      int budget, boolean overflow) {
    }
}
