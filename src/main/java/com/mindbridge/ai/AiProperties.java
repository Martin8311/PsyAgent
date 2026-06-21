package com.mindbridge.ai;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * AI 相关配置，对应 application.yml 中的 mindbridge.ai.*
 */
@ConfigurationProperties(prefix = "mindbridge.ai")
public class AiProperties {

    /** 当前启用的提供方: ollama | openai */
    private String provider = "ollama";

    private Ollama ollama = new Ollama();

    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }

    public Ollama getOllama() {
        return ollama;
    }

    public void setOllama(Ollama ollama) {
        this.ollama = ollama;
    }

    public static class Ollama {
        private String baseUrl = "http://localhost:11434";
        private String model = "qwen2.5:3b";
        private int timeoutSeconds = 120;
        /** 上下文窗口大小(token)。Ollama 默认仅 2048，prompt 易被静默截断，显式调大。 */
        private int numCtx = 8192;

        public String getBaseUrl() {
            return baseUrl;
        }

        public int getNumCtx() {
            return numCtx;
        }

        public void setNumCtx(int numCtx) {
            this.numCtx = numCtx;
        }

        public void setBaseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
        }

        public String getModel() {
            return model;
        }

        public void setModel(String model) {
            this.model = model;
        }

        public int getTimeoutSeconds() {
            return timeoutSeconds;
        }

        public void setTimeoutSeconds(int timeoutSeconds) {
            this.timeoutSeconds = timeoutSeconds;
        }
    }
}
