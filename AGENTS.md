# Agent Guidelines

1. Commits must use Conventional Commits in the form `type(scope): commit message`, with a one-line summary only.
2. Suggested plans must be written in Chinese.
3. Code changes should include enough explanatory comments to make the surrounding context, intent, and tradeoffs clear, following the style expected in well-maintained open source projects.
4. Secrets, API keys, tokens, private credentials, and other sensitive values must never be stored in the code repository.
5. Keep individual files preferably under 1000 lines; if a file grows beyond that, consider splitting it into focused, maintainable modules.
6. Organize code into appropriate directories as it is written, rather than concentrating unrelated functionality in a single directory.
