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
        int topK = props.getTopK();
        int candidateK = props.getCandidateK();
        EvalRun run = runRepo.save(new EvalRun(
                topK, candidateK, props.getMinScore(), props.getChunkSize(), note));

        // 口径分离：用 candidateK 大召回测「召回能力」(Recall@candidateK / MRR@candidateK)，
        // 再截断到 topK 测「最终命中」(Hit@topK / Precision@topK)。这样能区分
        // 「根本没召回到」与「召回到了但没排进前 topK」两类问题。
        double sumP = 0, sumR = 0, sumRR = 0;
        int hits = 0;   // Hit@topK 计数
        for (EvalQuery q : queries) {
            List<RetrievedChunk> chunks = knowledgeBaseService
                    .searchWithMeta(q.getQuery(), candidateK).block();
            // chunk 映射到文档，保序去重
            List<Long> retrieved = (chunks == null) ? List.of() : chunks.stream()
                    .map(RetrievedChunk::documentId)
                    .filter(Objects::nonNull)
                    .distinct()
                    .toList();

            int idx = retrieved.indexOf(q.getExpectedDocId());       // candidateK 内排名(0-based)
            boolean recalled = idx >= 0;                              // 是否召回到(Recall@candidateK)
            int rank = recalled ? idx + 1 : 0;
            double rr = recalled ? 1.0 / rank : 0.0;                  // MRR@candidateK

            // 截断到 topK 看最终命中
            List<Long> topDocs = retrieved.size() > topK ? retrieved.subList(0, topK) : retrieved;
            boolean hitTopK = topDocs.contains(q.getExpectedDocId());
            double precision = topDocs.isEmpty() ? 0.0 : (hitTopK ? 1.0 / topDocs.size() : 0.0);
            double recall = recalled ? 1.0 : 0.0;                     // 文档级期望集=1

            sumP += precision;
            sumR += recall;
            sumRR += rr;
            if (hitTopK) {
                hits++;
            }
            // 明细记录召回排名(rank)与是否召回(recalled)，便于下钻"召回到但没排进前topK"
            resultRepo.save(new EvalResultItem(run.getId(), q.getId(), q.getQuery(),
                    q.getExpectedDocId(), toJson(retrieved), recalled, rank, precision, recall, rr));
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
