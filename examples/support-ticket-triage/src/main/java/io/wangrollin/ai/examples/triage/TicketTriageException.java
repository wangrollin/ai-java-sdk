package io.wangrollin.ai.examples.triage;

/**
 * Application-level exception used when the AI response cannot be converted
 * into the structured triage contract expected by backend routing code.
 */
public class TicketTriageException extends RuntimeException {
    public TicketTriageException(String message, Throwable cause) {
        super(message, cause);
    }
}
