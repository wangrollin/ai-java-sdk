package io.wangrollin.ai;

/**
 * Receives safe request lifecycle events for diagnostics, metrics, or tracing.
 *
 * <p>Event payloads intentionally exclude API keys, prompts, model outputs,
 * tool arguments, and raw provider bodies so applications can connect this
 * listener to operational systems without accidentally logging sensitive data.
 */
public interface AiEventListener {
    /**
     * Listener that ignores every event.
     */
    AiEventListener NOOP = new AiEventListener() {
    };

    /**
     * Called before one HTTP attempt is sent.
     *
     * @param event safe request metadata
     */
    default void requestStarted(AiRequestEvent event) {
    }

    /**
     * Called after one HTTP attempt succeeds with a 2xx response and is parsed
     * far enough to expose safe response metadata.
     *
     * @param event safe response metadata
     */
    default void requestSucceeded(AiResponseEvent event) {
    }

    /**
     * Called after one HTTP attempt fails, including retryable provider
     * statuses, transport failures, response parsing failures, and stream
     * consumption failures.
     *
     * @param event safe failure metadata
     */
    default void requestFailed(AiFailureEvent event) {
    }
}
