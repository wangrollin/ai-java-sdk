package io.wangrolliin.ai;

import java.util.Objects;

public record ChatResponse(String text, String id, String model, String finishReason) {
    public ChatResponse(String text) {
        this(text, null, null, null);
    }

    public ChatResponse {
        text = Objects.requireNonNull(text, "text must not be null");
    }
}
