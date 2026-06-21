package com.mindbridge.ai.ollama;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.mindbridge.ai.ChatMessage;

import java.util.List;

/**
 * Ollama /api/chat 接口的请求/响应模型。
 */
public final class OllamaDtos {

    private OllamaDtos() {
    }

    /** 请求体。stream=true 时按 NDJSON 逐块返回；options 显式设上下文窗口等推理参数。 */
    public record ChatRequest(String model, List<ChatMessage> messages, boolean stream, Options options) {

        public record Options(@JsonProperty("num_ctx") int numCtx) {
        }
    }

    /**
     * 流式响应的单个数据块。最后一块(done=true)带本次调用的真实 token 计数：
     * prompt_eval_count=输入 token，eval_count=输出 token（中间块为 null）。
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ChatChunk(String model, Message message, boolean done,
                            @JsonProperty("prompt_eval_count") Integer promptEvalCount,
                            @JsonProperty("eval_count") Integer evalCount) {

        @JsonIgnoreProperties(ignoreUnknown = true)
        public record Message(String role, String content) {
        }

        public String text() {
            return message == null ? "" : message.content();
        }
    }
}
