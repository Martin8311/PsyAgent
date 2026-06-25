package com.mindbridge.controller;

import com.mindbridge.eval.EvalGenService;
import com.mindbridge.eval.EvalQuery;
import com.mindbridge.eval.EvalQueryRepository;
import com.mindbridge.eval.EvalResultItemRepository;
import com.mindbridge.eval.EvalRun;
import com.mindbridge.eval.EvalRunRepository;
import com.mindbridge.eval.EvalService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * RAG 评测后台接口。隶属 /api/admin/**，由 Spring Security 限定 ADMIN。
 */
@RestController
@RequestMapping("/api/admin/eval")
public class EvalController {

    private final EvalGenService genService;
    private final EvalService evalService;
    private final EvalQueryRepository queryRepo;
    private final EvalRunRepository runRepo;
    private final EvalResultItemRepository resultRepo;

    public EvalController(EvalGenService genService, EvalService evalService,
                          EvalQueryRepository queryRepo, EvalRunRepository runRepo,
                          EvalResultItemRepository resultRepo) {
        this.genService = genService;
        this.evalService = evalService;
        this.queryRepo = queryRepo;
        this.runRepo = runRepo;
        this.resultRepo = resultRepo;
    }

    /** LLM 为每篇文档生成 perDoc 个评测问题。style: factual(事实型) / scenario(场景型口语化)。 */
    @PostMapping("/generate")
    public Mono<Map<String, Object>> generate(@RequestParam(defaultValue = "3") int perDoc,
                                              @RequestParam(defaultValue = "factual") String style) {
        return genService.generate(perDoc, style).map(n -> Map.of("generated", n));
    }

    /** 评测集列表。 */
    @GetMapping("/queries")
    public Mono<List<Map<String, Object>>> queries() {
        return Mono.fromCallable(() -> queryRepo.findAllByOrderByCreatedAtDesc().stream()
                        .map(this::queryMap).toList())
                .subscribeOn(Schedulers.boundedElastic());
    }

    /** 校正一条评测样例(改问题/期望文档/确认 reviewed)。 */
    @PutMapping("/queries/{id}")
    public Mono<Map<String, Object>> updateQuery(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        return Mono.fromCallable(() -> {
            EvalQuery q = queryRepo.findById(id)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "评测样例不存在"));
            if (body.get("query") != null) {
                q.setQuery(body.get("query").toString());
            }
            if (body.get("expectedDocId") != null) {
                q.setExpectedDocId(((Number) body.get("expectedDocId")).longValue());
            }
            if (body.get("expectedTitle") != null) {
                q.setExpectedTitle(body.get("expectedTitle").toString());
            }
            if (body.get("reviewed") != null) {
                q.setReviewed(Boolean.parseBoolean(body.get("reviewed").toString()));
            }
            return queryMap(queryRepo.save(q));
        }).subscribeOn(Schedulers.boundedElastic());
    }

    @DeleteMapping("/queries/{id}")
    public Mono<Map<String, Object>> deleteQuery(@PathVariable Long id) {
        return Mono.fromCallable(() -> {
            queryRepo.deleteById(id);
            return Map.<String, Object>of("deleted", id);
        }).subscribeOn(Schedulers.boundedElastic());
    }

    /** 清空所有未校正样例(重新生成前清场)。 */
    @DeleteMapping("/queries/unreviewed")
    public Mono<Map<String, Object>> clearUnreviewed() {
        return Mono.fromCallable(() -> Map.<String, Object>of("deleted", queryRepo.deleteByReviewedFalse()))
                .subscribeOn(Schedulers.boundedElastic());
    }

    /** 批量确认选中样例。body: {"ids":[1,2,3]} */
    @PostMapping("/queries/batch-confirm")
    public Mono<Map<String, Object>> batchConfirm(@RequestBody Map<String, Object> body) {
        List<Long> ids = toIds(body.get("ids"));
        return Mono.fromCallable(() -> Map.<String, Object>of(
                        "confirmed", ids.isEmpty() ? 0 : queryRepo.confirmByIds(ids)))
                .subscribeOn(Schedulers.boundedElastic());
    }

    /** 批量删除选中样例。body: {"ids":[1,2,3]} */
    @PostMapping("/queries/batch-delete")
    public Mono<Map<String, Object>> batchDelete(@RequestBody Map<String, Object> body) {
        List<Long> ids = toIds(body.get("ids"));
        return Mono.fromCallable(() -> Map.<String, Object>of(
                        "deleted", ids.isEmpty() ? 0 : queryRepo.deleteByIds(ids)))
                .subscribeOn(Schedulers.boundedElastic());
    }

    private static List<Long> toIds(Object raw) {
        if (!(raw instanceof List<?> list)) {
            return List.of();
        }
        List<Long> ids = new ArrayList<>();
        for (Object o : list) {
            if (o instanceof Number n) {
                ids.add(n.longValue());
            }
        }
        return ids;
    }

    /** 跑一次评测。 */
    @PostMapping("/run")
    public Mono<Map<String, Object>> run(@RequestBody(required = false) Map<String, Object> body) {
        String note = (body != null && body.get("note") != null) ? body.get("note").toString() : null;
        return evalService.run(note).map(this::runMap);
    }

    /** 历史评测运行(指标趋势)。 */
    @GetMapping("/runs")
    public Mono<List<Map<String, Object>>> runs() {
        return Mono.fromCallable(() -> runRepo.findAllByOrderByRunAtDesc().stream()
                        .map(this::runMap).toList())
                .subscribeOn(Schedulers.boundedElastic());
    }

    /** 单次评测明细。 */
    @GetMapping("/runs/{id}")
    public Mono<List<Map<String, Object>>> runDetail(@PathVariable Long id) {
        return Mono.fromCallable(() -> resultRepo.findByRunIdOrderByIdAsc(id).stream()
                        .map(it -> Map.<String, Object>of(
                                "query", it.getQuery(),
                                "expectedDocId", it.getExpectedDocId(),
                                "hit", it.isHit(),
                                "rank", it.getRank(),
                                "precision", round(it.getPrecision()),
                                "recall", round(it.getRecall()),
                                "reciprocalRank", round(it.getReciprocalRank()),
                                "retrievedDocIds", it.getRetrievedDocIds() == null ? "[]" : it.getRetrievedDocIds()))
                        .toList())
                .subscribeOn(Schedulers.boundedElastic());
    }

    private Map<String, Object> queryMap(EvalQuery q) {
        return Map.of(
                "id", q.getId(),
                "query", q.getQuery(),
                "expectedDocId", q.getExpectedDocId(),
                "expectedTitle", q.getExpectedTitle() == null ? "" : q.getExpectedTitle(),
                "source", q.getSource(),
                "reviewed", q.isReviewed());
    }

    private Map<String, Object> runMap(EvalRun r) {
        return Map.of(
                "id", r.getId(),
                "runAt", r.getRunAt().toString(),
                "topK", r.getTopK(),
                "candidateK", r.getCandidateK(),
                "minScore", r.getMinScore(),
                "queryCount", r.getQueryCount(),
                "avgPrecision", round(r.getAvgPrecision()),
                "avgRecall", round(r.getAvgRecall()),
                "mrr", round(r.getMrr()),
                "hitRate", round(r.getHitRate()));
    }

    private static double round(double v) {
        return Math.round(v * 1000.0) / 1000.0;
    }
}
