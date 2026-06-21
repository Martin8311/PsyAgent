package com.mindbridge.controller;

import com.mindbridge.async.ExcelLogService;
import com.mindbridge.knowledge.KnowledgeBaseService;
import com.mindbridge.risk.AlertService;
import com.mindbridge.risk.RiskAlertRepository;
import com.mindbridge.user.User;
import com.mindbridge.user.UserService;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
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
    private final AlertService alertService;
    private final UserService userService;
    private final ExcelLogService excelLogService;

    public AdminController(RiskAlertRepository alertRepository,
                          KnowledgeBaseService knowledgeBaseService,
                          AlertService alertService,
                          UserService userService,
                          ExcelLogService excelLogService) {
        this.alertRepository = alertRepository;
        this.knowledgeBaseService = knowledgeBaseService;
        this.alertService = alertService;
        this.userService = userService;
        this.excelLogService = excelLogService;
    }

    private Mono<String> currentAdmin() {
        return ReactiveSecurityContextHolder.getContext()
                .map(ctx -> ctx.getAuthentication().getName());
    }

    // ===================== 高危预警台账 =====================

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
                                "status", a.getStatus().name(),
                                "handledBy", a.getHandledBy() == null ? "" : a.getHandledBy(),
                                "notifiedAt", a.getNotifiedAt() == null ? "" : a.getNotifiedAt().toString(),
                                "createdAt", a.getCreatedAt().toString()))
                        .toList())
                .subscribeOn(Schedulers.boundedElastic());
    }

    /** 第二跳：管理员核实后通知监护人。 */
    @PostMapping("/alerts/{id}/notify-guardian")
    public Mono<Map<String, Object>> notifyGuardian(@PathVariable Long id) {
        return currentAdmin().flatMap(admin -> alertService.notifyGuardian(id, admin))
                .map(a -> Map.<String, Object>of("id", a.getId(), "status", a.getStatus().name()));
    }

    /** 标记预警为误报（忽略）。 */
    @PostMapping("/alerts/{id}/dismiss")
    public Mono<Map<String, Object>> dismissAlert(@PathVariable Long id) {
        return currentAdmin().flatMap(admin -> alertService.dismiss(id, admin))
                .map(a -> Map.<String, Object>of("id", a.getId(), "status", a.getStatus().name()));
    }

    // ===================== 学生 / 监护人管理 =====================

    /** 学生列表（含监护人信息）。 */
    @GetMapping("/students")
    public Mono<List<Map<String, Object>>> students() {
        return userService.listStudents()
                .map(list -> list.stream().map(this::studentMap).toList());
    }

    /** 设定单个学生的监护人。 */
    @PutMapping("/students/{id}/guardian")
    public Mono<Map<String, Object>> setGuardian(@PathVariable Long id, @RequestBody Map<String, String> body) {
        return userService.updateGuardian(id,
                        body.get("guardianName"), body.get("guardianEmail"), body.get("guardianPhone"))
                .map(this::studentMap);
    }

    /** CSV 批量导入监护人。body: {"csv": "用户名,姓名,邮箱,电话\n..."} */
    @PostMapping("/students/import")
    public Mono<Map<String, Object>> importGuardians(@RequestBody Map<String, String> body) {
        return userService.importGuardiansCsv(body.get("csv"))
                .map(n -> Map.<String, Object>of("updated", n));
    }

    private Map<String, Object> studentMap(User u) {
        return Map.of(
                "id", u.getId(),
                "username", u.getUsername(),
                "nickname", u.displayName(),
                "guardianName", nz(u.getGuardianName()),
                "guardianEmail", nz(u.getGuardianEmail()),
                "guardianPhone", nz(u.getGuardianPhone()));
    }

    // ===================== 对话台账下载 =====================

    /** 列出对话台账文件名；username 非空则只列该生的。 */
    @GetMapping("/chat-logs")
    public Mono<List<String>> chatLogs(@RequestParam(required = false) String username) {
        return Mono.fromCallable(() -> excelLogService.listFiles(username))
                .subscribeOn(Schedulers.boundedElastic());
    }

    /** 下载指定台账 xlsx。 */
    @GetMapping("/chat-logs/download")
    public Mono<ResponseEntity<byte[]>> downloadLog(@RequestParam String file) {
        return Mono.fromCallable(() -> {
            Path p = excelLogService.safeResolve(file);
            if (!Files.exists(p)) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "台账文件不存在");
            }
            byte[] bytes = Files.readAllBytes(p);
            String encoded = URLEncoder.encode(file, StandardCharsets.UTF_8).replace("+", "%20");
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + encoded)
                    .contentType(MediaType.parseMediaType(
                            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                    .body(bytes);
        }).subscribeOn(Schedulers.boundedElastic());
    }

    // ===================== 知识库管理 (RAG) =====================

    /** 上传文档入库（仅 txt/md），切块向量化进 Chroma。 */
    @PostMapping(value = "/kb/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Mono<Map<String, Object>> uploadKb(@RequestPart("file") FilePart file,
                                              @RequestParam(value = "title", required = false) String title) {
        // WebFlux 的 DefaultPartHttpMessageReader 默认以 UTF-8 解析 Content-Disposition filename,
        // 直接使用即可。切勿再做 ISO-8859-1 转码——那会把正确的中文编码丢失成 '?'。
        String filename = file.filename();
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

    private static String nz(String s) {
        return s == null ? "" : s;
    }

    private static String stripExtension(String filename) {
        int dot = filename.lastIndexOf('.');
        return dot > 0 ? filename.substring(0, dot) : filename;
    }
}
