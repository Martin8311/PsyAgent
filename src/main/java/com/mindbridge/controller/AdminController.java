package com.mindbridge.controller;

import com.mindbridge.risk.RiskAlertRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.List;
import java.util.Map;

/**
 * 管理员后台接口。整个 /api/admin/** 由 Spring Security 限定 ADMIN 角色。
 */
@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final RiskAlertRepository alertRepository;

    public AdminController(RiskAlertRepository alertRepository) {
        this.alertRepository = alertRepository;
    }

    /** 高风险预警台账（按时间倒序）。 */
    @GetMapping("/alerts")
    public Mono<List<Map<String, Object>>> alerts() {
        return Mono.fromCallable(() -> alertRepository.findAllByOrderByCreatedAtDesc().stream()
                        .map(a -> Map.<String, Object>of(
                                "id", a.getId(),
                                "userId", a.getUserId(),
                                "level", a.getLevel().name(),
                                "emotion", a.getEmotion().name(),
                                "userMessage", a.getUserMessage(),
                                "reason", a.getReason() == null ? "" : a.getReason(),
                                "createdAt", a.getCreatedAt().toString()))
                        .toList())
                .subscribeOn(Schedulers.boundedElastic());
    }
}
