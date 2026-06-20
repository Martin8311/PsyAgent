package com.mindbridge.ai.ollama;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.mindbridge.ai.ChatMessage;

import java.util.List;

/**
 * Ollama /api/chat 接口的请求/响应模型。
 */
public final class OllamaDtos {

    private OllamaDtos() {
    }

    /** 请求体。stream=true 时按 NDJSON 逐块返回。 */
    public record ChatRequest(String model, List<ChatMessage> messages, boolean stream) {
    }

    /** 流式响应的单个数据块。 */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ChatChunk(String model, Message message, boolean done) {

        @JsonIgnoreProperties(ignoreUnknown = true)
        public record Message(String role, String content) {
        }

        public String text() {
            return message == null ? "" : message.content();
        }
    }
}
