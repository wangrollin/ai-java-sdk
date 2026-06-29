# ai-java-sdk

[![CI](https://github.com/wangrollin/ai-java-sdk/actions/workflows/ci.yml/badge.svg)](https://github.com/wangrollin/ai-java-sdk/actions/workflows/ci.yml)

`ai-java-sdk` is a production-oriented Java SDK for building AI features in backend systems.

The goal of this project is not to be another thin wrapper around a model provider's HTTP API. It is intended to provide a Java-first foundation for teams that need reliable, observable, and maintainable AI integrations in real applications.

## Why This Project Exists

AI APIs are easy to call once, but harder to operate well in a Java backend. Production systems need more than request and response objects. They need predictable timeouts, retries, streaming behavior, typed models, safe logging, metrics, testing support, and clean integration with existing application frameworks.

`ai-java-sdk` aims to bring those concerns into one coherent SDK so Java teams can focus on product behavior instead of rebuilding the same infrastructure around every provider.

## Core Value

- **Java-first API design**: provide clear, typed Java interfaces instead of raw maps and provider-specific JSON plumbing.
- **Production readiness**: treat timeouts, retries, errors, cancellation, and resource management as first-class concerns.
- **Stable streaming support**: make token streaming and event-style responses practical for backend services.
- **Provider flexibility**: support OpenAI-compatible APIs first, with room for additional model providers over time.
- **Operational visibility**: make logging, tracing, metrics, and request diagnostics easier to add safely.
- **Framework integration**: provide a path toward Spring Boot-friendly configuration and dependency injection.

## Initial Design Goals

The initial version is designed around a small, reliable foundation:

- A minimal chat API for synchronous responses.
- A streaming API for incremental model output.
- Typed request and response objects.
- Consistent error handling across provider responses.
- Configurable base URL, API key, model, timeout, and retry behavior.
- Test utilities for mocking model responses in application tests.

## Planned Capabilities

As the project evolves, it is expected to grow toward:

- OpenAI-compatible chat and response APIs.
- Tool calling and structured JSON output.
- Multi-provider adapters for common model platforms.
- Spring Boot starter support.
- Request/response logging with redaction.
- Metrics and tracing hooks.
- Examples for common backend AI workflows.

## Current Status

This project is at an early stage. The README describes the intended direction and value of the SDK; implementation details and public APIs will evolve as the core modules are built.

## Requirements

- JDK 25
- Maven 3.9+

## Minimal Usage

The first implementation milestone supports synchronous and streaming OpenAI-compatible chat completions.

```java
import io.wangrollin.ai.AiClient;
import io.wangrollin.ai.AiChatClient;
import io.wangrollin.ai.ChatDelta;
import io.wangrollin.ai.ChatMessage;
import io.wangrollin.ai.ChatRequest;
import io.wangrollin.ai.ChatResponse;
import io.wangrollin.ai.ChatStream;
import io.wangrollin.ai.RetryPolicy;

import java.time.Duration;

AiChatClient client = AiClient.builder()
    .apiKey(System.getenv("OPENAI_API_KEY"))
    .baseUrl("https://api.openai.com/v1")
    .defaultModel("gpt-4.1-mini")
    .timeout(Duration.ofSeconds(30))
    .retryPolicy(RetryPolicy.defaultPolicy())
    .build();

ChatResponse response = client.chat(ChatRequest.builder()
    .message(ChatMessage.user("Hello"))
    .build());

System.out.println(response.text());
```

Request-level generation options can be set when a call needs to override the provider defaults:

```java
ChatResponse response = client.chat(ChatRequest.builder()
    .message(ChatMessage.system("Answer with concise engineering guidance."))
    .message(ChatMessage.user("How should I handle retries?"))
    .temperature(0.2)
    .topP(0.9)
    .maxTokens(300)
    .stopSequence("END")
    .build());
```

`ChatResponse` exposes the generated `text()` plus optional provider metadata such as `id()`, `model()`, and `finishReason()`. These fields are useful for diagnostics and request correlation; avoid logging API keys, prompts, or model outputs unless your application has explicit redaction and retention controls.

For incremental output, consume a `ChatStream` with try-with-resources:

```java
try (ChatStream stream = client.stream(ChatRequest.builder()
    .message(ChatMessage.user("Tell me a short story"))
    .build())) {
    for (ChatDelta delta : stream) {
        System.out.print(delta.text());
    }
}
```

Retries are disabled by default. Use `RetryPolicy.defaultPolicy()` to retry transient `429`, `500`, `502`, `503`, and `504` responses up to three total attempts, or build a custom policy:

```java
AiClient client = AiClient.builder()
    .apiKey(System.getenv("OPENAI_API_KEY"))
    .defaultModel("gpt-4.1-mini")
    .retryPolicy(RetryPolicy.builder()
        .maxAttempts(3)
        .initialDelay(Duration.ofMillis(200))
        .maxDelay(Duration.ofSeconds(2))
        .build())
    .build();
```

Streaming requests only retry failures that happen before a successful response stream is returned. Once stream consumption begins, malformed events or read failures are surfaced to the caller without replaying the request.

## Testing Support

Application code can depend on the `AiChatClient` interface and use `FakeAiClient` in unit tests. The fake is fully in-memory: it does not require an API key, never opens a network connection, and records requests so tests can assert the prompt and generation options sent by the application.

```java
import io.wangrollin.ai.AiChatClient;
import io.wangrollin.ai.ChatDelta;
import io.wangrollin.ai.ChatMessage;
import io.wangrollin.ai.ChatRequest;
import io.wangrollin.ai.FakeAiClient;

AiChatClient client = FakeAiClient.builder()
    .chatResponse("Test response")
    .streamDeltas(
        new ChatDelta("Hel", null),
        new ChatDelta("lo", "stop"))
    .build();

String text = client.chat(ChatRequest.builder()
    .message(ChatMessage.user("Hello"))
    .build()).text();
```

`FakeAiClient` can also be configured with failures through `chatFailure(...)`, `streamFailure(...)`, and `streamMalformedEvent(...)`, which is useful for testing retry wrappers, fallback behavior, and stream-consumption error handling in application code without calling a real provider.

Run the test suite with:

```shell
mvn test
```

The HTTP client tests start a local in-process HTTP server on an ephemeral port. In locked-down
sandboxes, the test command may need permission to bind a local port even though it does not call
an external AI provider.

## License

This project is licensed under the Apache License 2.0.
