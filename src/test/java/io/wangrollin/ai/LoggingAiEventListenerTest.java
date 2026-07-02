package io.wangrollin.ai;

import org.junit.jupiter.api.Test;

import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.ResourceBundle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LoggingAiEventListenerTest {
    private static final URI BASE_URI = URI.create("https://example.test/v1/");

    @Test
    void logsStartedAndSucceededEventsWithoutSensitivePayloads() {
        RecordingLogger logger = new RecordingLogger();
        LoggingAiEventListener listener = LoggingAiEventListener.create(logger);

        listener.requestStarted(new AiRequestEvent(
                "chat",
                "test-model",
                BASE_URI,
                "/chat/completions",
                false,
                1));
        listener.requestSucceeded(new AiResponseEvent(
                "chat",
                "provider-model",
                BASE_URI,
                "/chat/completions",
                false,
                1,
                200,
                Duration.ofMillis(42),
                "stop",
                new ChatUsage(3, 4, 7)));

        assertEquals(2, logger.entries.size());
        assertEquals(Level.DEBUG, logger.entries.get(0).level());
        assertTrue(logger.entries.get(0).message().contains("event=ai.request.started"));
        assertTrue(logger.entries.get(0).message().contains("model=test-model"));
        assertEquals(Level.INFO, logger.entries.get(1).level());
        assertTrue(logger.entries.get(1).message().contains("event=ai.request.succeeded"));
        assertTrue(logger.entries.get(1).message().contains("statusCode=200"));
        assertTrue(logger.entries.get(1).message().contains(
                "usage=promptTokens:3,completionTokens:4,totalTokens:7"));

        String combinedLogs = logger.entries.toString();
        assertFalse(combinedLogs.contains("secret prompt"));
        assertFalse(combinedLogs.contains("secret answer"));
        assertFalse(combinedLogs.contains("test-api-key"));
    }

    @Test
    void logsFailuresWithSanitizedDiagnosticMessage() {
        RecordingLogger logger = new RecordingLogger();
        LoggingAiEventListener listener = LoggingAiEventListener.create(logger);

        listener.requestFailed(new AiFailureEvent(
                "stream",
                "test-model",
                BASE_URI,
                "/chat/completions",
                true,
                2,
                503,
                Duration.ofMillis(125),
                AiException.class.getName(),
                "HTTP status 503"));

        assertEquals(1, logger.entries.size());
        LoggedEntry entry = logger.entries.get(0);
        assertEquals(Level.WARNING, entry.level());
        assertTrue(entry.message().contains("event=ai.request.failed"));
        assertTrue(entry.message().contains("operation=stream"));
        assertTrue(entry.message().contains("attempt=2"));
        assertTrue(entry.message().contains("statusCode=503"));
        assertTrue(entry.message().contains("durationMs=125"));
        assertTrue(entry.message().contains("message=\"HTTP status 503\""));
        assertFalse(entry.message().contains("raw upstream detail"));
    }

    @Test
    void canDisableStartedEventsAndCustomizeLevels() {
        RecordingLogger logger = new RecordingLogger();
        LoggingAiEventListener listener = LoggingAiEventListener.builder()
                .logger(logger)
                .logStartedEvents(false)
                .succeededLevel(Level.DEBUG)
                .failedLevel(Level.ERROR)
                .build();

        listener.requestStarted(new AiRequestEvent(
                "chat",
                "test-model",
                BASE_URI,
                "/chat/completions",
                false,
                1));
        listener.requestSucceeded(new AiResponseEvent(
                "chat",
                "test-model",
                BASE_URI,
                "/chat/completions",
                false,
                1,
                200,
                Duration.ZERO,
                null,
                null));
        listener.requestFailed(new AiFailureEvent(
                "chat",
                "test-model",
                BASE_URI,
                "/chat/completions",
                false,
                1,
                null,
                Duration.ZERO,
                "transport",
                "Failed to send chat request"));

        assertEquals(List.of(Level.DEBUG, Level.ERROR), logger.entries.stream()
                .map(LoggedEntry::level)
                .toList());
        assertTrue(logger.entries.get(0).message().contains("finishReason=null"));
        assertTrue(logger.entries.get(0).message().contains("usage=null"));
        assertTrue(logger.entries.get(1).message().contains("statusCode=null"));
    }

    @Test
    void respectsLoggerLevelFiltering() {
        RecordingLogger logger = new RecordingLogger(Level.WARNING);
        LoggingAiEventListener listener = LoggingAiEventListener.create(logger);

        listener.requestSucceeded(new AiResponseEvent(
                "chat",
                "test-model",
                BASE_URI,
                "/chat/completions",
                false,
                1,
                200,
                Duration.ZERO,
                null,
                null));
        listener.requestFailed(new AiFailureEvent(
                "chat",
                "test-model",
                BASE_URI,
                "/chat/completions",
                false,
                1,
                500,
                Duration.ZERO,
                "provider",
                "HTTP status 500"));

        assertEquals(1, logger.entries.size());
        assertEquals(Level.WARNING, logger.entries.get(0).level());
    }

    private record LoggedEntry(Level level, String message) {
    }

    private static final class RecordingLogger implements Logger {
        private final Level minimumLevel;
        private final List<LoggedEntry> entries = new ArrayList<>();

        private RecordingLogger() {
            this(Level.ALL);
        }

        private RecordingLogger(Level minimumLevel) {
            this.minimumLevel = minimumLevel;
        }

        @Override
        public String getName() {
            return "test";
        }

        @Override
        public boolean isLoggable(Level level) {
            return level.getSeverity() >= minimumLevel.getSeverity();
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
