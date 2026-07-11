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

## M2 - Production Hardening and Provider Extensibility

Status: in_progress

Intent:

- Strengthen behavior under production backend conditions.
- Keep the provider boundary clear enough for future provider modules.
- Improve observability and examples without compromising sensitive data safety.

Focus areas:

- Provider adapter documentation and tests.
- Streaming, cancellation, timeout, and retry edge cases.
- Optional metrics and tracing integrations that preserve conservative redaction defaults.
- Release verification, migration notes, changelog discipline, and compiled examples.

## Later Candidate Milestones

### Provider Expansion

Add focused provider modules or presets when they provide real user value beyond the existing OpenAI-compatible path. Prefer narrow adapters over provider-specific public API spread.

### Responses API Depth

Expand Responses API support when the public shape is clear enough to model cleanly. Candidate areas include stored conversation management and additional multimodal or tool behaviors.

### Developer Experience and Adoption

Improve backend workflow examples, Spring Boot usage guidance, migration notes, and release documentation so teams can adopt the SDK with less reverse engineering.

### Release Reliability

Keep release readiness tied to repeatable verification, compiled examples, clean dependency boundaries, and documented compatibility expectations.
