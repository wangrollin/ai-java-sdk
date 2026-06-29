package io.wangrollin.ai;

/**
 * Base unchecked exception for SDK call failures.
 */
public class AiException extends RuntimeException {
    /**
     * Creates an SDK exception with a user-facing diagnostic message.
     *
     * @param message failure summary
     */
    public AiException(String message) {
        super(message);
    }

    /**
     * Creates an SDK exception that preserves the underlying cause.
     *
     * @param message failure summary
     * @param cause underlying failure
     */
    public AiException(String message, Throwable cause) {
        super(message, cause);
    }
}
