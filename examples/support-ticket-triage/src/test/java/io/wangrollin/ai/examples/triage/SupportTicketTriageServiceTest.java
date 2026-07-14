package io.wangrollin.ai.examples.triage;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.wangrollin.ai.chat.ChatRequest;
import io.wangrollin.ai.error.AiException;
import io.wangrollin.ai.testing.FakeAiClient;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

class SupportTicketTriageServiceTest {
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void triagesTicketWithStructuredOutputRequest() {
        FakeAiClient fakeClient = FakeAiClient.builder()
                .chatResponse("""
                        {
                          "category": "billing",
                          "priority": "high",
                          "summary": "Enterprise customer cannot download invoices.",
                          "queue": "billing-operations"
                        }
                        """)
                .build();
        SupportTicket ticket = new SupportTicket(
                "ticket-123",
                "Invoice export is failing",
                "The invoice export endpoint returns 500 for every admin user.",
                "enterprise");

        TriageResult result = new SupportTicketTriageService(fakeClient, objectMapper).triage(ticket);

        assertThat(result).isEqualTo(new TriageResult(
                "billing",
                "high",
                "Enterprise customer cannot download invoices.",
                "billing-operations"));
        ChatRequest request = fakeClient.requests().get(0);
        assertThat(request.responseFormat()).isEqualTo(TriageSchemas.SUPPORT_TICKET_TRIAGE);
        assertThat(request.temperature()).isEqualTo(0.1);
        assertThat(request.maxTokens()).isEqualTo(500);
        assertThat(request.messages()).hasSize(2);
        assertThat(request.messages().get(0).content()).contains("Return only JSON");
        assertThat(request.messages().get(1).content())
                .contains("ticket-123")
                .contains("enterprise")
                .contains("Invoice export is failing")
                .contains("endpoint returns 500");
    }

    @Test
    void reportsInvalidStructuredOutputClearly() {
        FakeAiClient fakeClient = FakeAiClient.builder()
                .chatResponse("not-json")
                .build();
        SupportTicket ticket = new SupportTicket("ticket-456", "Bad response", "The model returned text.", "free");

        assertThatExceptionOfType(TicketTriageException.class)
                .isThrownBy(() -> new SupportTicketTriageService(fakeClient, objectMapper).triage(ticket))
                .withMessage("AI triage response did not match the expected JSON contract");
    }

    @Test
    void propagatesSdkFailuresForApplicationRetryOrFallbackPolicy() {
        FakeAiClient fakeClient = FakeAiClient.builder()
                .chatFailure(new AiException("provider unavailable"))
                .build();
        SupportTicket ticket = new SupportTicket("ticket-789", "Timeout", "Provider timed out.", "pro");

        assertThatExceptionOfType(AiException.class)
                .isThrownBy(() -> new SupportTicketTriageService(fakeClient, objectMapper).triage(ticket))
                .withMessage("provider unavailable");
        assertThat(fakeClient.requests()).hasSize(1);
    }
}
