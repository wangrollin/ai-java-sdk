package io.wangrollin.ai.examples.triage;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.wangrollin.ai.chat.ChatRequest;
import io.wangrollin.ai.error.AiException;
import io.wangrollin.ai.testing.FakeAiClient;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class SupportTicketTriageControllerTest {
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void exposesTriageWorkflowThroughHttpBoundary() throws Exception {
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
        MockMvc mvc = mvc(fakeClient);

        MvcResult response = mvc.perform(post("/support-tickets/triage")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(ticket())))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andReturn();

        assertThat(objectMapper.readValue(
                response.getResponse().getContentAsString(),
                TriageResult.class))
                .isEqualTo(new TriageResult(
                        "billing",
                        "high",
                        "Enterprise customer cannot download invoices.",
                        "billing-operations"));

        ChatRequest request = fakeClient.requests().getFirst();
        assertThat(request.responseFormat()).isEqualTo(TriageSchemas.SUPPORT_TICKET_TRIAGE);
        assertThat(request.messages().get(1).content()).contains("ticket-123");
    }

    @Test
    void mapsInvalidStructuredOutputWithoutLeakingModelText() throws Exception {
        FakeAiClient fakeClient = FakeAiClient.builder()
                .chatResponse("not-json-with-private-model-output")
                .build();

        MvcResult response = mvc(fakeClient).perform(post("/support-tickets/triage")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(ticket())))
                .andExpect(status().isBadGateway())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andReturn();

        assertThat(response.getResponse().getContentAsString())
                .contains("\"error\":\"invalid_ai_response\"")
                .doesNotContain("not-json-with-private-model-output");
    }

    @Test
    void mapsSdkFailuresToServiceUnavailable() throws Exception {
        FakeAiClient fakeClient = FakeAiClient.builder()
                .chatFailure(new AiException("provider timeout with internal detail"))
                .build();

        MvcResult response = mvc(fakeClient).perform(post("/support-tickets/triage")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(ticket())))
                .andExpect(status().isServiceUnavailable())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andReturn();

        assertThat(response.getResponse().getContentAsString())
                .contains("\"error\":\"ai_provider_unavailable\"")
                .doesNotContain("provider timeout with internal detail");
    }

    private MockMvc mvc(FakeAiClient fakeClient) {
        SupportTicketTriageService service = new SupportTicketTriageService(fakeClient, objectMapper);
        return MockMvcBuilders
                .standaloneSetup(new SupportTicketTriageController(service))
                .setControllerAdvice(new TriageHttpExceptionHandler())
                .build();
    }

    private static SupportTicket ticket() {
        return new SupportTicket(
                "ticket-123",
                "Invoice export is failing",
                "The invoice export endpoint returns 500 for every admin user.",
                "enterprise");
    }
}
