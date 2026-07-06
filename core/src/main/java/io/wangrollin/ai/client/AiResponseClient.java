package io.wangrollin.ai.client;

import io.wangrollin.ai.error.AiException;
import io.wangrollin.ai.response.ResponseRequest;
import io.wangrollin.ai.response.ResponseResult;
import io.wangrollin.ai.response.ResponseStream;

/**
 * Minimal Responses API contract for text-first OpenAI-compatible integrations.
 */
public interface AiResponseClient {
    /**
     * Sends a response request and waits for the complete model output.
     *
     * @param request response request to send
     * @return parsed response result
     * @throws AiException when transport, provider, or response parsing fails
     */
    ResponseResult respond(ResponseRequest request);

    /**
     * Sends a response request and returns an iterator over text deltas.
     *
     * @param request response request to send
     * @return stream that must be closed by the caller
     * @throws AiException when the stream cannot be opened
     */
    ResponseStream streamResponse(ResponseRequest request);
}
