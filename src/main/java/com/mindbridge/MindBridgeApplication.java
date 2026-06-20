package com.mindbridge;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * MindBridge 启动类。
 *
 * <p>面向校园心理健康场景的 AI 智能体助手：
 * 学生匿名倾诉 + 多模态情绪识别 + 智能咨询 + 高风险自动预警。
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class MindBridgeApplication {

    public static void main(String[] args) {
        SpringApplication.run(MindBridgeApplication.class, args);
    }
}
