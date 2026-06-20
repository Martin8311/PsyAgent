package com.mindbridge.knowledge;

import jakarta.persistence.Basic;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "kb_document")
public class KbDocument {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 256)
    private String title;

    @Column(length = 256)
    private String filename;

    @Column(nullable = false)
    private int charCount;

    @Column(nullable = false)
    private int chunkCount;

    @Column(nullable = false, length = 16)
    private String status;

    @Column(nullable = false)
    private Instant createdAt = Instant.now();

    /** 原始文本内容，用于管理后台预览。Hibernate 6 @Lob 默认懒加载，显式设为 EAGER。 */
    @Lob
    @Basic(fetch = FetchType.EAGER)
    @Column(columnDefinition = "LONGTEXT")
    private String content;

    protected KbDocument() {
    }

    public KbDocument(String title, String filename, int charCount, int chunkCount, String status, String content) {
        this.title = title;
        this.filename = filename;
        this.charCount = charCount;
        this.chunkCount = chunkCount;
        this.status = status;
        this.content = content;
        this.createdAt = Instant.now();
    }

    public Long getId() { return id; }
    public String getTitle() { return title; }
    public String getFilename() { return filename; }
    public int getCharCount() { return charCount; }
    public int getChunkCount() { return chunkCount; }
    public String getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
    public String getContent() { return content; }
}
