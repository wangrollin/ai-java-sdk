package io.wangrollin.ai.examples.rag;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Application-level model and retrieval settings for the example workflow. */
@ConfigurationProperties("app.rag")
public class RagProperties {
    private String embeddingModel = "text-embedding-3-small";
    private String chatModel = "gpt-4.1-mini";
    private int topK = 2;

    public String getEmbeddingModel() {
        return embeddingModel;
    }

    public void setEmbeddingModel(String embeddingModel) {
        this.embeddingModel = requireText(embeddingModel, "app.rag.embedding-model");
    }

    public String getChatModel() {
        return chatModel;
    }

    public void setChatModel(String chatModel) {
        this.chatModel = requireText(chatModel, "app.rag.chat-model");
    }

    public int getTopK() {
        return topK;
    }

    public void setTopK(int topK) {
        if (topK < 1) {
            throw new IllegalArgumentException("app.rag.top-k must be positive");
        }
        this.topK = topK;
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }
}
