package io.wangrollin.ai.diagnostic;

import org.junit.jupiter.api.Test;

import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.util.ArrayList;
import java.util.List;
import java.util.ResourceBundle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LoggingAiPayloadDiagnosticsListenerTest {
    @Test
    void logsRedactedPayloadDiagnostics() {
        RecordingLogger logger = new RecordingLogger();
        LoggingAiPayloadDiagnosticsListener listener = LoggingAiPayloadDiagnosticsListener.builder()
                .logger(logger)
                .level(Level.INFO)
                .build();

        listener.requestPayload(new AiPayloadRequestEvent(
                "chat",
                "test-model",
                java.net.URI.create("https://example.test/v1/"),
                "/chat/completions",
                false,
                "{\"messages\":[{\"content\":\"<redacted>\"}]}"));

        assertEquals(1, logger.entries.size());
        LoggedEntry entry = logger.entries.get(0);
        assertEquals(Level.INFO, entry.level());
        assertTrue(entry.message().contains("event=ai.payload.request"));
        assertTrue(entry.message().contains("operation=chat"));
        assertTrue(entry.message().contains("body=\"{\\\"messages\\\""));
    }

    private record LoggedEntry(Level level, String message) {
    }

    private static final class RecordingLogger implements Logger {
        private final List<LoggedEntry> entries = new ArrayList<>();

        @Override
        public String getName() {
            return "test";
        }

        @Override
        public boolean isLoggable(Level level) {
            return true;
        }

        @Override
        public void log(Level level, ResourceBundle bundle, String msg, Throwable thrown) {
            entries.add(new LoggedEntry(level, msg));
        }

        @Override
        public void log(Level level, ResourceBundle bundle, String format, Object... params) {
            entries.add(new LoggedEntry(level, format));
        }
    }
}
