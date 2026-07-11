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

Status: in_progress

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
- Adoption readiness work such as README messaging, migration notes, release notes, and a JDK
  baseline review.

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
