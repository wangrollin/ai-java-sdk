# Upgrading ai-java-sdk

## Version Status

`v0.2.0` is the latest tagged release. It is published through GitHub Releases rather than Maven
Central or another public Maven repository. Check out the tag and run `mvn install` for normal Maven
dependency resolution, or download the GitHub release assets when managing jars directly.

All SDK modules used by one application should use the same version. Do not mix a released core jar
with snapshot integration modules.

## Upgrade from 0.1.0 to 0.2.0

### Configure the Provider Base URL Explicitly

`0.1.0` defaulted the core client and Spring Boot starter to the OpenAI endpoint. `0.2.0` requires
applications to provide the endpoint so a preset cannot silently route production traffic.

Core client configuration must include `baseUrl(...)`:

```java
AiChatClient client = AiClient.builder()
    .apiKey(System.getenv("OPENAI_API_KEY"))
    .baseUrl(System.getenv("OPENAI_BASE_URL"))
    .defaultModel("gpt-4.1-mini")
    .build();
```

Spring Boot applications must configure `ai.sdk.base-url`, preferably through an environment-backed
property:

```properties
ai.sdk.api-key=${OPENAI_API_KEY}
ai.sdk.base-url=${OPENAI_BASE_URL}
ai.sdk.model=gpt-4.1-mini
```

Provider presets select a protocol and expose a reference base URL for documentation and tooling.
They intentionally do not populate the runtime base URL.

### Java Baseline

Published artifacts target Java 17 bytecode. Applications and contributors can use JDK 17 or newer;
CI verifies the full reactor on Java 17 and Java 25. No source migration is required when moving from
the earlier Java 25 build requirement to Java 17.

### Provider Selection

OpenAI-compatible behavior remains the default. Existing applications do not need to select a
provider unless they want to make the protocol explicit or use Anthropic:

```java
AiChatClient claude = AiClient.builder()
    .apiKey(System.getenv("ANTHROPIC_API_KEY"))
    .baseUrl(System.getenv("ANTHROPIC_BASE_URL"))
    .providerPreset(AiProviderPreset.ANTHROPIC)
    .defaultModel("claude-sonnet-4-20250514")
    .build();
```

Anthropic supports `AiChatClient` chat, streaming text, and basic tool calls. It does not implement
the OpenAI Responses API, so `AiResponseClient` calls fail fast for that provider.

## Module Selection

| Application need | Dependency | Notes |
| --- | --- | --- |
| Core chat, Responses API, telemetry hooks, and test fake | `ai-java-sdk` | Add directly for framework-neutral applications. |
| Spring Boot configuration and dependency injection | `ai-java-sdk-spring-boot-starter` | Includes core transitively; verified with Spring Boot 4 and Spring Boot 3.5. |
| Micrometer metrics | `ai-java-sdk-micrometer` | Add beside core or the starter; use the same SDK version. |
| OpenTelemetry tracing | `ai-java-sdk-opentelemetry` | Add beside core or the starter; use the same SDK version. |

The example modules are source examples, not application dependencies. The complete reactor,
including the starter, support-ticket triage example, and knowledge-base RAG example, is verified
with the default Spring Boot 4 line and with Spring Boot 3.5.16 plus Spring Framework 6.2.19. Earlier
Spring Boot 3.x lines are not part of the current compatibility claim.

## Verification Checklist

After upgrading an application:

1. Run the application on JDK 17 or newer.
2. Confirm the API key, base URL, and model come from external configuration.
3. Exercise chat and streaming cleanup paths with `FakeAiClient` before enabling live traffic.
4. Enable only metadata-safe lifecycle telemetry by default.
5. Check [`COMPATIBILITY.md`](COMPATIBILITY.md) before relying on a provider preset in production.
