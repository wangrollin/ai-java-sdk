# Project State

## Long-Term Vision

`ai-java-sdk` is a production-ready Java SDK for adding AI calls to Java and Spring Boot backend
services without rebuilding retries, streaming cleanup, safe logs, metrics, tracing, tests, and
provider configuration around every model integration. The project should grow through pure AI
coding in small, verifiable steps: each iteration should either improve production reliability,
reduce future uncertainty, or make the SDK easier for Java teams to adopt safely.

The long-term goal is not to chase every provider feature immediately or become a general-purpose
agent framework. The SDK should preserve a Java-first public API, keep provider-specific wire details
behind stable boundaries, and make operational concerns such as retries, streaming, diagnostics,
metrics, tracing, and testing support first-class.

## Current Milestone

Name: M4 - v0.3.0 Embeddings and RAG Foundation

Status: in progress

Goal:

- Add a typed, batch-oriented Embeddings API for OpenAI-compatible providers.
- Prove a complete Java backend RAG workflow without introducing a vector-store framework.
- Preserve safe observability, deterministic testing, provider boundaries, and the Java 17 baseline.

Completion criteria:

- `AiEmbeddingClient` supports batch input, model overrides, optional dimensions, ordered vectors,
  usage metadata, retry/error behavior, and redacted diagnostics.
- Spring Boot, `FakeAiClient`, compatibility probes, and lifecycle telemetry support Embeddings.
- A compiled Spring Boot example demonstrates synthetic corpus indexing, cosine retrieval, grounded
  generation, and deterministic fake-client tests.
- The full reactor remains green on the `0.3.0-SNAPSHOT` line before release preparation.

## M2 Audit Result

M2 - Production Java Backend Adoption: completed on 2026-07-16. All six completion criteria were
confirmed from repository evidence, and `mvn verify` completed successfully across the full reactor.
The remaining non-OpenAI live-provider verification is an external, credential-dependent activity.
It was initially tracked in M3 and was later retained as optional compatibility work rather than a
`v0.2.0` release gate.

## Recent Progress

- 2026-07-17: Started M4 on `0.3.0-SNAPSHOT` with a typed OpenAI-compatible Embeddings API, batch
  requests, ordered vector results, usage metadata, safe vector redaction, fake-client fixtures,
  Spring Boot injection, and an optional live compatibility capability. Added a compiled in-memory
  RAG example using a synthetic corpus, cosine retrieval, request-level embedding/chat models, and
  deterministic tests without a vector-store abstraction. Full `mvn verify` passed across all seven
  reactor modules; the remaining M4 step is explicit `v0.3.0` release preparation and publication.
- 2026-07-17: Prepared stable `0.2.0` coordinates and GitHub Release documentation for the four
  reusable SDK modules. Live-provider verification remains opt-in compatibility work and is not a
  release gate; compatibility claims remain limited to recorded evidence.
- 2026-07-16: Audited M2 against its six completion criteria; all criteria are satisfied by the
  README, Spring Boot workflow example, testing guidance, observability examples, compatibility
  matrix, and Java 17 CI/build configuration. `mvn verify` passed for the complete reactor, so M3
  now focuses on release readiness and the first model-specific live compatibility record.

- 2026-07-15: Moved post-v0.1.0 development to `0.2.0-SNAPSHOT`, added a changelog and focused
  upgrade guide covering explicit base URLs, the Java 17 baseline, provider selection, and supported
  module combinations; corrected contributor documentation that still required Java 25.
- 2026-07-14: Added an opt-in, metadata-only live-provider compatibility probe for chat, streaming,
  tool calling, structured JSON output, and Responses API behavior; documented a credential-safe
  workflow and evidence record format without adding external calls to the default Maven or CI path.
- 2026-07-14: Added focused `FakeAiClient` testing guidance and a build-compiled application example
  covering structured-output request assertions, tool-call fixtures, scripted fallback, stream-opening
  failures, and stream-consumption failures; added regression coverage for structured request history
  and ordered failure/success outcomes.
- 2026-07-14: Lowered the published bytecode and build baseline from Java 25 to Java 17 after
  confirming that current dependencies and SDK language features support it; CI now verifies the
  full reactor on Java 17 and Java 25.
