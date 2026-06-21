package com.mindbridge.controller;

import com.mindbridge.memory.LongTermMemoryService;
import com.mindbridge.memory.UserMemory;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.List;
import java.util.Map;

/**
 * 「我的记忆」隐私接口：学生本人可查看 / 删除 AI 记住的关于自己的长期记忆。
 * 仅操作当前登录用户自己的数据，体现知情与可控(心理产品伦理底线)。
 */
@RestController
@RequestMapping("/api/memory")
public class MemoryController {

    private final LongTermMemoryService memoryService;

    public MemoryController(LongTermMemoryService memoryService) {
        this.memoryService = memoryService;
    }

    private Mono<String> currentUserId() {
        return ReactiveSecurityContextHolder.getContext()
                .map(ctx -> ctx.getAuthentication().getName());
    }

    /** 本人的全部有效长期记忆。 */
    @GetMapping
    public Mono<List<Map<String, Object>>> myMemories() {
        return currentUserId().flatMap(userId ->
                Mono.fromCallable(() -> memoryService.listForUser(userId).stream()
                                .map(this::toMap).toList())
                        .subscribeOn(Schedulers.boundedElastic()));
    }

    /** 删除本人的一条记忆(软删，仅能删自己的)。 */
    @DeleteMapping("/{id}")
    public Mono<Map<String, Object>> deleteMemory(@PathVariable Long id) {
        return currentUserId().flatMap(userId ->
                Mono.fromCallable(() -> Map.<String, Object>of("deleted", memoryService.softDelete(userId, id)))
                        .subscribeOn(Schedulers.boundedElastic()));
    }

    private Map<String, Object> toMap(UserMemory m) {
        return Map.of(
                "id", m.getId(),
                "type", m.getType(),
                "memoryKey", m.getMemoryKey() == null ? "" : m.getMemoryKey(),
                "content", m.getContent(),
                "updatedAt", m.getUpdatedAt().toString());
    }
}
