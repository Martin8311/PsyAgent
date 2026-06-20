package com.mindbridge.knowledge;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface KbDocumentRepository extends JpaRepository<KbDocument, Long> {

    /** 按上传时间倒序列出全部文档（管理员后台用）。 */
    List<KbDocument> findAllByOrderByCreatedAtDesc();
}
