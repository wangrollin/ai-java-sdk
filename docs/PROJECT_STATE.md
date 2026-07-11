# Project State

## Long-Term Vision

`ai-java-sdk` is a production-oriented Java SDK for building AI features in backend systems. The project should grow through pure AI coding in small, verifiable steps: each iteration should either improve production reliability, reduce future uncertainty, or make the SDK easier for Java teams to adopt safely.

The long-term goal is not to chase every provider feature immediately. The SDK should preserve a Java-first public API, keep provider-specific wire details behind stable boundaries, and make operational concerns such as retries, streaming, diagnostics, metrics, tracing, and testing support first-class.

## Current Milestone

Name: M2 - Production Hardening and Provider Extensibility

Status: in_progress

Goal:

- Expand the provider boundary carefully while keeping OpenAI-compatible support as the default path.
- Harden timeout, cancellation, retry, streaming, and error behavior under realistic backend usage.
- Keep observability useful without exposing API keys, prompts, model outputs, tool arguments, or raw provider bodies by default.
- Improve developer experience with compiled examples, migration notes, changelog discipline, and clear verification steps.

Completion criteria:

- Provider extension points are documented and covered by focused tests.
- Streaming and cancellation edge cases have compatibility coverage.
- Observability integrations remain optional, dependency-scoped, and redaction-safe by default.
- Release verification is centered on `mvn verify` and a clean working tree.
- Project state, roadmap, development flow, and important decisions are current enough for a new AI coding agent to continue without reconstructing context from scratch.

## Recent Progress

- 2026-07-11: Established long-running AI coding project state docs to make future iterations more deliberate and easier to resume.
- v0.1.0: Completed the initial foundation: synchronous chat, streaming, typed request and response objects, provider errors, configurable client options, retries, and in-memory test support.
- Post-v0.1.0: Added OpenAI-compatible chat and Responses API clients, tool-calling plumbing, structured output hints, image input references, background mode requests, diagnostics, metrics hooks, optional Micrometer and OpenTelemetry support, Spring Boot auto-configuration, provider presets, and compiled examples.

## Known Risks

- Provider-specific features can leak into the public API if extension work is not kept behind clear adapter boundaries.
- Observability can become unsafe if future events include prompts, outputs, raw bodies, API keys, or tool arguments by default.
- Streaming behavior is easy to regress because failures often happen in cancellation, partial output, and resource cleanup paths.
- Documentation can drift from implementation unless examples and verification commands remain part of normal Maven checks.

## Next Candidates

1. Identify the most important streaming and cancellation edge cases that are not yet covered by tests.
2. Document the provider adapter boundary with a minimal guide for adding future provider modules.
3. Add or refresh release-oriented docs such as changelog and migration notes before the next public release.
