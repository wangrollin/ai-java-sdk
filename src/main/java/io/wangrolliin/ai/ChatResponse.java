package io.wangrolliin.ai;

import java.util.Objects;

public record ChatResponse(String text) {
    public ChatResponse {
        text = Objects.requireNonNull(text, "text must not be null");
    }
}
