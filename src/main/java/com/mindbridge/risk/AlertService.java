package com.mindbridge.risk;

import com.mindbridge.async.AsyncPublisher;
import com.mindbridge.async.GuardianNotifyMessage;
import com.mindbridge.async.RiskAlertMessage;
import com.mindbridge.user.User;
import com.mindbridge.user.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.Instant;

/**
 * 高风险预警服务：第一跳落库 + 发管理员邮件；第二跳由管理员核实后通知监护人。
 *
 * <p>两段式闸门：命中高危即落库(状态=待核实)并发邮件给管理员/心理老师，
 * 绝不直接触达监护人；监护人通知必须经管理员在后台人工确认。
 */
@Service
public class AlertService {

    private static final Logger log = LoggerFactory.getLogger(AlertService.class);

    private final RiskAlertRepository alertRepository;
    private final UserRepository userRepository;
    private final AsyncPublisher asyncPublisher;

    public AlertService(RiskAlertRepository alertRepository,
                        UserRepository userRepository,
                        AsyncPublisher asyncPublisher) {
        this.alertRepository = alertRepository;
        this.userRepository = userRepository;
        this.asyncPublisher = asyncPublisher;
    }

    /**
     * 第一跳：触发高风险预警（异步落库 + 发管理员邮件消息，失败不影响主流程）。
     */
    public Mono<Void> raise(String userId, RiskAssessment risk, String userMessage) {
        return Mono.fromRunnable(() -> {
                    log.warn("⚠️ 高风险预警 | user={} | level={} | emotion={} | reason={} | msg={}",
                            userId, risk.level(), risk.emotion(), risk.reason(), userMessage);
                    RiskAlert alert = new RiskAlert(userId, risk.level(), risk.emotion(), userMessage, risk.reason());
                    RiskAlert saved = alertRepository.save(alert);
                    asyncPublisher.publishRiskAlert(new RiskAlertMessage(
                            saved.getId(), userId,
                            risk.level().name(), risk.emotion().name(),
                            userMessage, risk.reason()));
                })
                .subscribeOn(Schedulers.boundedElastic())
                .then();
    }

    /**
     * 第二跳：管理员核实后通知监护人。校验该生已配置监护人邮箱，
     * 发 guardian.notify 消息并更新预警状态为 GUARDIAN_NOTIFIED。
     */
    public Mono<RiskAlert> notifyGuardian(Long alertId, String adminName) {
        return Mono.fromCallable(() -> {
            RiskAlert alert = alertRepository.findById(alertId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "预警不存在"));
            User student = userRepository.findByUsername(alert.getUserId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "学生账号不存在"));
            if (student.getGuardianEmail() == null || student.getGuardianEmail().isBlank()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "该生未配置监护人邮箱，请先在学生管理中设定");
            }
            asyncPublisher.publishGuardianNotify(new GuardianNotifyMessage(
                    alertId, student.displayName(), student.getGuardianName(), student.getGuardianEmail(),
                    alert.getLevel().name(), alert.getUserMessage(), adminName));
            alert.setStatus(AlertStatus.GUARDIAN_NOTIFIED);
            alert.setNotifiedAt(Instant.now());
            alert.setHandledBy(adminName);
            return alertRepository.save(alert);
        }).subscribeOn(Schedulers.boundedElastic());
    }

    /** 标记预警为误报（已忽略），不通知监护人。 */
    public Mono<RiskAlert> dismiss(Long alertId, String adminName) {
        return Mono.fromCallable(() -> {
            RiskAlert alert = alertRepository.findById(alertId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "预警不存在"));
            alert.setStatus(AlertStatus.DISMISSED);
            alert.setHandledBy(adminName);
            return alertRepository.save(alert);
        }).subscribeOn(Schedulers.boundedElastic());
    }
}
