package io.wangrollin.ai.micrometer;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.wangrollin.ai.chat.ChatUsage;
import io.wangrollin.ai.event.AiFailureEvent;
import io.wangrollin.ai.event.AiRequestEvent;
import io.wangrollin.ai.event.AiResponseEvent;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.time.Duration;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MicrometerAiEventListenerTest {
    private static final URI BASE_URI = URI.create("https://example.test/v1/");

    @Test
    void recordsLifecycleCountersDurationsAndTokenUsage() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        MicrometerAiEventListener listener = MicrometerAiEventListener.create(registry);

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
                new ChatUsage(3, 4, 7)));
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
                "HTTP status 503"));

        assertEquals(1.0, registry.get("ai.sdk.requests.started")
                .tag("operation", "chat")
                .tag("model", "request-model")
                .tag("stream", "false")
                .counter()
                .count());
        assertEquals(1.0, registry.get("ai.sdk.requests.succeeded")
                .tag("operation", "chat")
                .tag("model", "provider-model")
                .tag("status", "200")
                .counter()
                .count());
        assertEquals(1.0, registry.get("ai.sdk.requests.failed")
                .tag("operation", "response.stream")
                .tag("status", "503")
                .tag("exception.type", "provider")
                .counter()
                .count());
        assertEquals(2, timerCount(registry, "ai.sdk.requests.duration"));
        assertEquals(3.0, counter(registry, "ai.sdk.tokens.prompt").count());
        assertEquals(4.0, counter(registry, "ai.sdk.tokens.completion").count());
        assertEquals(7.0, counter(registry, "ai.sdk.tokens.total").count());
    }

    @Test
    void keepsUnsafeAndHighCardinalityFieldsOutOfMetricTags() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        MicrometerAiEventListener listener = MicrometerAiEventListener.create(registry);

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
                "raw upstream detail"));

        Set<String> tagKeys = registry.getMeters().stream()
                .flatMap(meter -> meter.getId().getTags().stream())
                .map(tag -> tag.getKey())
                .collect(Collectors.toSet());
        Set<String> tagValues = registry.getMeters().stream()
                .flatMap(meter -> meter.getId().getTags().stream())
                .map(tag -> tag.getValue())
                .collect(Collectors.toSet());

        assertFalse(tagKeys.contains("baseUri"));
        assertFalse(tagKeys.contains("path"));
        assertFalse(tagKeys.contains("message"));
        assertFalse(tagValues.contains(BASE_URI.toString()));
        assertFalse(tagValues.contains("/chat/completions"));
        assertFalse(tagValues.contains("raw upstream detail"));
        assertEquals(1.0, registry.get("ai.sdk.requests.failed")
                .tag("status", "none")
                .tag("exception.type", "transport")
                .counter()
                .count());
    }

    @Test
    void supportsCustomMetricPrefix() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        MicrometerAiEventListener listener = MicrometerAiEventListener.builder(registry)
                .metricPrefix("custom.ai.")
                .build();

        listener.requestStarted(new AiRequestEvent(
                "chat",
                "test-model",
                BASE_URI,
                "/chat/completions",
                false,
                1));

        assertEquals(1.0, registry.get("custom.ai.requests.started").counter().count());
    }

    @Test
    void validatesBuilderInputs() {
        assertThrows(NullPointerException.class, () -> MicrometerAiEventListener.create(null));
        assertThrows(NullPointerException.class, () -> MicrometerAiEventListener.builder(new SimpleMeterRegistry())
                .metricPrefix(null));
        assertThrows(IllegalArgumentException.class, () -> MicrometerAiEventListener.builder(new SimpleMeterRegistry())
                .metricPrefix(" "));
        assertThrows(IllegalArgumentException.class, () -> MicrometerAiEventListener.builder(new SimpleMeterRegistry())
                .metricPrefix("."));
    }

    private static Counter counter(SimpleMeterRegistry registry, String name) {
        return registry.find(name)
                .meters()
                .stream()
                .filter(meter -> meter.getId().getType() == Meter.Type.COUNTER)
                .map(meter -> registry.get(meter.getId().getName()).tags(meter.getId().getTags()).counter())
                .findFirst()
                .orElseThrow();
    }

    private static long timerCount(SimpleMeterRegistry registry, String name) {
        return registry.find(name)
                .meters()
                .stream()
                .filter(meter -> meter.getId().getType() == Meter.Type.TIMER)
                .mapToLong(meter -> registry.get(meter.getId().getName()).tags(meter.getId().getTags()).timer().count())
                .sum();
    }
}
