package com.mindbridge.controller;

import com.mindbridge.agent.AgentContext;
import com.mindbridge.agent.AgentRuntimeService;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

/**
 * 学生端聊天入口。
 *
 * <p>统一经过 {@link AgentRuntimeService} 多 Agent 调度：
 * MemoryAgent → SupervisorAgent → 按意图分支(Companion / Knowledge→Risk→Counselor)
 * → 流式 SSE 返回。
 */
@RestController
public class ChatController {

    /** Phase 2 暂以固定值代表匿名学生；Phase 3 接入安全上下文取真实身份。 */
    private static final String ANONYMOUS_USER = "anonymous";

    private final AgentRuntimeService agentRuntime;

    public ChatController(AgentRuntimeService agentRuntime) {
        this.agentRuntime = agentRuntime;
    }

    /** POST 方式，正式入口。 */
    @PostMapping(value = "/api/chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> chat(@RequestBody ChatRequest request) {
        return stream(ANONYMOUS_USER, request.message());
    }

    /** GET 方式，便于浏览器/curl 快速测试。 */
    @GetMapping(value = "/api/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> chatStream(@RequestParam("message") String message) {
        return stream(ANONYMOUS_USER, message);
    }

    private Flux<String> stream(String userId, String message) {
        AgentContext context = new AgentContext(userId, message);
        return agentRuntime.run(context);
    }

    public record ChatRequest(@NotBlank String message) {
    }
}
