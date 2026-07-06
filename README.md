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

## Completed Foundation

The v0.1.0 release includes the small, reliable foundation originally planned for the first
implementation milestone:

- [x] A minimal chat API for synchronous responses.
- [x] A streaming API for incremental model output.
- [x] Typed request and response objects.
- [x] Consistent error handling across provider responses.
- [x] Configurable base URL, API key, model, timeout, and retry behavior.
- [x] Test utilities for mocking model responses in application tests.

## Completed Capabilities

The SDK now includes the first production-oriented layers around that foundation:

- [x] OpenAI-compatible chat completions and text-first Responses API clients.
- [x] Tool-calling request and response plumbing for chat completions.
- [x] Structured JSON output hints for chat completions and Responses API calls.
- [x] Request/response diagnostics with conservative redaction.
- [x] Safe lifecycle events and dependency-free metrics hooks.
- [x] Spring Boot auto-configuration for configuration binding and dependency injection.
- [x] Compilable examples for chat, streaming, responses, tool calling, diagnostics, metrics, and tests.

## Roadmap

Future work should keep the SDK useful in production while avoiding provider-specific leakage in
application code:

- **Provider support**: add a provider adapter abstraction, keep OpenAI-compatible support as the
  default path, and add focused provider modules for common platforms after the abstraction is stable.
- **Observability**: add optional Micrometer metrics and OpenTelemetry tracing bridges while keeping
  prompts, outputs, API keys, and raw provider bodies out of default telemetry.
- **Responses API depth**: expand beyond text-first input and output when the public API shape is
  clear, including image/file inputs, tool execution plumbing, background mode, and stored
  conversation management.
- **Production hardening**: improve timeout and cancellation coverage, add compatibility tests for
  streaming edge cases, and keep release verification centered on `mvn verify` plus a clean working
  tree.
- **Developer experience**: add complete backend workflow examples, maintain migration notes and a
  changelog before future releases, and keep all examples compiled by the normal Maven verification
  path.

## Current Status

The v0.1.0 foundation has been released. It supports OpenAI-compatible chat completions, the
text-first Responses API, streaming, tool-calling plumbing, structured output hints, safe lifecycle
events, redacted payload diagnostics, Spring Boot auto-configuration, and in-memory testing support.

The SDK does not yet include multi-provider adapters or built-in Micrometer/OpenTelemetry
integrations. Those remain roadmap items so the public API can evolve deliberately instead of
exposing provider-specific details too early.

## Requirements

- JDK 25
- Maven 3.9+

Validate the project locally with:

```shell
mvn verify
```

`mvn verify` also compiles the example sources under `core/src/examples/java` so public usage snippets
stay aligned with the current API without being packaged into the runtime jar.

## Minimal Usage

The first implementation milestone supports synchronous and streaming OpenAI-compatible chat completions.

```java
import io.wangrollin.ai.client.AiClient;
import io.wangrollin.ai.client.AiChatClient;
import io.wangrollin.ai.client.AiResponseClient;
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
import io.wangrollin.ai.diagnostic.LoggingAiPayloadDiagnosticsListener;
import io.wangrollin.ai.event.AiMetricsSnapshot;
import io.wangrollin.ai.event.InMemoryAiMetricsListener;
import io.wangrollin.ai.event.LoggingAiEventListener;
import io.wangrollin.ai.client.RetryPolicy;
import io.wangrollin.ai.response.ResponseDelta;
import io.wangrollin.ai.response.ResponseRequest;
import io.wangrollin.ai.response.ResponseResult;
import io.wangrollin.ai.response.ResponseStream;
import io.wangrollin.ai.response.ResponseTextFormat;

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

For lightweight metrics without adding a telemetry dependency, attach `InMemoryAiMetricsListener`.
It counts lifecycle attempts, terminal statuses, models, durations, and token usage from the same
safe event payloads. Applications that already use Micrometer, OpenTelemetry, or another metrics
stack can implement `AiEventListener` directly and bridge these safe fields into their own
instruments.

```java
InMemoryAiMetricsListener metrics = InMemoryAiMetricsListener.create();

AiChatClient measuredClient = AiClient.builder()
    .apiKey(System.getenv("OPENAI_API_KEY"))
    .defaultModel("gpt-4.1-mini")
    .eventListener(metrics)
    .build();

measuredClient.chat(ChatRequest.builder()
    .message(ChatMessage.user("Give me one production-readiness check."))
    .build());

AiMetricsSnapshot snapshot = metrics.snapshot();
System.out.printf(
    "started=%s succeeded=%s failed=%s%n",
    snapshot.startedCount(),
    snapshot.succeededCount(),
    snapshot.failedCount());
```

When troubleshooting provider wire-shape problems, opt in to redacted payload diagnostics. This is
separate from safe lifecycle events and is disabled by default. The default redaction policy hides
message content, tool arguments, provider error messages, and credential-like fields before anything
is logged.

```java
AiChatClient diagnosticClient = AiClient.builder()
    .apiKey(System.getenv("OPENAI_API_KEY"))
    .defaultModel("gpt-4.1-mini")
    .payloadDiagnosticsListener(LoggingAiPayloadDiagnosticsListener.create())
    .build();
```

Use payload diagnostics only in controlled environments with appropriate log retention and access
controls. Successful streaming responses are not buffered for diagnostics; only the redacted request
payload and stream metadata are emitted.

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

## Responses API

The SDK also includes a text-first OpenAI-compatible Responses API client. It reuses the same
timeouts, retry policy, safe lifecycle events, metrics listener, and redacted payload diagnostics as
chat completions.

```java
AiResponseClient responseClient = AiClient.builder()
    .apiKey(System.getenv("OPENAI_API_KEY"))
    .defaultModel("gpt-4.1-mini")
    .build();

