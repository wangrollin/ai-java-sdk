# Decisions

This file records durable project decisions that future AI coding agents should preserve unless new project facts justify changing them. Keep entries short, date them, and prefer updating this file when a change introduces an important tradeoff.

## 2026-07-11 - Build a Java-First Production SDK

Decision: `ai-java-sdk` should remain a Java-first, production-oriented SDK rather than a thin HTTP wrapper around model provider APIs.

Context:

- Java backend teams need typed APIs, predictable resource behavior, test support, and operational hooks.
- Calling a model API once is easy; operating it safely in production requires SDK-level structure.

Implications:

- Public APIs should use clear Java types instead of raw maps when the behavior is stable enough to model.
- Reliability, diagnostics, streaming behavior, retries, and testing utilities are core SDK concerns.
- Provider-specific JSON should stay inside adapters unless exposing it is a deliberate compatibility decision.

## 2026-07-11 - Keep OpenAI-Compatible Support as the Default Provider Path

Decision: OpenAI-compatible APIs remain the default implementation path while provider-specific modules are added only through focused adapter boundaries.

Context:

- The current SDK already supports OpenAI-compatible chat completions and Responses API usage.
- Many model services expose OpenAI-compatible protocols, so this path gives broad value without multiplying public APIs too early.

Implications:

- New provider work should first check whether a provider preset is enough.
- Provider-specific capabilities should not reshape common public APIs unless the behavior is broadly useful and stable.
- Adapter tests should protect the provider boundary from accidental wire-format leakage.

## 2026-07-12 - Add Claude Through the Internal Adapter Boundary

Decision: Anthropic Claude support is implemented as a native Messages API adapter behind the existing internal provider boundary.

Context:

- Claude Messages differs from OpenAI Chat Completions and Responses in authentication headers, system-message placement, content blocks, tool-use blocks, and named streaming events.
- The public chat API can represent Claude's basic text, streaming text, and function-tool workflow without adding a second public client shape.
- Claude Messages is not an OpenAI Responses API equivalent, so mapping Responses calls to Claude would create misleading compatibility claims.

Implications:

- `AiChatClient` remains the common surface for OpenAI-compatible chat and Claude Messages.
- `AiResponseClient` stays OpenAI-compatible only unless a provider exposes the Responses API protocol.
- Provider adapters own protocol-specific headers, endpoint paths, JSON payloads, and stream event parsing.

## 2026-07-12 - Use a Neutral Internal Turn Protocol

Decision: Provider adapters should translate through an internal typed content block, tool-call, and
stream-event protocol instead of binding SDK internals to Chat Completions, OpenAI Responses, or
Claude Messages directly.

Context:

- The SDK now supports multiple provider wire shapes with overlapping but different concepts:
  chat messages, Responses typed input items, Claude content blocks, function tools, tool results,
  and named streaming events.
- Public `chat` and `response` APIs remain useful compatibility facades, but using either one as
  the adapter SPI would make the other protocols look like second-class translations.

Implications:

- Public API requests should map into the neutral internal protocol before provider serialization.
- Provider-specific wire details should remain in focused adapters for chat completions, Responses,
  and Claude Messages.
- New provider work should extend the neutral protocol only for stable SDK concepts, not for raw
  provider JSON escape hatches by default.

## 2026-07-11 - Observability Must Be Conservative by Default

Decision: Default lifecycle events, metrics, tracing, and diagnostics must not expose API keys, prompts, model outputs, tool arguments, or raw provider response bodies.

Context:

- AI payloads often contain private user data, business data, or credentials.
- Production SDK users need diagnostics, but the safest default is metadata-only observability.

Implications:

- New telemetry fields must be reviewed for sensitive content before they are added.
- Raw payload diagnostics should remain opt-in and redaction-aware.
- Examples should avoid encouraging unsafe logging patterns.

## 2026-07-11 - Preserve AI Coding Continuity

Decision: Each significant AI coding iteration should update the project state, and important tradeoffs should be recorded here.

Context:

- The project is intended to develop through repeated pure AI coding sessions.
- Without an explicit state handoff, future sessions can waste effort reconstructing current goals or over-optimize local details.

Implications:

- `docs/PROJECT_STATE.md` is the source of truth for the active milestone, risks, and next candidates.
- `docs/ROADMAP.md` holds longer-term direction without becoming a detailed task tracker.
- If a future change makes the current milestone less useful, record the reason and update the milestone rather than continuing by inertia.

## 2026-07-11 - Focus on Java Backend Adoption

Decision: The recommended project position is a production-ready Java and Spring Boot backend AI
integration SDK, not a general agent framework, not an official SDK replacement, and not a race to
list the most providers.

Context:

- The current codebase is strongest around backend integration concerns: safe lifecycle events,
  redacted diagnostics, Micrometer metrics, OpenTelemetry tracing, Spring Boot auto-configuration,
  retries, streaming behavior, provider presets, and in-memory testing.
- Java backend teams are more likely to adopt the project because it reduces production integration
  work than because it exposes every model-provider feature first.
- Provider support has adoption value only when backed by compatibility evidence and stable public
  APIs.

Implications:

- README messaging should lead with safe Java backend adoption, Spring Boot readiness, testability,
  and OpenAI-compatible provider switching.
- Near-term milestones should prioritize a realistic Spring Boot workflow demo, fake-client testing
  guidance, observability examples, and provider compatibility matrices.
- New provider work should avoid broad support claims until chat, streaming, tool calling, JSON
  output, and Responses API behavior are verified.
- Agent runtime abstractions, broad orchestration, and speculative provider-specific public APIs are
  out of scope unless future user evidence justifies them.
