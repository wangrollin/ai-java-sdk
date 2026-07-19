package io.wangrollin.ai.compatibility;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.wangrollin.ai.chat.ChatDelta;
import io.wangrollin.ai.chat.ChatMessage;
import io.wangrollin.ai.chat.ChatRequest;
import io.wangrollin.ai.chat.ChatResponse;
import io.wangrollin.ai.chat.ChatResponseFormat;
import io.wangrollin.ai.chat.ChatStream;
import io.wangrollin.ai.chat.ChatTool;
import io.wangrollin.ai.chat.ChatToolCall;
import io.wangrollin.ai.chat.ChatToolChoice;
import io.wangrollin.ai.client.AiClient;
import io.wangrollin.ai.client.AiProviderPreset;
import io.wangrollin.ai.client.RetryPolicy;
import io.wangrollin.ai.embedding.EmbeddingRequest;
import io.wangrollin.ai.embedding.EmbeddingResult;
import io.wangrollin.ai.response.ResponseRequest;
import io.wangrollin.ai.response.ResponseResult;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Opt-in compatibility probes for real provider accounts.
 *
 * <p>The {@code *IT} suffix deliberately keeps this class out of the normal Surefire test
 * selection. Maintainers must name it explicitly and supply runtime-only configuration before any
 * external request is made. Probe output is metadata-only so credentials, endpoints, prompts,
 * responses, and tool arguments do not become build logs or compatibility evidence by accident.
 */
class LiveProviderCompatibilityIT {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(30);
    private static final String TOOL_NAME = "compatibility_probe";
    private static final String STRUCTURED_OUTPUT_SCHEMA = """
            {
              "type": "object",
              "properties": {
                "status": { "type": "string", "enum": ["ok"] }
              },
              "required": ["status"],
              "additionalProperties": false
            }
            """;
    private static final String TOOL_PARAMETERS_SCHEMA = """
            {
              "type": "object",
              "properties": {
                "probe": { "type": "string", "enum": ["compatibility"] }
              },
              "required": ["probe"],
              "additionalProperties": false
            }
            """;

    @Test
    void verifiesSelectedLiveProviderCapabilities() {
        VerificationConfig config = VerificationConfig.fromEnvironment();
        AiClient client = AiClient.builder()
                .apiKey(config.apiKey())
                .baseUrl(config.baseUrl())
                .providerPreset(config.providerPreset())
                .defaultModel(config.model())
                // Live probes should fail quickly and predictably instead of multiplying cost with retries.
                .timeout(REQUEST_TIMEOUT)
                .retryPolicy(RetryPolicy.none())
                .build();

        List<CapabilityResult> results = new ArrayList<>();
        for (Capability capability : config.capabilities()) {
            results.add(runProbe(capability, client));
        }

        printSafeSummary(config, results);
        List<CapabilityResult> failures = results.stream()
                .filter(result -> !result.passed())
                .toList();
        assertTrue(failures.isEmpty(), () -> "Live compatibility probes failed: " + failures.stream()
                .map(result -> result.capability().externalName() + "=" + result.failureType())
                .toList());
    }

    private static CapabilityResult runProbe(Capability capability, AiClient client) {
        try {
            switch (capability) {
                case CHAT -> verifyChat(client);
                case STREAMING -> verifyStreaming(client);
                case TOOL_CALLING -> verifyToolCalling(client);
                case JSON_OUTPUT -> verifyJsonOutput(client);
                case RESPONSES_API -> verifyResponsesApi(client);
                case EMBEDDINGS -> verifyEmbeddings(client);
            }
            return CapabilityResult.passed(capability);
        } catch (Exception | AssertionError failure) {
            // Do not retain the exception or its message: provider errors can include raw response bodies.
            return CapabilityResult.failed(capability, failure.getClass().getSimpleName());
        }
    }

    private static void verifyChat(AiClient client) {
        ChatResponse response = client.chat(ChatRequest.builder()
                .message(ChatMessage.user("Reply with a short synthetic compatibility acknowledgement."))
                .build());

        assertFalse(response.text().isBlank(), "Chat response must contain text");
    }

    private static void verifyStreaming(AiClient client) {
        StringBuilder text = new StringBuilder();
        try (ChatStream stream = client.stream(ChatRequest.builder()
                .message(ChatMessage.user("Reply with a short synthetic streaming acknowledgement."))
                .build())) {
            for (ChatDelta delta : stream) {
                text.append(delta.text());
            }
        }

        assertFalse(text.toString().isBlank(), "Streaming response must contain text");
    }

    private static void verifyToolCalling(AiClient client) throws Exception {
        ChatResponse response = client.chat(ChatRequest.builder()
                .message(ChatMessage.user("Call the compatibility_probe function with probe set to compatibility."))
                .tool(ChatTool.function(
                        TOOL_NAME,
                        "Returns a synthetic compatibility marker without accessing external data.",
                        TOOL_PARAMETERS_SCHEMA))
                .toolChoice(ChatToolChoice.function(TOOL_NAME))
                .build());

        assertEquals(1, response.toolCalls().size(), "Provider must return exactly one tool call");
        ChatToolCall toolCall = response.toolCalls().get(0);
        assertEquals(TOOL_NAME, toolCall.name(), "Provider must call the requested function");
        JsonNode arguments = OBJECT_MAPPER.readTree(toolCall.argumentsJson());
        assertTrue(arguments.isObject(), "Tool arguments must be a JSON object");
        assertEquals("compatibility", arguments.path("probe").asText(), "Tool arguments must match the probe");
    }

