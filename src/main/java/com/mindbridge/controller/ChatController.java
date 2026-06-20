package com.mindbridge.controller;

import com.mindbridge.agent.AgentContext;
import com.mindbridge.agent.AgentRuntimeService;
import com.mindbridge.ai.ChatMessage;
import com.mindbridge.memory.ChatMemoryService;
import com.mindbridge.session.SessionService;
import org.springframework.http.MediaType;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Map;

@RestController
public class ChatController {

    private static final String ANONYMOUS_USER = "anonymous";

    private final AgentRuntimeService agentRuntime;
    private final ChatMemoryService chatMemoryService;
    private final SessionService sessionService;

    public ChatController(AgentRuntimeService agentRuntime,
                          ChatMemoryService chatMemoryService,
                          SessionService sessionService) {
        this.agentRuntime = agentRuntime;
        this.chatMemoryService = chatMemoryService;
        this.sessionService = sessionService;
    }

    @PostMapping(value = "/api/chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> chat(@RequestBody Map<String, Object> body) {
        String message = (String) body.get("message");
        Long sessionId = body.get("sessionId") != null
                ? ((Number) body.get("sessionId")).longValue()
                : null;

        return currentUserId().flatMapMany(userId -> {
            Mono<Long> sessionMono = sessionId != null
                    ? Mono.just(sessionId)
                    : sessionService.createSession(userId).map(s -> s.getId());
            return sessionMono.flatMapMany(sid -> stream(userId, sid, message));
        });
    }

    private Mono<String> currentUserId() {
        return ReactiveSecurityContextHolder.getContext()
                .map(ctx -> ctx.getAuthentication().getName())
                .defaultIfEmpty(ANONYMOUS_USER);
    }

    private Flux<String> stream(String userId, Long sessionId, String message) {
        AgentContext context = new AgentContext(userId, sessionId, message);
        StringBuilder full = new StringBuilder();
        return agentRuntime.run(context)
                .doOnNext(full::append)
                .doOnComplete(() -> {
                    String aiReply = full.toString();
                    chatMemoryService.appendTurn(sessionId,
                            ChatMessage.user(message),
                            ChatMessage.assistant(aiReply)).subscribe();
                    sessionService.appendRecord(sessionId, userId, "user", message)
                            .then(sessionService.appendRecord(sessionId, userId, "assistant", aiReply))
                            .subscribe();
                });
    }
}
