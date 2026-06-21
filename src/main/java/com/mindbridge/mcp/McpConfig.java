package com.mindbridge.mcp;

import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 注册 MCP 工具：把 {@link McpToolService} 上 @Tool 标注的方法
 * 暴露为 MCP server 的工具列表，经 SSE 端点对外提供。
 */
@Configuration
public class McpConfig {

    @Bean
    public ToolCallbackProvider mindbridgeMcpTools(McpToolService mcpToolService) {
        return MethodToolCallbackProvider.builder()
                .toolObjects(mcpToolService)
                .build();
    }
}
