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

## 2026-07-13 - Separate Provider Presets from Compatibility Claims

Decision: Provider presets are configuration shortcuts and must not be treated as live-provider
compatibility promises without recorded evidence.

Context:

- The SDK exposes presets for several OpenAI-compatible services, but local tests mostly verify the
  shared OpenAI-compatible adapter rather than every live provider and model combination.
- Java backend adopters need to know whether a capability is SDK-verified, live-provider verified,
  preset-only, unsupported, or still unknown before relying on it in production.

Implications:

- `docs/COMPATIBILITY.md` is the source of truth for provider capability evidence.
- New presets should update the compatibility matrix in the same change.
- Live-provider verification should record capabilities and limitations without committing secrets,
  API keys, prompts, model outputs, or raw provider payloads.

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

## 2026-07-14 - Use Java 17 as the Minimum Runtime Baseline

Decision: Published SDK artifacts target Java 17, and CI verifies the project on both Java 17 and a
current JDK.

Context:

- Requiring Java 25 excluded established Java and Spring Boot environments without providing an
  SDK capability that depended on Java 25.
- The current Spring Boot, JUnit, Micrometer, OpenTelemetry, and Jackson dependencies support Java
  17, and the SDK's language features are available in Java 17.
- The only production source incompatibility was a convenience collection method introduced after
  Java 17, so lowering the baseline did not require changing the public API or runtime behavior.

Implications:

- Production code, tests, and examples must avoid JDK APIs introduced after Java 17 unless the
  minimum baseline is deliberately reconsidered.
- Maven compiles with `--release 17`, and the build environment accepts JDK 17 or newer.
- CI runs the complete verification path on Java 17 to protect the minimum baseline and on a current
  JDK to detect forward-compatibility problems.

## 2026-07-14 - Keep Live Provider Verification Explicit and Credential-Free by Default

Decision: Live-provider compatibility probes must be explicitly selected and configured through the
runtime environment; they are not part of the default Maven verification or CI workflow.

Context:

- Real-provider calls require private credentials, may incur cost, and can become nondeterministic as
  provider models and APIs change.
- Local adapter tests prove SDK wire behavior but cannot establish that every preset and model accepts
  the same capabilities in production.
- Compatibility evidence is useful only when it can be reproduced without storing prompts, outputs,
  credentials, or raw provider payloads.

Implications:

- `LiveProviderCompatibilityIT` is run by name and fails before sending requests when required runtime
  configuration is missing or invalid.
- The probe uses synthetic inputs, a bounded timeout, no retries, and metadata-only result output.
- A capability is marked `Live verified` only after a successful run is recorded with date, preset,
  model, tested capabilities, and limitations.
- Default CI remains deterministic and never depends on provider credentials or availability.

## 2026-07-15 - Use the Next Snapshot Version on Main Between Releases

Decision: After a release is tagged, ongoing development on `main` uses the next planned semantic
version with a `-SNAPSHOT` suffix until release preparation removes the suffix.

Context:

- The `v0.1.0` tag contains the initial core and Spring Boot foundation, while later work added new
  modules and provider capabilities without changing the Maven version from `0.1.0`.
- Reusing a released coordinate for unreleased source can overwrite local Maven artifacts and makes
  README dependency examples look available when the tagged release does not contain them.
- The current post-`0.1.0` changes include additive capabilities and one documented configuration
  migration, so `0.2.0-SNAPSHOT` is the appropriate pre-1.0 development line.

Implications:

- Parent and child POM versions must remain aligned on the same snapshot version during development.
- README and upgrade documentation must distinguish the latest tagged release from capabilities on
  `main`.
- Release preparation should finalize the changelog, remove `-SNAPSHOT`, verify the reactor, and tag
  the exact released commit before the next development version is chosen.
