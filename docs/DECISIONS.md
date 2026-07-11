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
