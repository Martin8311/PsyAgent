package com.mindbridge.async;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Stream;

/**
 * 对话台账写入服务（Apache POI / xlsx）。
 *
 * <p>按「用户 + 会话」分文件：{@code {username}_session{sessionId}.xlsx}，
 * 一个会话一份完整对话记录。每行一轮：序号 / 时间 / 学生提问 / AI回复 / 风险等级。
 *
 * <p>并发：消费者 {@code concurrency=1} 串行消费，叠加 per-file 锁双保险，
 * 杜绝 POI 多线程写坏同一 xlsx。
 */
@Service
public class ExcelLogService {

    private static final Logger log = LoggerFactory.getLogger(ExcelLogService.class);
    private static final String[] HEADERS = {"序号", "时间", "学生提问", "AI回复", "风险等级"};
    private static final DateTimeFormatter TS =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault());

    private final Path baseDir;
    private final ConcurrentHashMap<String, ReentrantLock> locks = new ConcurrentHashMap<>();

    public ExcelLogService(@Value("${mindbridge.excel.dir:./data/chat-logs}") String dir) throws IOException {
        this.baseDir = Paths.get(dir);
        Files.createDirectories(baseDir);
        log.info("对话台账目录: {}", baseDir.toAbsolutePath());
    }

    /** 台账文件名（已做文件名安全处理）。 */
    public String fileNameOf(String username, Long sessionId) {
        String safeUser = (username == null ? "unknown" : username).replaceAll("[^a-zA-Z0-9_\\-]", "_");
        return safeUser + "_session" + sessionId + ".xlsx";
    }

    public Path resolve(String username, Long sessionId) {
        return baseDir.resolve(fileNameOf(username, sessionId));
    }

    /** 追加一行对话记录到对应会话的台账文件。 */
    public void append(ChatLogMessage msg) {
        String fileName = fileNameOf(msg.username(), msg.sessionId());
        Path file = baseDir.resolve(fileName);
        ReentrantLock lock = locks.computeIfAbsent(fileName, k -> new ReentrantLock());
        lock.lock();
        try {
            Workbook wb;
            Sheet sheet;
            if (Files.exists(file)) {
                try (InputStream in = Files.newInputStream(file)) {
                    wb = new XSSFWorkbook(in);
                }
                sheet = wb.getSheetAt(0);
            } else {
                wb = new XSSFWorkbook();
                sheet = wb.createSheet("对话记录");
                Row header = sheet.createRow(0);
                for (int i = 0; i < HEADERS.length; i++) {
                    header.createCell(i).setCellValue(HEADERS[i]);
                }
            }
            int rowNum = sheet.getLastRowNum() + 1;
            Row row = sheet.createRow(rowNum);
            row.createCell(0).setCellValue(rowNum);
            row.createCell(1).setCellValue(TS.format(msg.timestamp() == null ? Instant.now() : msg.timestamp()));
            row.createCell(2).setCellValue(nz(msg.question()));
            row.createCell(3).setCellValue(nz(msg.answer()));
            row.createCell(4).setCellValue(nz(msg.riskLevel()));
            try (OutputStream out = Files.newOutputStream(file)) {
                wb.write(out);
            }
            wb.close();
            log.debug("台账写入 {} 第{}行", fileName, rowNum);
        } catch (IOException e) {
            throw new RuntimeException("写对话台账失败: " + file, e);
        } finally {
            lock.unlock();
        }
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }

    /** 列出台账文件名；username 非空则只列该生的（前缀匹配）。 */
    public List<String> listFiles(String username) {
        String prefix = (username == null || username.isBlank())
                ? null
                : username.replaceAll("[^a-zA-Z0-9_\\-]", "_") + "_";
        try (Stream<Path> s = Files.list(baseDir)) {
            return s.filter(p -> p.getFileName().toString().endsWith(".xlsx"))
                    .map(p -> p.getFileName().toString())
                    .filter(n -> prefix == null || n.startsWith(prefix))
                    .sorted()
                    .toList();
        } catch (IOException e) {
            return List.of();
        }
    }

    /** 按文件名解析路径，并防目录穿越。 */
    public Path safeResolve(String fileName) {
        Path p = baseDir.resolve(fileName).normalize();
        if (!p.startsWith(baseDir.normalize())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "非法文件名");
        }
        return p;
    }
}
