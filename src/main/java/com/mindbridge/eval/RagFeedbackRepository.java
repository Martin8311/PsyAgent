package com.mindbridge.eval;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RagFeedbackRepository extends JpaRepository<RagFeedback, Long> {

    List<RagFeedback> findTop200ByOrderByCreatedAtDesc();

    long countByRating(String rating);
}
