package com.mindbridge.security;

import com.mindbridge.user.UserRepository;
import org.springframework.security.core.userdetails.ReactiveUserDetailsService;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/**
 * 把阻塞的 JPA 查询适配成响应式的 {@link ReactiveUserDetailsService}。
 *
 * <p>关键点：JPA 调用是阻塞的，必须放到 {@link Schedulers#boundedElastic()}
 * 弹性线程池执行，绝不能跑在 WebFlux 的事件循环线程上。
 */
@Service
public class JpaReactiveUserDetailsService implements ReactiveUserDetailsService {

    private final UserRepository userRepository;

    public JpaReactiveUserDetailsService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public Mono<UserDetails> findByUsername(String username) {
        return Mono.fromCallable(() -> userRepository.findByUsername(username))
                .subscribeOn(Schedulers.boundedElastic())
                .flatMap(opt -> opt.map(Mono::just).orElseGet(Mono::empty))
                .map(u -> User.withUsername(u.getUsername())
                        .password(u.getPassword())
                        .roles(u.getRole().name())   // roles() 会自动加 ROLE_ 前缀
                        .disabled(!u.isEnabled())
                        .build());
    }
}
