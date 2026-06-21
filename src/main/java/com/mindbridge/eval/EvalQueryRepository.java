package com.mindbridge.eval;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

public interface EvalQueryRepository extends JpaRepository<EvalQuery, Long> {

    List<EvalQuery> findAllByOrderByCreatedAtDesc();

    List<EvalQuery> findByReviewedTrueOrderByCreatedAtDesc();

    long countByReviewedTrue();

    /** 删除某文档关联的评测样例(文档被删时清理)。 */
    @Modifying
    @Transactional
    @Query("DELETE FROM EvalQuery q WHERE q.expectedDocId = :docId")
    void deleteByExpectedDocId(Long docId);

    /** 清空所有未校正样例(重新生成前清场)，返回删除条数。 */
    @Modifying
    @Transactional
    @Query("DELETE FROM EvalQuery q WHERE q.reviewed = false")
    int deleteByReviewedFalse();
}
