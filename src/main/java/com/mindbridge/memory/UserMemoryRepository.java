package com.mindbridge.memory;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UserMemoryRepository extends JpaRepository<UserMemory, Long> {

    /** 某用户的全部有效记忆(隐私页/召回用)，按更新时间倒序。 */
    List<UserMemory> findByUserIdAndStatusOrderByUpdatedAtDesc(String userId, String status);

    /** 某用户某类型的有效记忆(FACT/SUMMARY 分别召回)。 */
    List<UserMemory> findByUserIdAndTypeAndStatusOrderByUpdatedAtDesc(String userId, String type, String status);

    /** 按 userId + memoryKey 找有效事实，用于去重覆盖。 */
    Optional<UserMemory> findFirstByUserIdAndTypeAndMemoryKeyAndStatus(
            String userId, String type, String memoryKey, String status);

    /** 按 userId + 来源会话找有效摘要，用于一个会话维护一条摘要(覆盖更新)。 */
    Optional<UserMemory> findFirstByUserIdAndTypeAndSourceSessionIdAndStatus(
            String userId, String type, Long sourceSessionId, String status);
}
