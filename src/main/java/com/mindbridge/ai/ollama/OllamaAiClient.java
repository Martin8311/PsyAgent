package com.mindbridge.ai.ollama;

import com.mindbridge.ai.AiClient;
import com.mindbridge.ai.AiProperties;
import com.mindbridge.ai.ChatMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.List;

/**
 * 基于 Ollama 本地推理服务的 {@link AiClient} 实现。
 *
 * <p>直接以 WebClient 调用 Ollama 的 /api/chat 流式接口(NDJSON)，
 * 逐块解析出增量文本，向上提供 {@link Flux} 流，天然适配 SSE。
 */
@Component
public class OllamaAiClient implements AiClient {

    private static final Logger log = LoggerFactory.getLogger(OllamaAiClient.class);

    private final WebClient webClient;
    private final AiProperties.Ollama config;

    public OllamaAiClient(AiProperties properties) {
        this.config = properties.getOllama();
        this.webClient = WebClient.builder()
                .baseUrl(config.getBaseUrl())
                .build();
        log.info("OllamaAiClient initialized: baseUrl={}, model={}", config.getBaseUrl(), config.getModel());
    }

    @Override
    public String provider() {
        return "ollama";
    }

    @Override
    public Flux<String> streamChat(List<ChatMessage> messages) {
        OllamaDtos.ChatRequest body = new OllamaDtos.ChatRequest(config.getModel(), messages, true);
        return webClient.post()
                .uri("/api/chat")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .retrieve()
                .bodyToFlux(OllamaDtos.ChatChunk.class)
                .timeout(Duration.ofSeconds(config.getTimeoutSeconds()))
                .takeUntil(OllamaDtos.ChatChunk::done)
                .map(OllamaDtos.ChatChunk::text)
                .filter(s -> !s.isEmpty())
                .doOnError(e -> log.error("Ollama streamChat error: {}", e.getMessage()));
    }

    @Override
    public Mono<String> chat(List<ChatMessage> messages) {
        return streamChat(messages).collect(StringBuilder::new, StringBuilder::append)
                .map(StringBuilder::toString);
    }
}
