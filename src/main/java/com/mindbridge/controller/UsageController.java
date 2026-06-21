package com.mindbridge.controller;

import com.mindbridge.usage.TokenUsageService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.Map;

/**
 * Token 用量统计后台接口。隶属 /api/admin/**，由 Spring Security 限定 ADMIN。
 */
@RestController
@RequestMapping("/api/admin/usage")
public class UsageController {

    private final TokenUsageService usageService;

    public UsageController(TokenUsageService usageService) {
        this.usageService = usageService;
    }

    /** 全局合计 + 按功能 / 用户 / 天 的 token 用量聚合。 */
    @GetMapping("/stats")
    public Mono<Map<String, Object>> stats() {
        return Mono.fromCallable(usageService::stats).subscribeOn(Schedulers.boundedElastic());
    }
}
