package com.mindbridge.controller;

import com.mindbridge.knowledge.KnowledgeBaseService;
import com.mindbridge.risk.RiskAlertRepository;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

/**
 * 管理员后台接口。整个 /api/admin/** 由 Spring Security 限定 ADMIN 角色。
 */
@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final RiskAlertRepository alertRepository;
    private final KnowledgeBaseService knowledgeBaseService;

    public AdminController(RiskAlertRepository alertRepository,
                          KnowledgeBaseService knowledgeBaseService) {
        this.alertRepository = alertRepository;
        this.knowledgeBaseService = knowledgeBaseService;
    }

    /** 高风险预警台账（按时间倒序）。 */
    @GetMapping("/alerts")
    public Mono<List<Map<String, Object>>> alerts() {
        return Mono.fromCallable(() -> alertRepository.findAllByOrderByCreatedAtDesc().stream()
                        .map(a -> Map.<String, Object>of(
                                "id", a.getId(),
                                "userId", a.getUserId(),
                                "level", a.getLevel().name(),
                                "emotion", a.getEmotion().name(),
                                "userMessage", a.getUserMessage(),
                                "reason", a.getReason() == null ? "" : a.getReason(),
                                "createdAt", a.getCreatedAt().toString()))
                        .toList())
                .subscribeOn(Schedulers.boundedElastic());
    }

    // ===================== 知识库管理 (RAG) =====================

    /** 上传文档入库（仅 txt/md），切块向量化进 Chroma。 */
    @PostMapping(value = "/kb/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Mono<Map<String, Object>> uploadKb(@RequestPart("file") FilePart file,
                                              @RequestParam(value = "title", required = false) String title) {
        // 浏览器 multipart filename 以 UTF-8 编码，但 HTTP 头按 ISO-8859-1 解析，需要转码
        String filename = new String(file.filename().getBytes(StandardCharsets.ISO_8859_1), StandardCharsets.UTF_8);
        String lower = filename.toLowerCase();
        if (!lower.endsWith(".txt") && !lower.endsWith(".md")) {
            return Mono.error(new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "仅支持 .txt / .md 文档"));
        }
        String docTitle = (title == null || title.isBlank()) ? stripExtension(filename) : title;

        return DataBufferUtils.join(file.content())
                .map(buffer -> {
                    byte[] bytes = new byte[buffer.readableByteCount()];
                    buffer.read(bytes);
                    DataBufferUtils.release(buffer);
                    return new String(bytes, StandardCharsets.UTF_8);
                })
                .flatMap(content -> knowledgeBaseService.ingest(docTitle, filename, content))
                .map(doc -> Map.<String, Object>of(
                        "id", doc.getId(),
                        "title", doc.getTitle(),
                        "chunkCount", doc.getChunkCount(),
                        "status", doc.getStatus()));
    }

    /** 知识库文档列表。 */
    @GetMapping("/kb/docs")
    public Mono<List<Map<String, Object>>> kbDocs() {
        return knowledgeBaseService.listDocuments()
                .map(list -> list.stream()
                        .map(d -> Map.<String, Object>of(
                                "id", d.getId(),
                                "title", d.getTitle(),
                                "filename", d.getFilename() == null ? "" : d.getFilename(),
                                "charCount", d.getCharCount(),
                                "chunkCount", d.getChunkCount(),
                                "status", d.getStatus(),
                                "createdAt", d.getCreatedAt().toString()))
                        .toList());
    }

    /** 查看文档切块列表（向量化内容）。 */
    @GetMapping("/kb/docs/{id}/chunks")
    public Mono<Map<String, Object>> kbDocChunks(@PathVariable Long id) {
        return knowledgeBaseService.getDocumentChunks(id);
    }

    /** 查看文档原始内容（管理后台预览）。 */
    @GetMapping("/kb/docs/{id}/content")
    public Mono<Map<String, Object>> kbDocContent(@PathVariable Long id) {
        return knowledgeBaseService.getDocumentContent(id);
    }

    /** 删除一篇文档（连同其在 Chroma 中的向量）。 */
    @DeleteMapping("/kb/docs/{id}")
    public Mono<Map<String, Object>> deleteKbDoc(@PathVariable Long id) {
        return knowledgeBaseService.deleteDocument(id)
                .thenReturn(Map.<String, Object>of("deleted", id));
    }

    private static String stripExtension(String filename) {
        int dot = filename.lastIndexOf('.');
        return dot > 0 ? filename.substring(0, dot) : filename;
    }
}
