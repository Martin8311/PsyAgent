package com.mindbridge.ai;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * 大模型客户端抽象。
 *
 * <p>面向"接口编程"，上层 Agent 只依赖本接口；
 * 底层实现可在 Ollama(本地/微调) 与 OpenAI(云端) 之间切换，
 * 方便接模型切换、降级与压测。
 */
public interface AiClient {

    /** 实现标识，例如 "ollama" / "openai"。 */
    String provider();

    /**
     * 流式对话(带调用元信息)：逐 token 返回增量文本，用于 SSE 流式输出，
     * 同时按 meta 归因记录 token 用量。
     */
    Flux<String> streamChat(List<ChatMessage> messages, LlmCallMeta meta);

    /**
     * 阻塞式对话(带调用元信息)：返回完整文本，用于 Agent 内部需要完整结果的场景。
     */
    Mono<String> chat(List<ChatMessage> messages, LlmCallMeta meta);

    /** 流式对话(不归因，记为 UNKNOWN)。 */
    default Flux<String> streamChat(List<ChatMessage> messages) {
        return streamChat(messages, LlmCallMeta.UNKNOWN);
    }

    /** 阻塞式对话(不归因，记为 UNKNOWN)。 */
    default Mono<String> chat(List<ChatMessage> messages) {
        return chat(messages, LlmCallMeta.UNKNOWN);
    }
}