ResponseResult result = responseClient.respond(ResponseRequest.builder()
    .instructions("Answer with concise engineering guidance.")
    .input("How should I prepare an AI SDK for production?")
    .build());

System.out.println(result.text());
```

Structured JSON output can be requested through the Responses API `text.format` field. The SDK
validates JSON Schema text before sending it and still returns the provider output as
`ResponseResult.text()` so application code can parse it into its own domain type.

```java
ResponseResult structured = responseClient.respond(ResponseRequest.builder()
    .input("Summarize this launch risk in one JSON object.")
    .textFormat(ResponseTextFormat.jsonSchema("risk_summary", """
        {
          "type": "object",
          "properties": {
            "risk": { "type": "string", "enum": ["low", "medium", "high"] },
            "summary": { "type": "string" }
          },
          "required": ["risk", "summary"],
          "additionalProperties": false
        }
        """))
    .build());
```

For streaming output, consume `ResponseStream` with try-with-resources:

```java
try (ResponseStream stream = responseClient.streamResponse(ResponseRequest.builder()
    .input("Give me two short rollout checks.")
    .build())) {
    for (ResponseDelta delta : stream) {
        System.out.print(delta.text());
    }
}
```

This first Responses API surface intentionally focuses on text input and output. Advanced provider
features such as image/file input, tool execution, background mode, and stored conversation
management are left for later milestones.

## Spring Boot Starter

Spring Boot applications can add the starter module to get configuration binding and dependency
injection for the default SDK client:

```xml
<dependency>
    <groupId>io.wangrollin.ai</groupId>
    <artifactId>ai-java-sdk-spring-boot-starter</artifactId>
    <version>0.1.0</version>
</dependency>
```

Configure the provider through external application properties or environment-backed configuration.
Do not commit real API keys to source control.

```properties
ai.sdk.api-key=${OPENAI_API_KEY}
ai.sdk.model=gpt-4.1-mini
ai.sdk.base-url=https://api.openai.com/v1
ai.sdk.timeout=30s
ai.sdk.retry.enabled=true
ai.sdk.retry.max-attempts=3
ai.sdk.retry.initial-delay=200ms
ai.sdk.retry.max-delay=2s
ai.sdk.retry.status-codes=429,500,502,503,504
```

The starter creates one `AiClient` bean when the application has not already defined an
`AiClient`, `AiChatClient`, or `AiResponseClient`. Since `AiClient` implements both interfaces,
application services can depend on the narrow contract they need:

```java
import io.wangrollin.ai.client.AiChatClient;
import org.springframework.stereotype.Service;

@Service
class AssistantService {
    private final AiChatClient client;

    AssistantService(AiChatClient client) {
        this.client = client;
    }
}
```

If the application defines `AiEventListener`, `AiPayloadDiagnosticsListener`, `AiRedactionPolicy`,
or `java.net.http.HttpClient` beans, the starter passes them into the SDK builder. Retry remains
disabled unless `ai.sdk.retry.enabled=true` is set, matching the core SDK default behavior.

## Testing Support

Application code can depend on the `AiChatClient` or `AiResponseClient` interfaces and use `FakeAiClient` in unit tests. The fake is fully in-memory: it does not require an API key, never opens a network connection, and records requests so tests can assert the prompt and generation options sent by the application.

```java
import io.wangrollin.ai.client.AiChatClient;
import io.wangrollin.ai.client.AiResponseClient;
import io.wangrollin.ai.chat.ChatDelta;
import io.wangrollin.ai.chat.ChatMessage;
import io.wangrollin.ai.chat.ChatRequest;
import io.wangrollin.ai.response.ResponseRequest;
import io.wangrollin.ai.testing.FakeAiClient;

FakeAiClient fake = FakeAiClient.builder()
    .chatResponse("Test response")
    .streamDeltas(
        new ChatDelta("Hel", null),
        new ChatDelta("lo", "stop"))
    .responseResult("Responses test result")
    .build();
AiChatClient client = fake;
AiResponseClient responseClient = fake;

String text = client.chat(ChatRequest.builder()
    .message(ChatMessage.user("Hello"))
    .build()).text();

String responseText = responseClient.respond(ResponseRequest.builder()
    .input("Hello")
    .build()).text();
```

`FakeAiClient` can also be configured with failures through `chatFailure(...)`, `streamFailure(...)`, `streamMalformedEvent(...)`, `responseFailure(...)`, `responseStreamFailure(...)`, and `responseStreamMalformedEvent(...)`, which is useful for testing retry wrappers, fallback behavior, and stream-consumption error handling in application code without calling a real provider.

Run the test suite with:

```shell
mvn test
```

The HTTP client tests start a local in-process HTTP server on an ephemeral port. In locked-down
sandboxes, the test command may need permission to bind a local port even though it does not call
an external AI provider.

## Examples

Small, compilable examples live in `core/src/examples/java/io/wangrollin/ai/examples`:

- `BasicChatExample` sends a synchronous chat request.
- `StreamingChatExample` consumes incremental streaming deltas.
- `ResponsesExample` sends synchronous and streaming text-first Responses API requests.
- `ToolCallingExample` shows provider tool-call plumbing while application code executes the tool.
- `MetricsListenerExample` collects safe request metrics from lifecycle events.
- `PayloadDiagnosticsExample` enables opt-in redacted payload diagnostics.
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
