# ai-java-sdk

[![CI](https://github.com/wangrollin/ai-java-sdk/actions/workflows/ci.yml/badge.svg)](https://github.com/wangrollin/ai-java-sdk/actions/workflows/ci.yml)
[![License](https://img.shields.io/badge/license-Apache--2.0-blue.svg)](LICENSE)
[![Java](https://img.shields.io/badge/java-25%2B-orange.svg)](pom.xml)

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

Validate the project locally with:

```shell
mvn verify
```

`mvn verify` also compiles the example sources under `src/examples/java` so public usage snippets
stay aligned with the current API without being packaged into the runtime jar.

## Minimal Usage

The first implementation milestone supports synchronous and streaming OpenAI-compatible chat completions.

```java
import io.wangrollin.ai.client.AiClient;
import io.wangrollin.ai.client.AiChatClient;
import io.wangrollin.ai.event.AiEventListener;
import io.wangrollin.ai.error.AiException;
import io.wangrollin.ai.chat.ChatDelta;
import io.wangrollin.ai.chat.ChatMessage;
import io.wangrollin.ai.chat.ChatRequest;
import io.wangrollin.ai.chat.ChatResponseFormat;
import io.wangrollin.ai.chat.ChatResponse;
import io.wangrollin.ai.chat.ChatUsage;
import io.wangrollin.ai.chat.ChatStream;
import io.wangrollin.ai.chat.ChatTool;
import io.wangrollin.ai.chat.ChatToolCall;
import io.wangrollin.ai.event.LoggingAiEventListener;
import io.wangrollin.ai.client.RetryPolicy;

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

OpenAI-compatible structured output can be requested with either a JSON-object hint or a JSON
schema. The SDK validates that schema text is valid JSON before sending it, while keeping the
provider-specific wire format inside the HTTP adapter.

```java
ChatResponse response = client.chat(ChatRequest.builder()
    .message(ChatMessage.system("Return only JSON that matches the requested shape."))
    .message(ChatMessage.user("Summarize the deployment risk."))
    .responseFormat(ChatResponseFormat.jsonSchema("risk_summary", """
        {
          "type": "object",
          "properties": {
            "summary": { "type": "string" },
            "risk": { "type": "string", "enum": ["low", "medium", "high"] }
          },
          "required": ["summary", "risk"],
          "additionalProperties": false
        }
        """))
    .build());
```

Tool calling is supported as request/response plumbing: the SDK sends tool definitions to the
provider and returns requested tool calls, while the application remains responsible for executing
trusted business logic and sending the tool result back.

```java
ChatResponse response = client.chat(ChatRequest.builder()
    .message(ChatMessage.user("What is the weather in Shanghai?"))
    .tool(ChatTool.function("lookup_weather", "Look up current weather by city.", """
        {
          "type": "object",
          "properties": {
            "city": { "type": "string" }
          },
          "required": ["city"],
          "additionalProperties": false
        }
        """))
    .build());

for (ChatToolCall toolCall : response.toolCalls()) {
    if ("lookup_weather".equals(toolCall.name())) {
        String resultJson = "{\"temperatureCelsius\":21}";
        ChatResponse finalResponse = client.chat(ChatRequest.builder()
            .message(ChatMessage.user("What is the weather in Shanghai?"))
            .message(ChatMessage.tool(toolCall.id(), resultJson))
            .build());
        System.out.println(finalResponse.text());
    }
}
```

`ChatResponse` exposes the generated `text()` plus optional provider metadata such as `id()`, `model()`, `finishReason()`, and `usage()`. These fields are useful for diagnostics and request correlation; avoid logging API keys, prompts, or model outputs unless your application has explicit redaction and retention controls.

```java
ChatUsage usage = response.usage();
if (usage != null) {
    System.out.printf(
        "prompt=%s completion=%s total=%s%n",
        usage.promptTokens(),
        usage.completionTokens(),
        usage.totalTokens());
}
```

Safe lifecycle events can be connected to application logging, metrics, or tracing code. Event
payloads intentionally exclude API keys, prompts, model outputs, tool arguments, and raw provider
response bodies.

```java
AiEventListener listener = new AiEventListener() {
    @Override
    public void requestSucceeded(io.wangrollin.ai.event.AiResponseEvent event) {
        System.out.printf(
            "operation=%s model=%s status=%s duration=%s%n",
            event.operation(),
            event.model(),
            event.statusCode(),
            event.duration());
    }

    @Override
    public void requestFailed(io.wangrollin.ai.event.AiFailureEvent event) {
        System.err.printf(
            "operation=%s status=%s message=%s%n",
            event.operation(),
            event.statusCode(),
            event.message());
    }
};

AiChatClient observedClient = AiClient.builder()
    .apiKey(System.getenv("OPENAI_API_KEY"))
    .defaultModel("gpt-4.1-mini")
    .eventListener(listener)
    .build();
```

For a ready-to-use logging integration, attach `LoggingAiEventListener`. It uses JDK
`System.Logger` and formats only the safe lifecycle metadata exposed by the SDK event types.

```java
AiChatClient loggedClient = AiClient.builder()
    .apiKey(System.getenv("OPENAI_API_KEY"))
    .defaultModel("gpt-4.1-mini")
    .eventListener(LoggingAiEventListener.builder()
        .logStartedEvents(false)
        .build())
    .build();
```

Provider HTTP failures are surfaced as `AiException`. When an OpenAI-compatible error body is available, the exception includes the HTTP status code and structured provider details:

```java
try {
    client.chat(ChatRequest.builder()
        .message(ChatMessage.user("Hello"))
        .build());
} catch (AiException e) {
    if (e.statusCode() != null && e.statusCode() == 429) {
        // Apply application-level rate-limit handling or backoff here.
    }
    if (e.error() != null) {
        System.err.println(e.error().code());
    }
}
```

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
import io.wangrollin.ai.client.AiChatClient;
import io.wangrollin.ai.chat.ChatDelta;
import io.wangrollin.ai.chat.ChatMessage;
import io.wangrollin.ai.chat.ChatRequest;
import io.wangrollin.ai.testing.FakeAiClient;

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

## Examples

Small, compilable examples live in `src/examples/java/io/wangrollin/ai/examples`:

- `BasicChatExample` sends a synchronous chat request.
- `StreamingChatExample` consumes incremental streaming deltas.
- `ToolCallingExample` shows provider tool-call plumbing while application code executes the tool.
- `FakeAiClientExample` demonstrates in-memory test usage without API keys or sockets.

The networked examples read `OPENAI_API_KEY` from the environment at runtime. Do not hard-code API
keys, prompts containing private data, provider response bodies, or other sensitive values in the
repository.

## Release Readiness

The project metadata includes license, SCM, issue tracker, and maintainer information expected by
public Maven repositories. Before publishing a release, run:

```shell
mvn verify
git status --short
```

Release signing keys, repository tokens, and publishing credentials should be configured only in the
publisher's local environment or CI secrets store; they should never be committed to this repository.

## License

This project is licensed under the Apache License 2.0.
