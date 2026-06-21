package com.mindbridge.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.ServerAuthenticationEntryPoint;
import reactor.core.publisher.Mono;

/**
 * 响应式安全配置（WebFlux）。
 *
 * <p>采用 HTTP Basic 登录；按角色保护接口：
 * <ul>
 *   <li>/api/admin/** 仅 ADMIN</li>
 *   <li>/api/chat/**  需登录(STUDENT/ADMIN)</li>
 *   <li>首页、注册、健康检查 放行</li>
 * </ul>
 */
@Configuration
@EnableWebFluxSecurity
public class SecurityConfig {

    @Bean
    public SecurityWebFilterChain securityWebFilterChain(ServerHttpSecurity http) {
        return http
                // 前后端分离 + Basic 认证，关闭 CSRF
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                // 保留 HTTP Basic 认证能力，但替换"挑战"响应：
                // 未认证时只返回 401，不带 WWW-Authenticate 头，避免浏览器弹原生登录框
                .httpBasic(httpBasic -> httpBasic.authenticationEntryPoint(noBrowserPopupEntryPoint()))
                // 关闭表单登录(我们用 Basic)
                .formLogin(ServerHttpSecurity.FormLoginSpec::disable)
                .authorizeExchange(ex -> ex
                        // 放行：首页、所有静态页(*.html)、静态资源、注册、健康检查
                        // 注意：页面本身放行，真正的数据保护在 /api/admin/** 接口上
                        .pathMatchers("/", "/*.html", "/favicon.ico",
                                "/css/**", "/js/**", "/actuator/health").permitAll()
                        .pathMatchers("/api/auth/register").permitAll()
                        // MCP Server SSE 端点(本地开发放行; 生产应改为 token 鉴权)
                        .pathMatchers("/sse/**", "/mcp/**").permitAll()
                        // 管理员后台
                        .pathMatchers("/api/admin/**").hasRole("ADMIN")
                        // 聊天 + 会话管理需登录
                        .pathMatchers("/api/chat", "/api/chat/**", "/api/sessions/**", "/api/feedback").authenticated()
                        // 其余一律需要认证
                        .anyExchange().authenticated()
                )
                .build();
    }

    /**
     * 自定义未认证入口：仅返回 401，不写 {@code WWW-Authenticate: Basic} 头，
     * 这样浏览器不会弹出原生登录框，登录交互完全由前端登录页接管。
     */
    private ServerAuthenticationEntryPoint noBrowserPopupEntryPoint() {
        return (exchange, ex) -> {
            exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
            return Mono.empty();
        };
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
