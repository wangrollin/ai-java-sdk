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

## Live Provider Verification

`LiveProviderCompatibilityIT` provides a repeatable, opt-in probe for real provider accounts. Its
name deliberately keeps it out of the normal Surefire selection, so `mvn verify` never requires
credentials or calls an external provider.

Configure the probe through runtime-only environment variables. Keep the real API key in a local
secret manager, shell session, or CI secret store rather than a tracked file:

```shell
AI_COMPAT_PROVIDER_PRESET=DEEPSEEK \
AI_COMPAT_API_KEY="$PROVIDER_API_KEY" \
AI_COMPAT_BASE_URL=https://api.deepseek.com \
AI_COMPAT_MODEL=deepseek-chat \
AI_COMPAT_CAPABILITIES=chat,streaming,tool-calling,json-output \
mvn -q -pl core -Dtest=LiveProviderCompatibilityIT test
```

All five variables are required. `AI_COMPAT_PROVIDER_PRESET` must name an `AiProviderPreset`, and
`AI_COMPAT_CAPABILITIES` accepts a comma-separated subset of:

- `chat`: require a non-empty synchronous chat result.
- `streaming`: consume and close a chat stream, then require non-empty accumulated text.
- `tool-calling`: require the named synthetic function and validate its JSON arguments.
- `json-output`: request a strict synthetic JSON schema and validate the returned object.
- `responses-api`: require a non-empty result through `AiResponseClient`.

The probe uses synthetic inputs, a 30-second timeout, and no retries. It prints only the UTC date,
preset, model, capability names, pass/fail status, and failure class. It does not print the API key,
base URL, prompts, model output, tool arguments, or raw provider payloads. Missing or invalid
configuration fails before the client sends a request.

Run only capabilities the provider is expected to expose. For example, do not select
`responses-api` for `ANTHROPIC`, because the SDK intentionally does not map Claude Messages to the
OpenAI Responses API.

## Live Verification Records

Promote a capability in the matrix to **Live verified** only after a successful probe. Record the
provider-facing model identifier and any relevant limitations; do not paste probe payloads or
provider responses into this repository.

| Date (UTC) | Preset | Model | Verified capabilities | Limitations |
| --- | --- | --- | --- | --- |
