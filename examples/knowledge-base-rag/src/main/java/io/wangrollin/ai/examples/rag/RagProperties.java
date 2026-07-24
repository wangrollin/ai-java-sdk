package io.wangrollin.ai.examples.rag;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Application-level retrieval settings for the example workflow. */
@ConfigurationProperties("app.rag")
public class RagProperties {
    private int topK = 2;

    public int getTopK() {
        return topK;
    }

    public void setTopK(int topK) {
        if (topK < 1) {
            throw new IllegalArgumentException("app.rag.top-k must be positive");
        }
        this.topK = topK;
    }

}
