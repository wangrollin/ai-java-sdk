package io.wangrollin.ai.client;

/**
 * Provider wire protocol selected by {@link AiClient}.
 *
 * <p>The SDK keeps provider-specific HTTP paths and JSON shapes behind internal
 * adapters. This public enum is intentionally smaller than that internal
 * boundary: applications choose a supported protocol, while the SDK remains free
 * to evolve adapter implementation details without exposing provider plumbing as
 * a public extension API.
 */
public enum AiProvider {
    /**
     * OpenAI-compatible chat completions and Responses API protocol.
     */
    OPENAI_COMPATIBLE
}
