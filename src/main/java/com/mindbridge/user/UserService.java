package com.mindbridge.user;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/**
 * 用户业务逻辑。所有 JPA(阻塞) 调用统一放到 boundedElastic 线程池。
 */
@Service
public class UserService {

    private static final Logger log = LoggerFactory.getLogger(UserService.class);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    /**
     * 注册新用户（默认 STUDENT 角色）。用户名已存在则抛 {@link IllegalStateException}。
     * 昵称为空时默认取用户名。
     */
    public Mono<User> register(String username, String rawPassword, String nickname) {
        return Mono.fromCallable(() -> {
            if (userRepository.existsByUsername(username)) {
                throw new IllegalStateException("用户名已存在");
            }
            User user = new User(username, passwordEncoder.encode(rawPassword), Role.STUDENT);
            user.setNickname((nickname == null || nickname.isBlank()) ? username : nickname.trim());
            return userRepository.save(user);
        }).subscribeOn(Schedulers.boundedElastic());
    }

    /**
     * 按用户名查询用户（响应式包装）。
     */
    public Mono<User> findByUsername(String username) {
        return Mono.fromCallable(() -> userRepository.findByUsername(username)
                        .orElseThrow(() -> new IllegalStateException("用户不存在")))
                .subscribeOn(Schedulers.boundedElastic());
    }

    /**
     * 修改当前用户昵称。
     */
    public Mono<User> updateNickname(String username, String nickname) {
        return Mono.fromCallable(() -> {
            User user = userRepository.findByUsername(username)
                    .orElseThrow(() -> new IllegalStateException("用户不存在"));
            user.setNickname(nickname.trim());
            return userRepository.save(user);
        }).subscribeOn(Schedulers.boundedElastic());
    }

    /**
     * 若用户不存在则创建（用于启动时初始化种子账号，同步执行）。
     */
    public void createIfAbsent(String username, String rawPassword, Role role) {
        if (!userRepository.existsByUsername(username)) {
            User user = new User(username, passwordEncoder.encode(rawPassword), role);
            user.setNickname(username);
            userRepository.save(user);
            log.info("种子账号已创建: {} ({})", username, role);
        }
    }

    /**
     * 给昵称为空的历史用户回填昵称=用户名（启动时一次性迁移）。
     */
    public void backfillNicknames() {
        userRepository.findAll().forEach(u -> {
            if (u.getNickname() == null || u.getNickname().isBlank()) {
                u.setNickname(u.getUsername());
                userRepository.save(u);
            }
        });
    }
}
