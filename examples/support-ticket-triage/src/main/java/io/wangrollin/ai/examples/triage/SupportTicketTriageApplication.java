package io.wangrollin.ai.examples.triage;

import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Minimal Spring Boot application marker for the support-ticket triage example.
 *
 * <p>The example is intentionally service-first: production teams can attach the
 * {@link SupportTicketTriageService} to their own controllers, queues, or jobs
 * while keeping provider credentials in external Spring Boot configuration.
 */
@SpringBootApplication
public class SupportTicketTriageApplication {
}
