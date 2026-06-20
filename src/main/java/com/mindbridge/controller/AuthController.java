package com.mindbridge.controller;

import com.mindbridge.user.UserService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

/**
 * 认证相关接口。注册接口对未登录用户开放。
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final UserService userService;

    public AuthController(UserService userService) {
        this.userService = userService;
    }

    /** 学生自助注册。昵称可选，不填默认用用户名。 */
    @PostMapping("/register")
    public Mono<ResponseEntity<Map<String, Object>>> register(@Valid @RequestBody RegisterRequest req) {
        return userService.register(req.username(), req.password(), req.nickname())
                .map(user -> ResponseEntity.status(HttpStatus.CREATED)
                        .body(Map.<String, Object>of(
                                "id", user.getId(),
                                "username", user.getUsername(),
                                "nickname", user.displayName(),
                                "role", user.getRole().name(),
                                "message", "注册成功")))
                .onErrorResume(IllegalStateException.class, e -> Mono.just(
                        ResponseEntity.status(HttpStatus.CONFLICT)
                                .body(Map.of("message", e.getMessage()))));
    }

    /** 修改个人信息（当前仅昵称）。 */
    @PutMapping("/profile")
    public Mono<ResponseEntity<Map<String, Object>>> updateProfile(@Valid @RequestBody ProfileRequest req) {
        return currentUsername()
                .flatMap(username -> userService.updateNickname(username, req.nickname()))
                .map(user -> ResponseEntity.ok(Map.<String, Object>of(
                        "username", user.getUsername(),
                        "nickname", user.displayName(),
                        "message", "已保存")));
    }

    /**
     * 获取当前登录用户信息。前端登录时用它校验凭证，并拿到角色用于界面展示。
     * 未登录会被 Security 拦截返回 401。
     */
    @GetMapping("/me")
    public Mono<Map<String, Object>> me() {
        return ReactiveSecurityContextHolder.getContext()
                .flatMap(ctx -> {
                    var auth = ctx.getAuthentication();
                    List<String> roles = auth.getAuthorities().stream()
                            .map(a -> a.getAuthority())
                            .toList();
                    return userService.findByUsername(auth.getName())
                            .map(user -> Map.<String, Object>of(
                                    "username", user.getUsername(),
                                    "nickname", user.displayName(),
                                    "roles", roles));
                });
    }

    /** 取当前登录用户名。 */
    private Mono<String> currentUsername() {
        return ReactiveSecurityContextHolder.getContext()
                .map(ctx -> ctx.getAuthentication().getName());
    }

    public record RegisterRequest(
            @NotBlank @Size(min = 3, max = 64) String username,
            @NotBlank @Size(min = 6, max = 64) String password,
            @Size(max = 32) String nickname) {
    }

    public record ProfileRequest(
            @NotBlank @Size(min = 1, max = 32) String nickname) {
    }
}
