package com.mindbridge.controller;

import com.mindbridge.memory.ChatMemoryService;
import com.mindbridge.session.ChatRecord;
import com.mindbridge.session.ChatSession;
import com.mindbridge.session.SessionService;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/sessions")
public class SessionController {

    private final SessionService sessionService;
    private final ChatMemoryService chatMemoryService;

    public SessionController(SessionService sessionService, ChatMemoryService chatMemoryService) {
        this.sessionService = sessionService;
        this.chatMemoryService = chatMemoryService;
    }

    @PostMapping
    public Mono<Map<String, Object>> create() {
        return userId().flatMap(sessionService::createSession)
                .map(s -> Map.<String, Object>of(
                        "id", s.getId(),
                        "title", s.getTitle(),
                        "messageCount", s.getMessageCount(),
                        "createdAt", s.getCreatedAt().toString(),
                        "updatedAt", s.getUpdatedAt().toString()));
    }

    @GetMapping
    public Mono<List<Map<String, Object>>> list() {
        return userId().flatMap(sessionService::listSessions)
                .map(list -> list.stream().map(this::sessionMap).toList());
    }

    @GetMapping("/{id}/messages")
    public Mono<List<Map<String, Object>>> messages(@PathVariable Long id) {
        return userId().flatMap(uid -> sessionService.getMessages(id, uid))
                .map(records -> records.stream().map(this::recordMap).toList());
    }

    @DeleteMapping("/{id}")
    public Mono<Map<String, Object>> delete(@PathVariable Long id) {
        return userId().flatMap(uid -> sessionService.deleteSession(id, uid))
                .flatMap(sessionId -> chatMemoryService.clearSession(sessionId)
                        .thenReturn(Map.<String, Object>of("deleted", sessionId)));
    }

    @GetMapping("/search")
    public Mono<List<Map<String, Object>>> search(@RequestParam String q) {
        return userId().flatMap(uid -> sessionService.searchMessages(uid, q));
    }

    private Mono<String> userId() {
        return ReactiveSecurityContextHolder.getContext()
                .map(ctx -> ctx.getAuthentication().getName());
    }

    private Map<String, Object> sessionMap(ChatSession s) {
        return Map.of(
                "id", s.getId(),
                "title", s.getTitle(),
                "messageCount", s.getMessageCount(),
                "createdAt", s.getCreatedAt().toString(),
                "updatedAt", s.getUpdatedAt().toString());
    }

    private Map<String, Object> recordMap(ChatRecord r) {
        return Map.of(
                "id", r.getId(),
                "sessionId", r.getSessionId(),
                "role", r.getRole(),
                "content", r.getContent(),
                "createdAt", r.getCreatedAt().toString());
    }
}
