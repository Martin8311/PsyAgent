package com.mindbridge.risk;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/**
 * 高风险预警服务：落库台账 + 日志告警。
 *
 * <p>当前实现为「记录到 risk_alert 表 + WARN 日志」；
 * 邮件预警留待 MCP 工具阶段接入（spring-boot-starter-mail），
 * 这里预留 {@link #sendEmailAlert} 扩展点。
 */
@Service
public class AlertService {

    private static final Logger log = LoggerFactory.getLogger(AlertService.class);

    private final RiskAlertRepository alertRepository;

    public AlertService(RiskAlertRepository alertRepository) {
        this.alertRepository = alertRepository;
    }

    /**
     * 触发一次高风险预警（异步落库 + 告警，失败不影响主流程）。
     */
    public Mono<Void> raise(String userId, RiskAssessment risk, String userMessage) {
        return Mono.fromRunnable(() -> {
                    log.warn("⚠️ 高风险预警 | user={} | level={} | emotion={} | reason={} | msg={}",
                            userId, risk.level(), risk.emotion(), risk.reason(), userMessage);
                    RiskAlert alert = new RiskAlert(userId, risk.level(), risk.emotion(), userMessage, risk.reason());
                    alertRepository.save(alert);
                    sendEmailAlert(alert);
                })
                .subscribeOn(Schedulers.boundedElastic())
                .then();
    }

    /** 邮件预警扩展点（后续 MCP 阶段接入真实 SMTP）。 */
    private void sendEmailAlert(RiskAlert alert) {
        // TODO Phase MCP: 通过 MCP 工具 / JavaMailSender 给管理员发邮件预警
        log.debug("(邮件预警占位) 将通知管理员: alertId={}", alert.getId());
    }
}
