package com.mindbridge.eval;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface EvalResultItemRepository extends JpaRepository<EvalResultItem, Long> {

    List<EvalResultItem> findByRunIdOrderByIdAsc(Long runId);
}
