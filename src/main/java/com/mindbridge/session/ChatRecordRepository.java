package com.mindbridge.session;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

public interface ChatRecordRepository extends JpaRepository<ChatRecord, Long> {

    List<ChatRecord> findBySessionIdOrderByCreatedAtAsc(Long sessionId);

    List<ChatRecord> findTop30ByUserIdAndContentContainingOrderByCreatedAtDesc(String userId, String content);

    @Modifying
    @Transactional
    @Query("DELETE FROM ChatRecord r WHERE r.sessionId = :sessionId")
    void deleteBySessionId(@Param("sessionId") Long sessionId);
}
