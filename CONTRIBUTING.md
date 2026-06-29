# Contributing to ai-java-sdk

Thank you for taking the time to improve `ai-java-sdk`.

This project is an early-stage, production-oriented Java SDK for backend AI integrations. Contributions should keep the public API clear, typed, observable, and maintainable for Java backend teams.

## Getting Started

Before opening a pull request:

1. Check existing issues and pull requests to avoid duplicate work.
2. Open an issue first for large changes, public API changes, new provider integrations, or breaking changes.
3. Keep each pull request focused on one problem or feature.
4. Include tests and documentation updates when behavior or public APIs change.

## Requirements

- JDK 25
- Maven 3.9+

Run the test suite before submitting a pull request:

```shell
mvn test
```

## Commit Messages

Commits must use Conventional Commits with a one-line summary:

```text
type(scope): summary
```

Examples:

```text
feat(core): add chat client
fix(http): handle empty response body
docs(readme): clarify minimal usage
test(core): cover retry exhaustion
```

Use a clear scope that matches the area being changed, such as `core`, `http`, `streaming`, `docs`, `test`, or `build`.

## Pull Request Standards

A pull request can be merged when:

- The motivation and behavior change are clear.
- The change is small enough to review effectively.
- `mvn test` passes, or any failure is explained.
- New behavior has focused tests.
- Public API changes include documentation updates.
- Breaking changes are explicitly marked and justified.
- At least one maintainer approves the pull request.

## Code Guidelines

- Prefer Java-first APIs over raw maps, provider-specific JSON plumbing, or loosely typed objects.
- Keep public APIs small, predictable, and hard to misuse.
- Treat timeouts, cancellation, retries, resource management, and error handling as part of feature design.
- Avoid logging secrets, prompts, responses, or provider payloads unless the data is explicitly redacted.
- Keep provider-specific details behind stable SDK abstractions where practical.
- Add tests for failure modes, not only successful responses.

## Public API and Compatibility

This project is still early, so APIs may evolve. Even so, contributors should be careful with compatibility:

- Mark breaking changes clearly in the pull request.
- Explain migration impact for users.
- Avoid changing method names, package names, or constructor behavior without a strong reason.
- Prefer additive changes when they keep the API coherent.

## Reporting Security Issues

Please do not open a public issue for vulnerabilities, leaked credentials, or sensitive operational details. Contact the maintainer privately through the repository owner profile and include enough detail to reproduce or evaluate the issue.
