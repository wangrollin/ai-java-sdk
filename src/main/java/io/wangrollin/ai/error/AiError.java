package io.wangrollin.ai.error;

/**
 * Structured provider error details when an HTTP response body exposes them.
 *
 * @param message optional provider error message
 * @param type optional provider error category
 * @param code optional provider-specific error code
 */
public record AiError(String message, String type, String code) {
}
