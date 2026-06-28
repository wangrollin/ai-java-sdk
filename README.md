# ai-java-sdk

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

The first implementation milestone supports synchronous OpenAI-compatible chat completions.

```java
import io.wangrolliin.ai.AiClient;
import io.wangrolliin.ai.ChatMessage;
import io.wangrolliin.ai.ChatRequest;
import io.wangrolliin.ai.ChatResponse;

import java.time.Duration;

AiClient client = AiClient.builder()
    .apiKey(System.getenv("OPENAI_API_KEY"))
    .baseUrl("https://api.openai.com/v1")
    .defaultModel("gpt-4.1-mini")
    .timeout(Duration.ofSeconds(30))
    .build();

ChatResponse response = client.chat(ChatRequest.builder()
    .message(ChatMessage.user("Hello"))
    .build());

System.out.println(response.text());
```

Run the test suite with:

```shell
mvn test
```

## License

This project is licensed under the Apache License 2.0.
