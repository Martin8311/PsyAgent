package com.mindbridge.risk;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RiskAlertRepository extends JpaRepository<RiskAlert, Long> {

    /** 按时间倒序列出全部预警（管理员后台用）。 */
    List<RiskAlert> findAllByOrderByCreatedAtDesc();

    /** 某用户的预警记录。 */
    List<RiskAlert> findByUserIdOrderByCreatedAtDesc(String userId);
}
