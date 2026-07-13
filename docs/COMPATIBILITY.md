# Provider Compatibility

This matrix records the SDK compatibility evidence that exists in this repository. It is not a
claim that every provider preset has been verified against a live provider account.

## Evidence Levels

- **SDK verified**: covered by local adapter, codec, or client tests in this repository.
- **Live verified**: exercised against a real provider account and recorded with the provider,
  model, date, and tested capabilities.
- **Preset only**: the SDK exposes a documented base URL and protocol selection, but this repository
  does not yet include live-provider verification for the listed capability.
- **Not supported**: the SDK intentionally does not expose the capability for that provider
  protocol.
- **Not verified**: the provider may support the capability, but the project has not recorded
  evidence yet.

## Capability Matrix

| Preset | Protocol | Chat | Streaming | Tool calling | JSON output | Responses API |
| --- | --- | --- | --- | --- | --- | --- |
| `OPENAI` | OpenAI-compatible | SDK verified | SDK verified | SDK verified | SDK verified | SDK verified |
| `DEEPSEEK` | OpenAI-compatible | Preset only | Preset only | Not verified | Not verified | Not verified |
| `QWEN` | OpenAI-compatible | Preset only | Preset only | Not verified | Not verified | Not verified |
| `MOONSHOT` | OpenAI-compatible | Preset only | Preset only | Not verified | Not verified | Not verified |
| `ZHIPU` | OpenAI-compatible | Preset only | Preset only | Not verified | Not verified | Not verified |
| `OPENROUTER` | OpenAI-compatible | Preset only | Preset only | Not verified | Not verified | Not verified |
| `ANTHROPIC` | Claude Messages API | SDK verified | SDK verified | SDK verified | Not supported | Not supported |

## Notes

- OpenAI-compatible presets reuse the SDK's OpenAI-compatible adapter. A preset selects the protocol
  and reference base URL; applications still choose credentials, models, and runtime base URLs.
- The local test suite verifies OpenAI-compatible request and response shapes for chat, streaming,
  function tools, structured JSON output, and the OpenAI Responses API. It does not prove that every
  OpenAI-compatible provider accepts every OpenAI feature.
- Anthropic uses the native Claude Messages API adapter. It supports chat, streaming text, and basic
  function-tool calls through `AiChatClient`, but `AiResponseClient` remains OpenAI-compatible only.
- Future live-provider verification should record the provider, model, API date when relevant,
  capabilities tested, and any provider-specific limitations without committing credentials or
  sensitive payloads.
