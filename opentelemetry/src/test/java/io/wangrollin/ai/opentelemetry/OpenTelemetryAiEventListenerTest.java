package io.wangrollin.ai.opentelemetry;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;
import io.wangrollin.ai.event.AiFailureEvent;
import io.wangrollin.ai.event.AiRequestEvent;
import io.wangrollin.ai.event.AiResponseEvent;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpenTelemetryAiEventListenerTest {
    private static final URI BASE_URI = URI.create("https://example.test/v1/");
    private static final AttributeKey<String> AI_OPERATION = AttributeKey.stringKey("ai.operation");
    private static final AttributeKey<String> AI_MODEL = AttributeKey.stringKey("ai.model");
    private static final AttributeKey<Boolean> AI_STREAM = AttributeKey.booleanKey("ai.stream");
    private static final AttributeKey<Long> AI_ATTEMPT = AttributeKey.longKey("ai.attempt");
    private static final AttributeKey<String> AI_FINISH_REASON = AttributeKey.stringKey("ai.finish_reason");
    private static final AttributeKey<Long> HTTP_STATUS = AttributeKey.longKey("http.response.status_code");
    private static final AttributeKey<String> EXCEPTION_TYPE = AttributeKey.stringKey("exception.type");

    @Test
    void createsSuccessSpanWithSafeOperationalAttributes() {
        TestTelemetry telemetry = TestTelemetry.create();
        OpenTelemetryAiEventListener listener = OpenTelemetryAiEventListener.builder(telemetry.openTelemetry())
                .instrumentationName("custom.ai")
                .build();

        listener.requestStarted(new AiRequestEvent(
                "chat",
                "request-model",
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
                null));

        List<SpanData> spans = telemetry.finishedSpans();
        assertEquals(1, spans.size());
        SpanData span = spans.get(0);
        assertEquals("ai.sdk.chat", span.getName());
        assertEquals(StatusCode.OK, span.getStatus().getStatusCode());
        assertEquals("custom.ai", span.getInstrumentationScopeInfo().getName());
        assertEquals("chat", span.getAttributes().get(AI_OPERATION));
        assertEquals("provider-model", span.getAttributes().get(AI_MODEL));
        assertEquals(false, span.getAttributes().get(AI_STREAM));
        assertEquals(1L, span.getAttributes().get(AI_ATTEMPT));
        assertEquals(200L, span.getAttributes().get(HTTP_STATUS));
        assertEquals("stop", span.getAttributes().get(AI_FINISH_REASON));
        assertTrue(span.getEndEpochNanos() - span.getStartEpochNanos() >= Duration.ofMillis(42).toNanos());
    }

    @Test
    void createsFailureSpanWithoutSensitiveOrHighCardinalityAttributes() {
        TestTelemetry telemetry = TestTelemetry.create();
        OpenTelemetryAiEventListener listener = OpenTelemetryAiEventListener.create(telemetry.openTelemetry());

        listener.requestFailed(new AiFailureEvent(
                "response.stream",
                "request-model",
                BASE_URI,
                "/responses",
                true,
                2,
                503,
                Duration.ofMillis(13),
                "provider",
                "raw upstream detail"));

        SpanData span = telemetry.onlySpan();
        assertEquals("ai.sdk.response.stream", span.getName());
        assertEquals(StatusCode.ERROR, span.getStatus().getStatusCode());
        assertEquals("response.stream", span.getAttributes().get(AI_OPERATION));
        assertEquals("request-model", span.getAttributes().get(AI_MODEL));
        assertEquals(true, span.getAttributes().get(AI_STREAM));
        assertEquals(2L, span.getAttributes().get(AI_ATTEMPT));
        assertEquals(503L, span.getAttributes().get(HTTP_STATUS));
        assertEquals("provider", span.getAttributes().get(EXCEPTION_TYPE));

        Set<String> attributeKeys = span.getAttributes().asMap().keySet().stream()
                .map(AttributeKey::getKey)
                .collect(Collectors.toSet());
        Set<Object> attributeValues = Set.copyOf(span.getAttributes().asMap().values());
        assertFalse(attributeKeys.contains("baseUri"));
        assertFalse(attributeKeys.contains("path"));
        assertFalse(attributeKeys.contains("message"));
        assertFalse(attributeValues.contains(BASE_URI.toString()));
        assertFalse(attributeValues.contains("/responses"));
        assertFalse(attributeValues.contains("raw upstream detail"));
    }

    @Test
    void omitsMissingStatusCodeOnTransportFailure() {
        TestTelemetry telemetry = TestTelemetry.create();
        OpenTelemetryAiEventListener listener = OpenTelemetryAiEventListener.create(telemetry.openTelemetry());

        listener.requestFailed(new AiFailureEvent(
                "chat",
                "test-model",
                BASE_URI,
                "/chat/completions",
                false,
                1,
                null,
                Duration.ofMillis(5),
                "transport",
                "connect timeout"));

        SpanData span = telemetry.onlySpan();
        assertFalse(span.getAttributes().asMap().containsKey(HTTP_STATUS));
        assertEquals("transport", span.getAttributes().get(EXCEPTION_TYPE));
    }

    @Test
    void validatesBuilderInputs() {
        assertThrows(NullPointerException.class, () -> OpenTelemetryAiEventListener.create(null));
        assertThrows(NullPointerException.class, () -> OpenTelemetryAiEventListener.builder((OpenTelemetry) null));
        assertThrows(NullPointerException.class, () -> OpenTelemetryAiEventListener.builder((io.opentelemetry.api.trace.Tracer) null));
        assertThrows(NullPointerException.class, () -> OpenTelemetryAiEventListener.builder(OpenTelemetry.noop())
                .instrumentationName(null));
        assertThrows(IllegalArgumentException.class, () -> OpenTelemetryAiEventListener.builder(OpenTelemetry.noop())
                .instrumentationName(" "));
    }

    private record TestTelemetry(OpenTelemetrySdk openTelemetry, InMemorySpanExporter exporter) {
        static TestTelemetry create() {
            InMemorySpanExporter exporter = InMemorySpanExporter.create();
            SdkTracerProvider tracerProvider = SdkTracerProvider.builder()
                    .addSpanProcessor(SimpleSpanProcessor.create(exporter))
                    .build();
            OpenTelemetrySdk openTelemetry = OpenTelemetrySdk.builder()
                    .setTracerProvider(tracerProvider)
                    .build();
            return new TestTelemetry(openTelemetry, exporter);
        }

        List<SpanData> finishedSpans() {
            return exporter.getFinishedSpanItems();
        }

        SpanData onlySpan() {
            List<SpanData> spans = finishedSpans();
            assertEquals(1, spans.size());
            return spans.get(0);
        }
    }
}
