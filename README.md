# ai-java-sdk

[![CI](https://github.com/wangrollin/ai-java-sdk/actions/workflows/ci.yml/badge.svg)](https://github.com/wangrollin/ai-java-sdk/actions/workflows/ci.yml)
[![License](https://img.shields.io/badge/license-Apache--2.0-blue.svg)](LICENSE)
[![Java](https://img.shields.io/badge/java-17%2B-orange.svg)](pom.xml)

`ai-java-sdk` is a production-ready Java SDK for adding AI calls to backend services without
rebuilding retries, streaming cleanup, safe logs, metrics, tracing, tests, and provider
configuration around every model integration.

The goal of this project is not to be another thin wrapper around a model provider's HTTP API. It is
intended to give Java and Spring Boot teams a practical integration layer for reliable, observable,
testable, and provider-flexible AI features in real backend systems.

## Why This Project Exists

AI APIs are easy to call once, but harder to operate well in a Java backend. Production systems need
more than request and response objects. They need predictable timeouts, retries, streaming behavior,
typed models, safe logging, metrics, tracing, test doubles, and clean integration with Spring Boot
configuration.

`ai-java-sdk` aims to bring those concerns into one coherent SDK so Java teams can focus on product
behavior instead of rebuilding the same infrastructure around every provider.

## Core Value

- **Production-safe by default**: treat timeouts, retries, errors, streaming cleanup, and sensitive
  telemetry as first-class SDK concerns.
- **Spring Boot ready**: expose configuration binding and dependency injection so backend services
  can adopt AI calls without custom wiring in every application.
- **Test AI code without API calls**: provide narrow client interfaces and an in-memory fake so CI
  can assert prompts, generation options, failures, and stream behavior without credentials.
- **OpenAI-compatible provider switching**: support OpenAI-compatible APIs first, with presets and
  compatibility evidence added only when they provide real backend adoption value.

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

- [x] OpenAI-compatible chat completions and Responses API clients.
- [x] Tool-calling request and response plumbing for chat completions.
- [x] Tool-calling request and response plumbing for Responses API calls.
- [x] Structured JSON output hints for chat completions and Responses API calls.
- [x] Responses API image input references, function-tool plumbing, and background mode requests.
- [x] Anthropic Claude Messages API adapter for chat, streaming text, and basic tool calling.
- [x] Request/response diagnostics with conservative redaction.
- [x] Safe lifecycle events, dependency-free metrics hooks, optional Micrometer metrics, and optional OpenTelemetry tracing.
- [x] Spring Boot auto-configuration for configuration binding and dependency injection.
- [x] Internal provider adapter boundary with OpenAI-compatible support as the default implementation.
- [x] Provider presets for common OpenAI-compatible model services and Anthropic.
- [x] Compilable examples for chat, streaming, responses, tool calling, diagnostics, metrics, and tests.

## Spring Boot Workflow Example

For a backend-oriented adoption path, see `examples/support-ticket-triage`. It is a small Spring
Boot REST workflow that accepts support tickets over HTTP, uses the starter configuration, requests
structured JSON output for support-ticket routing, wires metadata-only lifecycle logging, and tests
the service and controller with `FakeAiClient` instead of API keys or sockets.

## Backend Adoption Quick Path

For a Spring Boot backend team, the shortest path is:

1. Add `ai-java-sdk-spring-boot-starter` and configure `ai.sdk.*` from environment-backed
   application properties.
2. Depend on the narrow interface the service needs, usually `AiChatClient` for chat-style
   workflows or `AiResponseClient` for OpenAI-compatible Responses API calls.
3. Model the AI boundary as ordinary backend code: assemble prompts, request structured JSON when
   the service needs typed routing decisions, and keep lifecycle logging metadata-only by default.
4. Test the service and HTTP boundary with `FakeAiClient`, asserting prompt assembly, structured
   output requests, SDK failures, and controller error mapping without API keys or sockets.

The `examples/support-ticket-triage` module follows this path end to end: a Spring Boot controller
exposes `POST /support-tickets/triage`, the service asks for structured category/priority/queue
output, safe telemetry stays outside the model payload, and tests cover both service behavior and
the REST boundary using the in-memory fake.

## Roadmap

Future work should prove that the SDK helps real Java backend teams adopt AI safely, not just expose
more provider fields:

- **Backend workflow demo**: add a complete Spring Boot service example that combines structured
  output, safe observability, provider configuration, and in-memory tests.
- **Testing experience**: expand the fake client and examples so application tests can cover prompt
  assembly, fallback behavior, tool calls, structured output, failures, and streaming errors.
- **Compatibility evidence**: document which OpenAI-compatible providers have been verified for chat,
  streaming, tool calling, JSON output, and Responses API behavior. The current evidence matrix lives
  in [`docs/COMPATIBILITY.md`](docs/COMPATIBILITY.md).
- **Production control plane**: continue hardening timeout, cancellation, retry, telemetry, and
  redaction behavior before adding broader provider-specific abstractions.
- **Adoption readiness**: keep examples compiled, publish clear migration and release notes, and
  preserve the Java 17 minimum baseline in CI so the SDK remains usable by established Java/Spring
  backend teams.

## Current Status

The v0.1.0 foundation has been released. It already contains the production integration pieces that
make the project more than a model API wrapper: OpenAI-compatible chat completions and Responses API
clients, Anthropic Claude Messages chat support, streaming, tool-calling plumbing, structured output
hints, safe lifecycle events, optional Micrometer metrics, optional OpenTelemetry tracing, redacted
payload diagnostics, Spring Boot auto-configuration, provider presets, and in-memory testing support.

The SDK targets Java 17 bytecode and verifies the full build on both Java 17 and Java 25. This keeps
the minimum runtime aligned with established Spring backend environments while continuously checking
compatibility with a current JDK.

OpenAI-compatible APIs remain the default provider path. Anthropic support is implemented as a focused
internal adapter for Claude Messages API chat calls; it intentionally does not pretend that Claude
Messages is the same protocol as OpenAI Responses API. Provider presets are configuration shortcuts,
not live compatibility promises; see [`docs/COMPATIBILITY.md`](docs/COMPATIBILITY.md) for the current
evidence level behind each preset and capability.

## Requirements

- JDK 17 or newer
- Maven 3.9+

## Version Status

`v0.2.0` is the latest tagged release. It is distributed through GitHub Releases rather than a
public Maven repository. Build the tag locally with `mvn install`, or download the release assets
from GitHub when managing the jars directly, and keep all SDK modules on the same version.

See [`CHANGELOG.md`](CHANGELOG.md) for release contents and
[`docs/UPGRADING.md`](docs/UPGRADING.md) for the `0.1.0` to `0.2.0` migration requirements.

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
import io.wangrollin.ai.client.AiProvider;
import io.wangrollin.ai.client.RetryPolicy;
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
import io.wangrollin.ai.response.ResponseDelta;
import io.wangrollin.ai.response.ResponseInputMessage;
import io.wangrollin.ai.response.ResponseInputPart;
import io.wangrollin.ai.response.ResponseRequest;
import io.wangrollin.ai.response.ResponseResult;
import io.wangrollin.ai.response.ResponseStream;
import io.wangrollin.ai.response.ResponseTextFormat;
import io.wangrollin.ai.response.ResponseTool;
import io.wangrollin.ai.response.ResponseToolCall;

import java.time.Duration;

AiChatClient client = AiClient.builder()
    .apiKey(System.getenv("OPENAI_API_KEY"))
    .provider(AiProvider.OPENAI_COMPATIBLE)
    .baseUrl(System.getenv("OPENAI_BASE_URL"))
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
    .baseUrl(System.getenv("OPENAI_BASE_URL"))
    .defaultModel("gpt-4.1-mini")
    .eventListener(listener)
    .build();
```

For a ready-to-use logging integration, attach `LoggingAiEventListener`. It uses JDK
`System.Logger` and formats only the safe lifecycle metadata exposed by the SDK event types.

```java
AiChatClient loggedClient = AiClient.builder()
    .apiKey(System.getenv("OPENAI_API_KEY"))
    .baseUrl(System.getenv("OPENAI_BASE_URL"))
    .defaultModel("gpt-4.1-mini")
    .eventListener(LoggingAiEventListener.builder()
        .logStartedEvents(false)
        .build())
    .build();
```

For lightweight metrics without adding a telemetry dependency, attach `InMemoryAiMetricsListener`.
It counts lifecycle attempts, terminal statuses, models, durations, and token usage from the same
safe event payloads.

```java
InMemoryAiMetricsListener metrics = InMemoryAiMetricsListener.create();

AiChatClient measuredClient = AiClient.builder()
    .apiKey(System.getenv("OPENAI_API_KEY"))
    .baseUrl(System.getenv("OPENAI_BASE_URL"))
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

Applications that already use Micrometer can add the optional bridge module and register the same
safe lifecycle fields as Micrometer meters. The bridge intentionally avoids high-cardinality and
sensitive tags such as provider paths, base URLs, prompts, outputs, tool arguments, raw bodies, and
diagnostic messages.

```xml
<dependency>
    <groupId>io.wangrollin.ai</groupId>
    <artifactId>ai-java-sdk-micrometer</artifactId>
    <version>0.2.0</version>
</dependency>
```

```java
import io.micrometer.core.instrument.MeterRegistry;
import io.wangrollin.ai.micrometer.MicrometerAiEventListener;

MeterRegistry registry = // obtain from your application runtime

AiChatClient micrometerClient = AiClient.builder()
    .apiKey(System.getenv("OPENAI_API_KEY"))
    .baseUrl(System.getenv("OPENAI_BASE_URL"))
    .defaultModel("gpt-4.1-mini")
    .eventListener(MicrometerAiEventListener.create(registry))
    .build();
```

Applications that already export OpenTelemetry traces can add the optional bridge module. The
listener creates request-attempt spans from the same safe lifecycle fields and avoids prompts,
outputs, API keys, raw bodies, provider paths, base URLs, and diagnostic messages by default.

```xml
<dependency>
    <groupId>io.wangrollin.ai</groupId>
    <artifactId>ai-java-sdk-opentelemetry</artifactId>
    <version>0.2.0</version>
</dependency>
```

```java
import io.opentelemetry.api.OpenTelemetry;
import io.wangrollin.ai.opentelemetry.OpenTelemetryAiEventListener;

OpenTelemetry openTelemetry = // obtain from your application runtime

AiChatClient tracedClient = AiClient.builder()
    .apiKey(System.getenv("OPENAI_API_KEY"))
    .baseUrl(System.getenv("OPENAI_BASE_URL"))
    .defaultModel("gpt-4.1-mini")
    .eventListener(OpenTelemetryAiEventListener.create(openTelemetry))
    .build();
```

When troubleshooting provider wire-shape problems, opt in to redacted payload diagnostics. This is
separate from safe lifecycle events and is disabled by default. The default redaction policy hides
message content, tool arguments, provider error messages, and credential-like fields before anything
is logged.

```java
AiChatClient diagnosticClient = AiClient.builder()
    .apiKey(System.getenv("OPENAI_API_KEY"))
    .baseUrl(System.getenv("OPENAI_BASE_URL"))
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
    .baseUrl(System.getenv("OPENAI_BASE_URL"))
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

The SDK also includes an OpenAI-compatible Responses API client for text output. It reuses the same
timeouts, retry policy, safe lifecycle events, metrics listener, and redacted payload diagnostics as
chat completions.

```java
AiResponseClient responseClient = AiClient.builder()
    .apiKey(System.getenv("OPENAI_API_KEY"))
    .baseUrl(System.getenv("OPENAI_BASE_URL"))
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

Responses API requests can also send typed input messages that combine text with image references.
The SDK passes image URLs, data URLs, or provider file ids through to the OpenAI-compatible adapter;
applications remain responsible for hosting images, creating data URLs, or uploading files when a
provider-specific file workflow is required.

```java
ResponseResult imageSummary = responseClient.respond(ResponseRequest.builder()
    .inputMessage(ResponseInputMessage.user(
        ResponseInputPart.text("Describe the operational risk visible in this image."),
        ResponseInputPart.imageUrl(
            "https://example.com/dashboard.png",
            ResponseInputPart.ImageDetail.LOW)))
    .build());

System.out.println(imageSummary.text());
```

For a previously uploaded provider file id, use `ResponseInputPart.imageFileId("file_...")`.
When a provider supports Responses API background execution, the SDK can pass that request flag
through without introducing a provider-specific polling abstraction:

```java
ResponseResult backgroundResult = responseClient.respond(ResponseRequest.builder()
    .input("Prepare a concise incident review draft.")
    .background(true)
    .build());

System.out.println(backgroundResult.id());
```

Responses API function tools use the same SDK boundary as chat tools: the SDK sends tool definitions
and returns requested function calls, while the application owns trusted execution and result
validation. Send the tool result back with the provider `call_id` and the previous response id:

```java
ResponseResult toolPlanning = responseClient.respond(ResponseRequest.builder()
    .input("What is the weather in Shanghai?")
    .tool(ResponseTool.function("lookup_weather", "Look up current weather by city.", """
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

for (ResponseToolCall toolCall : toolPlanning.toolCalls()) {
    if ("lookup_weather".equals(toolCall.name())) {
        ResponseResult finalResult = responseClient.respond(ResponseRequest.builder()
            .previousResponseId(toolPlanning.id())
            .functionCallOutput(toolCall.callId(), "{\"temperatureCelsius\":21}")
            .build());
        System.out.println(finalResult.text());
    }
}
```

Advanced provider features such as stored conversation management are left for later milestones.

## Provider Compatibility

`AiProviderPreset` provides protocol selection and documented base URL references for common model
services. Applications still provide API keys, endpoint URLs, and model names through
environment-backed configuration so proxies, gateways, and regional endpoints are explicit.

```java
AiChatClient client = AiClient.builder()
    .apiKey(System.getenv("DEEPSEEK_API_KEY"))
    .baseUrl(System.getenv("DEEPSEEK_BASE_URL"))
    .providerPreset(AiProviderPreset.DEEPSEEK)
    .defaultModel("deepseek-chat")
    .build();
```

| Preset | Base URL | Initial support |
| --- | --- | --- |
| `OPENAI` | `https://api.openai.com/v1` | Chat Completions and Responses API |
| `DEEPSEEK` | `https://api.deepseek.com` | OpenAI-compatible Chat Completions |
| `QWEN` | `https://dashscope.aliyuncs.com/compatible-mode/v1` | OpenAI-compatible Chat Completions |
| `MOONSHOT` | `https://api.moonshot.cn/v1` | OpenAI-compatible Chat Completions |
| `ZHIPU` | `https://open.bigmodel.cn/api/paas/v4` | OpenAI-compatible Chat Completions |
| `OPENROUTER` | `https://openrouter.ai/api/v1` | OpenAI-compatible Chat Completions |
| `ANTHROPIC` | `https://api.anthropic.com/v1` | Claude Messages API chat and streaming text |

For OpenAI-compatible non-OpenAI services, the first compatibility target is chat, streaming chat,
tool calling, and JSON output hints through the chat completions protocol. The SDK's Responses API
client remains available, but providers that do not implement `/responses` may reject those calls.
For Anthropic, use `chat(...)` or `stream(...)`; `respond(...)` and `streamResponse(...)` fail fast
because Claude Messages is not the OpenAI Responses API.

```java
AiChatClient claude = AiClient.builder()
    .apiKey(System.getenv("ANTHROPIC_API_KEY"))
    .baseUrl(System.getenv("ANTHROPIC_BASE_URL"))
    .providerPreset(AiProviderPreset.ANTHROPIC)
    .defaultModel("claude-sonnet-4-20250514")
    .build();
```

Use `baseUrl(...)` or `ai.sdk.base-url` for every runtime client. Preset base URLs are reference
values that can be copied into environment-specific configuration, not implicit runtime defaults.

## Spring Boot Starter

Spring Boot applications can add the starter module to get configuration binding and dependency
injection for the default SDK client:

```xml
<dependency>
    <groupId>io.wangrollin.ai</groupId>
    <artifactId>ai-java-sdk-spring-boot-starter</artifactId>
    <version>0.2.0</version>
</dependency>
```

Configure the provider through external application properties or environment-backed configuration.
Do not commit real API keys to source control.

```properties
ai.sdk.api-key=${OPENAI_API_KEY}
ai.sdk.model=gpt-4.1-mini
ai.sdk.provider=openai-compatible
ai.sdk.provider-preset=openai
ai.sdk.base-url=${OPENAI_BASE_URL}
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
`ai.sdk.provider` defaults to `openai-compatible`, so applications can omit it until they need to
make provider protocol selection explicit across environments. `ai.sdk.provider-preset` defaults to
`openai`; use `ai.sdk.provider=anthropic` and `ai.sdk.provider-preset=anthropic` for Claude. Always
set `ai.sdk.base-url`; presets expose reference endpoints but do not bind the runtime client to one.

## Testing Support

Application code can depend on the `AiChatClient` or `AiResponseClient` interfaces and use `FakeAiClient` in unit tests. The fake is fully in-memory: it does not require an API key, never opens a network connection, and records requests so tests can assert the prompt and generation options sent by the application.

For focused application-test patterns covering structured-output assertions, tool-call fixtures,
scripted fallback, and stream-consumption failures, see [`docs/TESTING.md`](docs/TESTING.md).

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
- `ResponsesExample` sends text, structured-output, image-input, and streaming Responses API requests.
- `ToolCallingExample` shows provider tool-call plumbing while application code executes the tool.
- `MetricsListenerExample` collects safe request metrics from lifecycle events.
- `PayloadDiagnosticsExample` enables opt-in redacted payload diagnostics.
- `FakeAiClientExample` demonstrates in-memory test usage without API keys or sockets.

The adoption-focused Spring Boot example in `examples/support-ticket-triage` exposes
`POST /support-tickets/triage`, returns structured JSON such as category, priority, summary, and
queue, and demonstrates configuration-only provider switching, safe lifecycle logging, and
fake-client tests for both the service contract and HTTP boundary.

The networked examples read `OPENAI_API_KEY` and `OPENAI_BASE_URL` from the environment at runtime.
Do not hard-code API keys, prompts containing private data, provider response bodies, or other
sensitive values in the repository.

## Release Distribution

Releases are published through GitHub Releases with the main, sources, and javadoc jars for the
core, Spring Boot starter, Micrometer, and OpenTelemetry modules. The example application remains a
source-only reactor module and is not uploaded as a release asset. Before publishing a release, run:

```shell
mvn verify
git status --short
```

GitHub tokens and other publishing credentials must remain in the publisher's local environment or
CI secrets store; they must never be committed to this repository. The project is not currently
published to Maven Central or another public Maven repository.

## License

This project is licensed under the Apache License 2.0.
