package io.wangrollin.ai.diagnostic;

/**
 * Receives opt-in diagnostics for redacted provider payloads.
 *
 * <p>This listener is separate from the safe lifecycle event API because even
 * redacted payloads can be operationally sensitive. The default SDK behavior is
 * {@link #NOOP}; applications must explicitly attach a listener before any
 * redacted request or response payload is emitted.
 */
public interface AiPayloadDiagnosticsListener {
    /**
     * Listener that ignores every diagnostic event.
     */
    AiPayloadDiagnosticsListener NOOP = new AiPayloadDiagnosticsListener() {
    };

    /**
     * Called after an HTTP request body is serialized and redacted, before it is sent.
     *
     * @param event redacted request diagnostic
     */
    default void requestPayload(AiPayloadRequestEvent event) {
    }

    /**
     * Called after a complete non-streaming HTTP response body is read and redacted.
     *
     * @param event redacted response diagnostic
     */
    default void responsePayload(AiPayloadResponseEvent event) {
    }

    /**
     * Called after a complete HTTP error response body is read and redacted.
     *
     * @param event redacted failure diagnostic
     */
    default void failurePayload(AiPayloadFailureEvent event) {
    }
}
