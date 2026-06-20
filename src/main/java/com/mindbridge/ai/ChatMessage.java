package com.mindbridge.ai;

/**
 * 统一的对话消息模型，屏蔽不同提供方(Ollama/OpenAI)的格式差异。
 *
 * @param role    角色: system | user | assistant
 * @param content 文本内容
 */
public record ChatMessage(String role, String content) {

    public static ChatMessage system(String content) {
        return new ChatMessage("system", content);
    }

    public static ChatMessage user(String content) {
        return new ChatMessage("user", content);
    }

    public static ChatMessage assistant(String content) {
        return new ChatMessage("assistant", content);
    }
}
