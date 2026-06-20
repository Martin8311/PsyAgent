package com.mindbridge.user;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * 用户数据访问。注意：JPA 是阻塞的，调用方需在 boundedElastic 线程池中执行，
 * 避免阻塞 WebFlux 的事件循环线程。
 */
public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByUsername(String username);

    boolean existsByUsername(String username);
}
