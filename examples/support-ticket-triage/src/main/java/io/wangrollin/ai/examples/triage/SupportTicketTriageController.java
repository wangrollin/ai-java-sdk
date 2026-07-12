package io.wangrollin.ai.examples.triage;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Objects;

/**
 * Thin HTTP boundary for the example workflow.
 *
 * <p>The controller intentionally delegates all prompt assembly, structured-output
 * configuration, and parsing to {@link SupportTicketTriageService}. Keeping the
 * web layer boring makes it easier for backend teams to copy the service contract
 * into controllers, queue consumers, or jobs without duplicating AI-specific logic.
 */
@RestController
@RequestMapping("/support-tickets")
public class SupportTicketTriageController {
    private final SupportTicketTriageService triageService;

    public SupportTicketTriageController(SupportTicketTriageService triageService) {
        this.triageService = Objects.requireNonNull(triageService, "triageService must not be null");
    }

    @PostMapping("/triage")
    public TriageResult triage(@RequestBody SupportTicket ticket) {
        return triageService.triage(ticket);
    }
}
