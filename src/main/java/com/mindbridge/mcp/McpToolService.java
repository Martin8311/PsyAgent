package com.mindbridge.mcp;

import com.mindbridge.async.ChatLogMessage;
import com.mindbridge.async.ExcelLogService;
import com.mindbridge.async.MailService;
import com.mindbridge.risk.RiskAlert;
import com.mindbridge.risk.RiskAlertRepository;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;

/**
 * MCP 工具：把「写台账 / 发邮件 / 查预警」以标准 MCP 工具对外暴露，
 * 经 SSE 端点供外部 AI 客户端（如 Claude Desktop）调用。
 *
 * <p>这些工具与 RabbitMQ 消费者复用同一 Service 层（{@link ExcelLogService}/{@link MailService}），
 * 即「同一套能力，两个入口」：MQ 入口保证系统自动触发，MCP 入口供外部 AI 按需调用。
 */
@Service
public class McpToolService {

    private final ExcelLogService excelLogService;
    private final MailService mailService;
    private final RiskAlertRepository alertRepository;

    public McpToolService(ExcelLogService excelLogService,
                          MailService mailService,
                          RiskAlertRepository alertRepository) {
        this.excelLogService = excelLogService;
        this.mailService = mailService;
        this.alertRepository = alertRepository;
    }

    @Tool(description = "把一条对话记录追加写入 Excel 台账（按 用户+会话 分文件）")
    public String appendChatLog(
            @ToolParam(description = "学生用户名") String username,
            @ToolParam(description = "会话 ID") Long sessionId,
            @ToolParam(description = "学生提问") String question,
            @ToolParam(description = "AI 回复") String answer,
            @ToolParam(description = "风险等级 LOW/MEDIUM/HIGH") String riskLevel) {
        excelLogService.append(new ChatLogMessage(username, sessionId, question, answer, riskLevel, Instant.now()));
        return "已写入台账: " + excelLogService.fileNameOf(username, sessionId);
    }

    @Tool(description = "发送一封预警/通知邮件给指定收件人")
    public String sendAlertEmail(
            @ToolParam(description = "收件人邮箱") String to,
            @ToolParam(description = "邮件主题") String subject,
            @ToolParam(description = "邮件正文") String body) {
        mailService.send(to, subject, body);
        return "邮件已发送至 " + to;
    }

    @Tool(description = "查询某学生最近的高危预警记录（最多 5 条）")
    public String queryRecentAlerts(
            @ToolParam(description = "学生用户名") String username) {
        List<RiskAlert> list = alertRepository.findByUserIdOrderByCreatedAtDesc(username);
        if (list.isEmpty()) {
            return "该学生暂无预警记录";
        }
        return list.stream().limit(5)
                .map(a -> "#" + a.getId() + " [" + a.getStatus() + "] " + a.getLevel()
                        + " - " + a.getUserMessage())
                .collect(Collectors.joining("\n"));
    }
}
