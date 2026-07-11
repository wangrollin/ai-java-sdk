package io.wangrollin.ai.client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AiProviderPresetTest {
    @Test
    void exposesOpenAiCompatibleProviderEndpoints() {
        assertEquals(AiProvider.OPENAI_COMPATIBLE, AiProviderPreset.OPENAI.provider());
        assertEquals(AiClient.DEFAULT_BASE_URL, AiProviderPreset.OPENAI.baseUrl());
        assertEquals("https://api.deepseek.com", AiProviderPreset.DEEPSEEK.baseUrl());
        assertEquals("https://dashscope.aliyuncs.com/compatible-mode/v1", AiProviderPreset.QWEN.baseUrl());
        assertEquals("https://api.moonshot.cn/v1", AiProviderPreset.MOONSHOT.baseUrl());
        assertEquals("https://open.bigmodel.cn/api/paas/v4", AiProviderPreset.ZHIPU.baseUrl());
        assertEquals("https://openrouter.ai/api/v1", AiProviderPreset.OPENROUTER.baseUrl());
    }
}
