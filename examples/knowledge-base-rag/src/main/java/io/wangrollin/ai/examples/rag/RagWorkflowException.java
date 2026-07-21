package io.wangrollin.ai.examples.rag;

/** Safe application-level failure for indexing or retrieval contract violations. */
public class RagWorkflowException extends RuntimeException {
    public RagWorkflowException(String message) {
        super(message);
    }

    public RagWorkflowException(String message, Throwable cause) {
        super(message, cause);
    }
}
