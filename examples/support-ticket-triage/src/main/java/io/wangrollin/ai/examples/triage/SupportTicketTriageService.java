package io.wangrollin.ai.examples.triage;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.wangrollin.ai.chat.ChatMessage;
import io.wangrollin.ai.chat.ChatRequest;
import io.wangrollin.ai.client.AiChatClient;
import org.springframework.stereotype.Service;

import java.util.Objects;

/**
 * Backend service that keeps prompt assembly, structured-output requests, and
 * JSON parsing close together so application tests can verify the full AI
 * contract with {@code FakeAiClient}.
 */
@Service
public class SupportTicketTriageService {
    private static final String SYSTEM_PROMPT = """
            You triage SaaS support tickets for backend operations.
            Return only JSON that matches the requested schema.
            Do not include credentials, raw customer secrets, or unnecessary personal data in the summary.
            """;

    private final AiChatClient aiClient;
    private final ObjectMapper objectMapper;

    public SupportTicketTriageService(AiChatClient aiClient, ObjectMapper objectMapper) {
        this.aiClient = Objects.requireNonNull(aiClient, "aiClient must not be null");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
    }

    public TriageResult triage(SupportTicket ticket) {
        Objects.requireNonNull(ticket, "ticket must not be null");
        String responseText = aiClient.chat(ChatRequest.builder()
                .message(ChatMessage.system(SYSTEM_PROMPT))
                .message(ChatMessage.user(userPrompt(ticket)))
                .responseFormat(TriageSchemas.SUPPORT_TICKET_TRIAGE)
                .temperature(0.1)
                .maxTokens(500)
                .build()).text();

        try {
            return objectMapper.readValue(responseText, TriageResult.class);
        } catch (JsonProcessingException e) {
            throw new TicketTriageException("AI triage response did not match the expected JSON contract", e);
        }
    }

    private static String userPrompt(SupportTicket ticket) {
        return """
                Ticket ID: %s
                Customer tier: %s
                Title: %s
                Body:
                %s
                """.formatted(ticket.id(), ticket.customerTier(), ticket.title(), ticket.body());
    }
}