- 2026-07-13: Added `docs/COMPATIBILITY.md` to separate SDK-verified capabilities from preset-only
  provider configuration and unsupported protocol claims across chat, streaming, tool calling, JSON
  output, and Responses API behavior.
- 2026-07-12: Added a README backend adoption quick path that connects the first-screen value
  proposition to the Spring Boot starter, the `support-ticket-triage` REST workflow, metadata-only
  telemetry, and `FakeAiClient` tests for service and HTTP boundaries.
- 2026-07-12: Introduced an internal provider-neutral turn protocol for typed content blocks,
  function tools/tool calls, usage, and stream events; OpenAI Chat Completions, OpenAI Responses,
  and Claude Messages adapters now translate through that boundary while preserving existing public
  chat and response APIs.
- 2026-07-12: Added a thin HTTP REST boundary to the Spring Boot `support-ticket-triage` example,
  including safe exception mapping and `FakeAiClient` controller tests that verify structured output
  requests without leaking model text or SDK failure details.
- 2026-07-11: Added a compiled Spring Boot `support-ticket-triage` workflow example that combines
  structured JSON output, externalized starter configuration, metadata-only lifecycle logging, and
  `FakeAiClient` tests for prompt assembly, schema requests, parsing failures, and SDK failures.
- 2026-07-11: Added focused compatibility coverage for caller-side early stream close across chat streams, Responses streams, and lifecycle events; no production code change was needed because the current stream implementation already treats early close as normal cancellation.
- 2026-07-11: Repositioned M2 around production Java backend adoption: Spring Boot workflow
  examples, testing support, safe observability, compatibility evidence, and explicit adoption
  risks now take priority over broad provider expansion.
- 2026-07-11: Established long-running AI coding project state docs to make future iterations more deliberate and easier to resume.
- v0.1.0: Completed the initial foundation: synchronous chat, streaming, typed request and response objects, provider errors, configurable client options, retries, and in-memory test support.
- Post-v0.1.0: Added OpenAI-compatible chat and Responses API clients, tool-calling plumbing, structured output hints, image input references, background mode requests, diagnostics, metrics hooks, optional Micrometer and OpenTelemetry support, Spring Boot auto-configuration, provider presets, and compiled examples.

## Known Risks

- Provider-specific features can leak into the public API if extension work is not kept behind clear adapter boundaries.
- Observability can become unsafe if future events include prompts, outputs, raw bodies, API keys, or tool arguments by default.
- Streaming behavior is easy to regress because failures often happen in timeout, interrupted network, partial output, and resource cleanup paths.
- Documentation can drift from implementation unless examples and verification commands remain part of normal Maven checks.
- JUnit snippets in the testing guide are documentation rather than compiled test sources; the
  framework-neutral example and focused `FakeAiClientTest` coverage must remain aligned with them.
- Provider presets are now documented separately from compatibility claims, but most non-OpenAI
  OpenAI-compatible presets still lack live-provider verification.
- Live-provider verification is deliberately manual and model-specific; recorded evidence can become
  stale when providers change model behavior or protocol compatibility.
- GitHub Release assets are not a Maven repository; adopters must build and install the tag locally
  or manage downloaded jars and their transitive dependencies explicitly.
- The Embeddings API currently targets OpenAI-compatible wire behavior; provider presets still need
  model-specific live evidence before compatibility claims are promoted.
- The RAG example uses an in-memory index and startup embedding call; production applications still
  own durable storage, document ingestion, chunking, index refresh, and availability strategy.
- If the project stays at the low-level client layer, users may choose official SDKs or handwritten
  HTTP clients instead.
- The first Spring Boot workflow example now shows an HTTP controller boundary, but it still does not
  show asynchronous queue consumption, which some backend teams may expect for support workflows.

## Next Candidates

1. Complete deterministic M4 verification and prepare the `v0.3.0` GitHub Release.
2. Record optional model-specific Embeddings compatibility evidence without credentials or vectors.
3. Evaluate durable vector-store integration only after real application feedback identifies a
   stable abstraction boundary.

## Long-Term Goal Review

The recommended direction is now narrower and more adoption-focused: build a production-ready
Java/Spring backend AI integration SDK. Future work should prioritize backend workflow evidence,
testing support, safe observability, and verified provider compatibility over broad API surface area
or agent-framework ambitions. The Java 17 baseline supports this direction and does not require a
change to the long-term goal.