    private static void verifyJsonOutput(AiClient client) throws Exception {
        ChatResponse response = client.chat(ChatRequest.builder()
                .message(ChatMessage.system("Return only JSON matching the supplied schema."))
                .message(ChatMessage.user("Return the synthetic compatibility status."))
                .responseFormat(ChatResponseFormat.jsonSchema(
                        "compatibility_status",
                        STRUCTURED_OUTPUT_SCHEMA))
                .build());

        JsonNode output = OBJECT_MAPPER.readTree(response.text());
        assertTrue(output.isObject(), "Structured output must be a JSON object");
        assertEquals("ok", output.path("status").asText(), "Structured output must match the probe schema");
    }

    private static void verifyResponsesApi(AiClient client) {
        ResponseResult result = client.respond(ResponseRequest.builder()
                .input("Reply with a short synthetic Responses API compatibility acknowledgement.")
                .build());

        assertFalse(result.text().isBlank(), "Responses API result must contain text");
    }

    private static void verifyEmbeddings(AiClient client) {
        EmbeddingResult result = client.embed(EmbeddingRequest.builder()
                .input("Synthetic compatibility embedding input.")
                .build());

        assertEquals(1, result.embeddings().size(), "Provider must return one vector for one input");
        assertFalse(result.embeddings().get(0).vector().isEmpty(), "Embedding vector must not be empty");
    }

    private static void printSafeSummary(VerificationConfig config, List<CapabilityResult> results) {
        System.out.printf(
                "compatibility-verification date=%s preset=%s model=%s%n",
                LocalDate.now(ZoneOffset.UTC),
                config.providerPreset(),
                config.model());
        for (CapabilityResult result : results) {
            if (result.passed()) {
                System.out.printf("capability=%s status=PASS%n", result.capability().externalName());
            } else {
                System.out.printf(
                        "capability=%s status=FAIL failure=%s%n",
                        result.capability().externalName(),
                        result.failureType());
            }
        }
    }

    private enum Capability {
        CHAT("chat"),
        STREAMING("streaming"),
        TOOL_CALLING("tool-calling"),
        JSON_OUTPUT("json-output"),
        RESPONSES_API("responses-api"),
        EMBEDDINGS("embeddings");

        private final String externalName;

        Capability(String externalName) {
            this.externalName = externalName;
        }

        String externalName() {
            return externalName;
        }

        static Capability parse(String value) {
            return Arrays.stream(values())
                    .filter(capability -> capability.externalName.equals(value))
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException(
                            "Unsupported AI_COMPAT_CAPABILITIES value '" + value + "'; allowed values: "
                                    + Arrays.stream(values()).map(Capability::externalName).toList()));
        }
    }

    private record CapabilityResult(Capability capability, boolean passed, String failureType) {
        static CapabilityResult passed(Capability capability) {
            return new CapabilityResult(capability, true, null);
        }

        static CapabilityResult failed(Capability capability, String failureType) {
            return new CapabilityResult(capability, false, failureType);
        }
    }

    private record VerificationConfig(
            AiProviderPreset providerPreset,
            String apiKey,
            String baseUrl,
            String model,
            List<Capability> capabilities) {
        static VerificationConfig fromEnvironment() {
            String presetValue = requireEnvironment("AI_COMPAT_PROVIDER_PRESET");
            AiProviderPreset providerPreset;
            try {
                providerPreset = AiProviderPreset.valueOf(presetValue.toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException exception) {
                throw new IllegalArgumentException(
                        "AI_COMPAT_PROVIDER_PRESET must be one of " + Arrays.toString(AiProviderPreset.values()));
            }

            LinkedHashSet<Capability> capabilities = new LinkedHashSet<>();
            for (String value : requireEnvironment("AI_COMPAT_CAPABILITIES").split(",")) {
                String normalized = value.trim().toLowerCase(Locale.ROOT);
                if (normalized.isEmpty()) {
                    throw new IllegalArgumentException("AI_COMPAT_CAPABILITIES must not contain blank values");
                }
                capabilities.add(Capability.parse(normalized));
            }

            return new VerificationConfig(
                    providerPreset,
                    requireEnvironment("AI_COMPAT_API_KEY"),
                    requireEnvironment("AI_COMPAT_BASE_URL"),
                    requireEnvironment("AI_COMPAT_MODEL"),
                    List.copyOf(capabilities));
        }

        private static String requireEnvironment(String name) {
            String value = System.getenv(name);
            if (value == null || value.isBlank()) {
                throw new IllegalArgumentException(name + " must be configured for live compatibility verification");
            }
            return value.trim();
        }
    }
}
