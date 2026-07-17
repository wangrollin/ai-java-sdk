# Changelog

This file records user-visible changes to `ai-java-sdk`. The repository follows Semantic Versioning;
work on `main` uses the next `-SNAPSHOT` version until a release is prepared and tagged.

## [Unreleased]

## [0.2.0] - 2026-07-17

### Added

- OpenAI-compatible Responses API support for typed inputs, image references, function tools,
  structured output, background requests, and streaming.
- Anthropic Claude Messages support for chat, streaming text, and basic function-tool calls behind
  the existing `AiChatClient` API.
- Provider selection and presets, with a compatibility evidence matrix that separates configuration
  shortcuts from live-provider claims.
- Optional Micrometer and OpenTelemetry modules built on metadata-only lifecycle events.
- A Spring Boot support-ticket triage example with structured output, safe telemetry, an HTTP
  boundary, and in-memory tests.
- Expanded `FakeAiClient` fixtures and testing guidance for request assertions, ordered failures,
  tool calls, and stream-consumption failures.
- An opt-in, credential-free-by-default workflow for recording live-provider compatibility evidence.

### Changed

- The minimum runtime and build baseline is Java 17 instead of Java 25; CI verifies Java 17 and a
  current JDK.
- Runtime clients must configure a base URL explicitly. Provider presets expose reference endpoints
  but do not silently select a production endpoint.
- Provider wire formats now translate through an internal neutral protocol while the public chat and
  Responses APIs remain Java-first.
- Project positioning now focuses on production Java and Spring Boot backend adoption rather than
  broad provider or agent-framework coverage.

### Fixed

- Streaming cleanup and lifecycle behavior now cover caller cancellation and failures that occur
  while consuming a stream.
- Documentation examples are compiled or backed by focused tests to reduce drift from public APIs.

See [`docs/UPGRADING.md`](docs/UPGRADING.md) before moving an application from `0.1.0` to `0.2.0`.

## [0.1.0]

- Added synchronous and streaming OpenAI-compatible chat completions.
- Added typed requests and responses, provider error handling, configurable timeouts and retries,
  Spring Boot auto-configuration, safe lifecycle events, redacted diagnostics, and `FakeAiClient`.
- Established the initial Java-first SDK foundation.

[Unreleased]: https://github.com/wangrollin/ai-java-sdk/compare/v0.2.0...HEAD
[0.2.0]: https://github.com/wangrollin/ai-java-sdk/compare/v0.1.0...v0.2.0
[0.1.0]: https://github.com/wangrollin/ai-java-sdk/releases/tag/v0.1.0
