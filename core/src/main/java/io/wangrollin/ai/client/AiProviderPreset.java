package io.wangrollin.ai.client;

/**
 * Convenience presets for providers that expose an OpenAI-compatible protocol.
 *
 * <p>A preset is configuration sugar, not a promise that every provider supports
 * every OpenAI feature. It selects the SDK wire protocol and a documented base
 * URL while keeping credentials and model names in application configuration.
 */
public enum AiProviderPreset {
    /**
     * OpenAI's native OpenAI-compatible API.
     */
    OPENAI(AiProvider.OPENAI_COMPATIBLE, AiClient.DEFAULT_BASE_URL),

    /**
     * DeepSeek's OpenAI-compatible API endpoint.
     */
    DEEPSEEK(AiProvider.OPENAI_COMPATIBLE, "https://api.deepseek.com"),

    /**
     * Alibaba Cloud DashScope Qwen OpenAI-compatible endpoint.
     */
    QWEN(AiProvider.OPENAI_COMPATIBLE, "https://dashscope.aliyuncs.com/compatible-mode/v1"),

    /**
     * Moonshot AI Kimi OpenAI-compatible endpoint.
     */
    MOONSHOT(AiProvider.OPENAI_COMPATIBLE, "https://api.moonshot.cn/v1"),

    /**
     * Zhipu AI GLM OpenAI-compatible endpoint.
     */
    ZHIPU(AiProvider.OPENAI_COMPATIBLE, "https://open.bigmodel.cn/api/paas/v4"),

    /**
     * OpenRouter's OpenAI-compatible model routing endpoint.
     */
    OPENROUTER(AiProvider.OPENAI_COMPATIBLE, "https://openrouter.ai/api/v1"),

    /**
     * Anthropic's native Claude Messages API endpoint.
     */
    ANTHROPIC(AiProvider.ANTHROPIC, "https://api.anthropic.com/v1");

    private final AiProvider provider;
    private final String baseUrl;

    AiProviderPreset(AiProvider provider, String baseUrl) {
        this.provider = provider;
        this.baseUrl = baseUrl;
    }

    /**
     * Returns the provider protocol used by this preset.
     *
     * @return SDK provider protocol
     */
    public AiProvider provider() {
        return provider;
    }

    /**
     * Returns the documented provider base URL.
     *
     * @return base URL without credentials or model information
     */
    public String baseUrl() {
        return baseUrl;
    }
}
