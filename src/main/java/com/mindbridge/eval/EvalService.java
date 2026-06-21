package com.mindbridge.eval;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mindbridge.knowledge.KnowledgeBaseService;
import com.mindbridge.knowledge.KnowledgeProperties;
import com.mindbridge.knowledge.RetrievedChunk;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.List;
import java.util.Objects;

/**
 * RAG 检索评测引擎。对已校正的评测集逐条跑检索，按「文档级」判命中，
 * 计算 Precision@K / Recall@K / MRR / Hit@K，落库 EvalRun + 明细。
 */
@Service
public class EvalService {

    private static final Logger log = LoggerFactory.getLogger(EvalService.class);

    private final KnowledgeBaseService knowledgeBaseService;
    private final KnowledgeProperties props;
    private final EvalQueryRepository queryRepo;
    private final EvalRunRepository runRepo;
    private final EvalResultItemRepository resultRepo;
    private final ObjectMapper objectMapper;

    public EvalService(KnowledgeBaseService knowledgeBaseService, KnowledgeProperties props,
                       EvalQueryRepository queryRepo, EvalRunRepository runRepo,
                       EvalResultItemRepository resultRepo, ObjectMapper objectMapper) {
        this.knowledgeBaseService = knowledgeBaseService;
        this.props = props;
        this.queryRepo = queryRepo;
        this.runRepo = runRepo;
        this.resultRepo = resultRepo;
        this.objectMapper = objectMapper;
    }

    /** 跑一次评测，返回汇总指标。 */
    public Mono<EvalRun> run(String note) {
        return Mono.fromCallable(() -> doRun(note)).subscribeOn(Schedulers.boundedElastic());
    }

    private EvalRun doRun(String note) {
        List<EvalQuery> queries = queryRepo.findByReviewedTrueOrderByCreatedAtDesc();
        if (queries.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "评测集为空(无已校正样例)，请先生成评测集并在后台校正确认");
        }
        EvalRun run = runRepo.save(new EvalRun(
                props.getTopK(), props.getMinScore(), props.getChunkSize(), note));

        double sumP = 0, sumR = 0, sumRR = 0;
        int hits = 0;
        for (EvalQuery q : queries) {
            List<RetrievedChunk> chunks = knowledgeBaseService
                    .searchWithMeta(q.getQuery(), props.getTopK()).block();
            // chunk 映射到文档，保序去重
            List<Long> retrieved = (chunks == null) ? List.of() : chunks.stream()
                    .map(RetrievedChunk::documentId)
                    .filter(Objects::nonNull)
                    .distinct()
                    .toList();

            int idx = retrieved.indexOf(q.getExpectedDocId());
            boolean hit = idx >= 0;
            int rank = hit ? idx + 1 : 0;
            double rr = hit ? 1.0 / rank : 0.0;
            // 文档级：期望集大小=1
            double precision = retrieved.isEmpty() ? 0.0 : (hit ? 1.0 / retrieved.size() : 0.0);
            double recall = hit ? 1.0 : 0.0;

            sumP += precision;
            sumR += recall;
            sumRR += rr;
            if (hit) {
                hits++;
            }
            resultRepo.save(new EvalResultItem(run.getId(), q.getId(), q.getQuery(),
                    q.getExpectedDocId(), toJson(retrieved), hit, rank, precision, recall, rr));
        }

        int n = queries.size();
        run.setQueryCount(n);
        run.setAvgPrecision(sumP / n);
        run.setAvgRecall(sumR / n);
        run.setMrr(sumRR / n);
        run.setHitRate((double) hits / n);
        EvalRun saved = runRepo.save(run);
        log.info("RAG 评测完成: run={}, N={}, P={}, R={}, MRR={}, Hit={}",
                saved.getId(), n, saved.getAvgPrecision(), saved.getAvgRecall(), saved.getMrr(), saved.getHitRate());
        return saved;
    }

    private String toJson(List<Long> ids) {
        try {
            return objectMapper.writeValueAsString(ids);
        } catch (Exception e) {
            return "[]";
        }
    }
}
