# Roadmap

This roadmap describes direction, not a fixed delivery schedule. Keep it focused on milestone intent so future AI coding agents can choose small, verifiable next steps without losing the long-term path.

## M1 - v0.1.0 Foundation

Status: completed

Outcome:

- Minimal synchronous chat API.
- Streaming API for incremental model output.
- Typed request and response objects.
- Consistent provider error handling.
- Configurable base URL, API key, model, timeout, and retry behavior.
- Test utilities for mocking model responses in application tests.

## M2 - Production Java Backend Adoption

Status: completed (2026-07-16)

Intent:

- Make the SDK's value clear to Java and Spring Boot backend teams within a short README scan.
- Prove that the SDK helps teams ship AI features with safe observability, tests, and provider
  configuration instead of only wrapping HTTP APIs.
- Keep production reliability and provider boundaries strong while prioritizing adoption evidence.

Focus areas:

- A complete Spring Boot backend workflow demo, starting with a support-ticket triage service.
- Testing guidance and fake-client capabilities for prompt assembly, structured output, failures,
  tool calls, and streaming behavior.
- Optional metrics, tracing, and redacted diagnostics that preserve conservative defaults.
- Provider compatibility evidence for OpenAI-compatible chat, streaming, tool calling, JSON output,
  and Responses API behavior.
- Adoption readiness work such as README messaging, migration notes, release notes, and preserving
  the documented Java 17 baseline through normal CI verification.

Outcome:

- README leads with safe Java backend adoption and a Spring Boot quick path.
- The compiled `support-ticket-triage` workflow demonstrates structured output, safe telemetry,
  externalized configuration, an HTTP boundary, and `FakeAiClient` tests.
- Testing, diagnostics, metrics, tracing, compatibility, upgrade, and Java-baseline guidance are
  documented and covered by the normal Maven verification path.

## M3 - v0.2.0 Release Readiness

Status: completed (2026-07-17)

Intent:

- Turn the post-v0.1.0 snapshot into a GitHub Release that backend adopters can evaluate with clear
  compatibility boundaries and a migration path.
- Keep live-provider checks opt-in and separate from deterministic release verification.

Focus areas:

- Publish the four reusable SDK modules as main, sources, and javadoc jars on GitHub Releases.
- Promote release metadata, changelog, and upgrade documentation, and remove `-SNAPSHOT` coordinates.
- Run the full reactor verification before tagging `v0.2.0`.

## M4 - v0.3.0 Embeddings and RAG Foundation

Status: in progress

Intent:

- Expand the SDK from reliable text generation into the backend primitives needed for basic
  retrieval-augmented generation workflows.
- Keep vector storage and document-ingestion policy application-owned instead of prematurely
  freezing a broad RAG framework API.

Focus areas:

- A typed synchronous Embeddings API with batch input, request-level model selection, optional
  dimensions, token usage, safe diagnostics, and OpenAI-compatible transport support.
- `FakeAiClient`, Spring Boot injection, lifecycle telemetry, and optional live compatibility probes
  for embedding calls.
- A compiled Spring Boot example that embeds a synthetic corpus, performs in-memory cosine retrieval,
  and builds a grounded chat request without introducing a vector-store abstraction.
- Prepare and verify the `0.3.0-SNAPSHOT` development line before a later GitHub Release.

Completion criteria:

- Core, starter, fake-client, telemetry, compatibility, and example coverage pass the full Java 17
  reactor verification.
- Documentation distinguishes OpenAI-compatible SDK evidence, preset-only providers, and Anthropic's
  intentionally unsupported Embeddings capability.
- Release preparation can remove `-SNAPSHOT` and publish `v0.3.0` without additional feature work.

## Later Candidate Milestones

### Spring Boot Production Integration

Deepen Spring Boot integration around configuration, dependency injection, actuator-style visibility,
and application-level examples. The goal is to make a backend service demo feel like the natural
first adoption path.

### AI Testing Toolkit

Turn the in-memory fake into a stronger testing story for AI-integrated Java services. Candidate
areas include request assertions, scripted failures, structured output fixtures, tool-call fixtures,
streaming edge cases, and prompt regression examples.

### Provider Compatibility and Routing

Add provider modules, presets, fallback, or routing only when they provide real user value beyond the
existing OpenAI-compatible path. Compatibility matrices and tests should come before broad support
claims.

### Production Control Plane

Continue hardening timeout, cancellation, retry, streaming, error classification, metrics, tracing,
redaction, and release verification so production behavior remains the project's main advantage.
