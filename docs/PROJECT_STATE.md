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

Name: M2 - Production Java Backend Adoption

Status: in_progress

Goal:

- Make the SDK's adoption story obvious for Java and Spring Boot backend teams.
- Demonstrate a realistic backend workflow that combines structured output, safe observability,
  provider configuration, and in-memory tests.
- Improve the testing experience so application code can cover AI behavior without API keys,
  sockets, or provider availability.
- Add compatibility evidence for OpenAI-compatible providers instead of accumulating unverified
  provider promises.
- Keep production safety centered on timeout, cancellation, retry, streaming, telemetry, and
  redaction behavior.

Completion criteria:

- README explains the project value in terms of safe Java backend AI integration within the first
  screen.
- A complete Spring Boot backend workflow example exists and is compiled or verified by the normal
  project checks.
- Testing guidance shows how to assert prompt assembly, structured output requests, failures, and
  streaming behavior with `FakeAiClient`.
- Metrics, tracing, and diagnostics examples preserve conservative redaction defaults.
- A provider compatibility matrix documents verified behavior for chat, streaming, tool calling,
  JSON output, and Responses API support.
- The project records and verifies a Java baseline appropriate for the intended Java/Spring
  audience.

## Recent Progress

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
- If the project stays at the low-level client layer, users may choose official SDKs or handwritten
  HTTP clients instead.
- The first Spring Boot workflow example now shows an HTTP controller boundary, but it still does not
  show asynchronous queue consumption, which some backend teams may expect for support workflows.

## Next Candidates

1. Run the live-provider verification workflow against one existing non-OpenAI preset and record the
   first model-specific evidence without committing credentials or provider payloads.
2. Add release and migration guidance that makes the Java 17 runtime baseline and supported module
   combinations explicit for backend adopters.
3. Audit the remaining M2 completion criteria after the first live evidence and release guidance, and
   either close M2 or record the smallest remaining adoption gap.

## Long-Term Goal Review

The recommended direction is now narrower and more adoption-focused: build a production-ready
Java/Spring backend AI integration SDK. Future work should prioritize backend workflow evidence,
testing support, safe observability, and verified provider compatibility over broad API surface area
or agent-framework ambitions. The Java 17 baseline supports this direction and does not require a
change to the long-term goal.
