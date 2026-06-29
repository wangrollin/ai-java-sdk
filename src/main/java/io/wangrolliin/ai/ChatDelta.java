package io.wangrolliin.ai;

public record ChatDelta(String text, String finishReason) {
    public ChatDelta {
        if (text == null) {
            text = "";
        }
    }
}
