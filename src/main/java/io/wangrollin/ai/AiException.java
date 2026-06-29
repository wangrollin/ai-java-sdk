package io.wangrollin.ai;

/**
 * Base unchecked exception for SDK call failures.
 */
public class AiException extends RuntimeException {
    private final Integer statusCode;
    private final AiError error;

    /**
     * Creates an SDK exception with a user-facing diagnostic message.
     *
     * @param message failure summary
     */
    public AiException(String message) {
        this(message, null, null, null);
    }

    /**
     * Creates an SDK exception that preserves the underlying cause.
     *
     * @param message failure summary
     * @param cause underlying failure
     */
    public AiException(String message, Throwable cause) {
        this(message, cause, null, null);
    }

    /**
     * Creates an SDK exception with structured provider response metadata.
     *
     * @param message failure summary
     * @param statusCode optional HTTP status code
     * @param error optional structured provider error
     */
    public AiException(String message, Integer statusCode, AiError error) {
        this(message, null, statusCode, error);
    }

    private AiException(String message, Throwable cause, Integer statusCode, AiError error) {
        super(message, cause);
        this.statusCode = statusCode;
        this.error = error;
    }

    /**
     * Returns the HTTP status code for provider response failures.
     *
     * @return optional HTTP status code
     */
    public Integer statusCode() {
        return statusCode;
    }

    /**
     * Returns provider-supplied structured error details when available.
     *
     * @return optional provider error details
     */
    public AiError error() {
        return error;
    }
}
