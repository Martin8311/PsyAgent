package com.mindbridge.controller;

import com.mindbridge.agent.AgentContext;
import com.mindbridge.agent.AgentRuntimeService;
import com.mindbridge.ai.ChatMessage;
import com.mindbridge.memory.ChatMemoryService;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.MediaType;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * 学生端聊天入口。
 *
 * <p>用户身份取自 Spring Security 登录上下文；经 {@link AgentRuntimeService}
 * 多 Agent 调度后流式返回，并在回答结束后把本轮对话写回 Redis 会话记录。
 */
@RestController
public class ChatController {

    private static final String ANONYMOUS_USER = "anonymous";

    private final AgentRuntimeService agentRuntime;
    private final ChatMemoryService chatMemoryService;

    public ChatController(AgentRuntimeService agentRuntime, ChatMemoryService chatMemoryService) {
        this.agentRuntime = agentRuntime;
        this.chatMemoryService = chatMemoryService;
    }

    @PostMapping(value = "/api/chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> chat(@RequestBody ChatRequest request) {
        return currentUserId().flatMapMany(userId -> stream(userId, request.message()));
    }

    @GetMapping(value = "/api/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> chatStream(@RequestParam("message") String message) {
        return currentUserId().flatMapMany(userId -> stream(userId, message));
    }

    /** 从响应式安全上下文取登录用户名，未登录则匿名。 */
    private Mono<String> currentUserId() {
        return ReactiveSecurityContextHolder.getContext()
                .map(ctx -> ctx.getAuthentication().getName())
                .defaultIfEmpty(ANONYMOUS_USER);
    }

    private Flux<String> stream(String userId, String message) {
        AgentContext context = new AgentContext(userId, message);
        StringBuilder full = new StringBuilder();
        return agentRuntime.run(context)
                .doOnNext(full::append)
                // 回答完整结束后，把这一轮对话写回 Redis(异步、失败不影响响应)
                .doOnComplete(() -> chatMemoryService.appendTurn(userId,
                        ChatMessage.user(message),
                        ChatMessage.assistant(full.toString())).subscribe());
    }

    public record ChatRequest(@NotBlank String message) {
    }
}
