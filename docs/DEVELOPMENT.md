# Development

This project is intended to be developed through small, verifiable AI coding iterations. Use this file as the operational guide for local work, and use `docs/PROJECT_STATE.md` as the handoff between sessions.

## Prerequisites

- JDK 17 or newer
- Maven 3.9+

## Standard Verification

Run the full verification path before considering a code change complete:

```shell
mvn verify
```

`mvn verify` also compiles examples under `core/src/examples/java`, so public usage snippets stay aligned with the current API.

Run the full reactor against the maintained Spring Boot 3.5 compatibility line with:

```shell
mvn -Pspring-boot-3 verify
```

The profile keeps the Spring Boot and Spring Framework versions aligned for the starter and both
Spring examples. CI runs this compatibility path on Java 17 in addition to the default Spring Boot 4
build on Java 17 and Java 25.

For documentation-only changes, at minimum inspect the changed Markdown files and confirm the working tree contains only the intended edits. Run `mvn verify` when the documentation change affects commands, examples, public API descriptions, or release guidance.

## AI Coding Workflow

Before starting a new implementation task:

1. Read `AGENTS.md`, `README.md`, `docs/PROJECT_STATE.md`, `docs/ROADMAP.md`, and `docs/DECISIONS.md`.
2. Identify the current milestone and choose one small, verifiable task that advances it.
3. Prefer work that reduces future uncertainty, improves production behavior, or makes the SDK easier to adopt safely.
4. Avoid broad refactors unless they are required to complete the selected task.

During implementation:

1. Keep the change scoped to the selected task.
2. Follow existing module boundaries and public API style.
3. Add focused tests when behavior changes or risk increases.
4. Keep provider-specific wire details behind adapter boundaries.
5. Preserve conservative redaction defaults for diagnostics, metrics, tracing, and logs.

At the end of the task:

1. Run the relevant verification command and record any command that could not be run.
2. Update `docs/PROJECT_STATE.md` with progress, milestone state, risks, and next candidates when the project state changed.
3. Update `docs/DECISIONS.md` when the task introduced an important technical tradeoff.
4. Leave the working tree limited to intentional changes.

## Safety Rules

- Never commit secrets, API keys, tokens, private credentials, or sensitive payloads.
- Do not log prompts, model outputs, tool arguments, raw provider bodies, or API keys by default.
- Keep public APIs stable and Java-first; expose provider-specific behavior only when the tradeoff is deliberate.
- Prefer small, complete changes over speculative rewrites.
