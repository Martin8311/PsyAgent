package com.mindbridge.ai;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * ContextBudgetService 上下文护栏单元测试：token 估算 + 预算裁剪。
 */
class ContextBudgetServiceTest {

    private ContextBudgetService service(int numCtx) {
        AiProperties p = new AiProperties();
        p.getOllama().setNumCtx(numCtx);
        return new ContextBudgetService(p);
    }

    @Test
    void estimateNonEmptyAndZero() {
        ContextBudgetService svc = service(8192);
        assertTrue(svc.estimate("你好") >= 1);
        assertEquals(0, svc.estimate(""));
        assertEquals(0, svc.estimate((String) null));
    }

    @Test
    void fitKeepsAllWhenBudgetAmple() {
        ContextBudgetService svc = service(8192);
        List<ChatMessage> head = List.of(ChatMessage.system("系统提示"));
        List<ChatMessage> history = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            history.add(ChatMessage.user("历史消息" + i));
        }
        ContextBudgetService.Fit fit = svc.fit(head, history, ChatMessage.user("当前问题"));
        assertEquals(0, fit.droppedHistory());
        assertEquals("系统提示", fit.messages().get(0).content());
        assertEquals("当前问题", fit.messages().get(fit.messages().size() - 1).content());
    }

    @Test
    void fitDropsOldestHistoryWhenOverBudget() {
        ContextBudgetService svc = service(80);   // 预算 = 80 * 0.75 = 60 token
        List<ChatMessage> head = List.of(ChatMessage.system("系统提示词比较长一些占用预算空间"));
        List<ChatMessage> history = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            history.add(ChatMessage.user("这是一条比较长的历史消息内容" + i));
        }
        ContextBudgetService.Fit fit = svc.fit(head, history, ChatMessage.user("当前问题"));
        assertTrue(fit.droppedHistory() > 0, "超预算应裁掉最旧历史");
        // 当前输入必保
        assertEquals("当前问题", fit.messages().get(fit.messages().size() - 1).content());
    }
}
