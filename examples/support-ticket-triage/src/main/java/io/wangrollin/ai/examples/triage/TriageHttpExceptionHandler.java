package io.wangrollin.ai.examples.triage;

import io.wangrollin.ai.error.AiException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Maps AI workflow failures to stable HTTP errors without exposing prompts,
 * model output, provider response bodies, or other sensitive payload data.
 */
@RestControllerAdvice
public class TriageHttpExceptionHandler {
    @ExceptionHandler(TicketTriageException.class)
    @ResponseStatus(HttpStatus.BAD_GATEWAY)
    public ErrorResponse invalidAiResponse() {
        return new ErrorResponse("invalid_ai_response");
    }

    @ExceptionHandler(AiException.class)
    @ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
    public ErrorResponse aiProviderUnavailable() {
        return new ErrorResponse("ai_provider_unavailable");
    }

    public record ErrorResponse(String error) {
    }
}
