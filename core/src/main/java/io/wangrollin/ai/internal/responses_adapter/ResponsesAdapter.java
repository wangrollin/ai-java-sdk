package io.wangrollin.ai.internal.responses_adapter;

import io.wangrollin.ai.internal.openai.OpenAiResponseCodec;
import io.wangrollin.ai.internal.protocol.AiStreamEvent;
import io.wangrollin.ai.internal.protocol.AiTurnRequest;
import io.wangrollin.ai.internal.protocol.AiTurnResult;

/**
 * Adapter between the neutral turn protocol and OpenAI-compatible Responses API.
 */
public final class ResponsesAdapter {
    public static final String PATH = OpenAiResponseCodec.RESPONSES_PATH;

    private final OpenAiResponseCodec codec = new OpenAiResponseCodec();

    /**
     * Serializes a neutral turn request to a Responses API request body.
     *
     * @param request neutral request
     * @param defaultModel fallback model
     * @param stream whether to enable streaming
     * @return JSON request body
     */
    public String serializeRequest(AiTurnRequest request, String defaultModel, boolean stream) {
        return codec.serializeRequest(request, defaultModel, stream);
    }

    /**
     * Parses a Responses API response body.
     *
     * @param body JSON response body
     * @return neutral turn result
     */
    public AiTurnResult parseTurnResult(String body) {
        return codec.parseTurnResult(body);
    }

    /**
     * Parses one Responses API stream event.
     *
     * @param data raw SSE data value
     * @return neutral stream event
     */
    public AiStreamEvent parseStreamEvent(String data) {
        return codec.parseStreamEvent(data);
    }
}
