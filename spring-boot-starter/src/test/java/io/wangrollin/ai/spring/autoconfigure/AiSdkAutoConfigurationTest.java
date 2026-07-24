package io.wangrollin.ai.spring.autoconfigure;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import io.wangrollin.ai.chat.ChatMessage;
import io.wangrollin.ai.chat.ChatRequest;
import io.wangrollin.ai.client.AiChatClient;
import io.wangrollin.ai.client.AiClient;
import io.wangrollin.ai.client.AiEmbeddingClient;
import io.wangrollin.ai.client.AiResponseClient;
import io.wangrollin.ai.diagnostic.AiPayloadDiagnosticsListener;
import io.wangrollin.ai.embedding.EmbeddingRequest;
import io.wangrollin.ai.diagnostic.AiPayloadRequestEvent;
import io.wangrollin.ai.diagnostic.AiRedactionPolicy;
import io.wangrollin.ai.event.AiEventListener;
import io.wangrollin.ai.event.AiRequestEvent;
import io.wangrollin.ai.testing.FakeAiClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AiSdkAutoConfigurationTest {
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(AiSdkAutoConfiguration.class));

    private HttpServer server;

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void createsAiClientForAllNarrowInterfaces() throws Exception {
        startServer(exchange -> {
            if ("/embeddings".equals(exchange.getRequestURI().getPath())) {
                respond(exchange, 200, """
                        {"data":[{"index":0,"embedding":[0.1,0.2]}]}
                        """);
                return;
            }
            respond(exchange, 200, """
                    {"choices":[{"message":{"content":"ok"}}]}
                    """);
        });

        contextRunner
                .withPropertyValues(requiredProperties())
                .run(context -> {
                    assertTrue(context.containsBean("aiClient"));
                    AiClient client = context.getBean(AiClient.class);
                    assertSame(client, context.getBean(AiChatClient.class));
                    assertSame(client, context.getBean(AiResponseClient.class));
                    assertSame(client, context.getBean(AiEmbeddingClient.class));

                    String text = context.getBean(AiChatClient.class)
                            .chat(ChatRequest.builder()
                                    .message(ChatMessage.user("Hello"))
                                    .build())
                            .text();
                    assertEquals("ok", text);
                    assertEquals(1, context.getBean(AiEmbeddingClient.class)
                            .embed(EmbeddingRequest.builder().input("document").build())
                            .embeddings().size());
                });
    }

    @Test
    void appliesDedicatedEmbeddingModelWithoutChangingChatDefault() throws Exception {
        AtomicReference<String> chatBody = new AtomicReference<>();
        AtomicReference<String> embeddingBody = new AtomicReference<>();
        startServer(exchange -> {
            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            if ("/embeddings".equals(exchange.getRequestURI().getPath())) {
                embeddingBody.set(body);
                respond(exchange, 200, """
                        {"data":[{"index":0,"embedding":[0.1,0.2]}]}
                        """);
                return;
            }
            chatBody.set(body);
            respond(exchange, 200, """
                    {"choices":[{"message":{"content":"ok"}}]}
                    """);
        });

        contextRunner
                .withPropertyValues(requiredProperties())
                .withPropertyValues("ai.sdk.embedding-model=embedding-test")
                .run(context -> {
                    context.getBean(AiChatClient.class).chat(ChatRequest.builder()
                            .message(ChatMessage.user("Hello"))
                            .build());
                    context.getBean(AiEmbeddingClient.class).embed(EmbeddingRequest.builder()
                            .input("document")
                            .build());

                    assertTrue(chatBody.get().contains("\"model\":\"test-model\""));
                    assertTrue(embeddingBody.get().contains("\"model\":\"embedding-test\""));
                });
    }

    @Test
    void failsFastWhenEmbeddingModelIsBlank() {
        contextRunner
                .withPropertyValues(
                        "ai.sdk.api-key=test-key",
                        "ai.sdk.base-url=http://localhost",
                        "ai.sdk.model=test-model",
                        "ai.sdk.embedding-model=")
                .run(context -> assertTrue(hasCauseMessage(
                        context.getStartupFailure(), "ai.sdk.embedding-model must be configured")));
    }

    @Test
    void failsFastWhenRequiredConfigurationIsMissing() {
        contextRunner
                .withPropertyValues(
                        "ai.sdk.api-key=test-key",
                        "ai.sdk.base-url=http://localhost")
                .run(context -> {
                    assertTrue(hasCauseMessage(context.getStartupFailure(), "ai.sdk.model must be configured"));
                });
    }

    @Test
    void bindsExplicitOpenAiCompatibleProvider() throws Exception {
        startServer(exchange -> respond(exchange, 200, """
                {"choices":[{"message":{"content":"ok"}}]}
                """));

        contextRunner
                .withPropertyValues(requiredProperties())
                .withPropertyValues("ai.sdk.provider=openai-compatible")
                .run(context -> {
                    String text = context.getBean(AiChatClient.class)
                            .chat(ChatRequest.builder()
                                    .message(ChatMessage.user("Hello"))
                                    .build())
                            .text();

                    assertEquals("ok", text);
                });
    }

    @Test
    void failsFastWhenBaseUrlIsMissing() {
        contextRunner
                .withPropertyValues(
                        "ai.sdk.api-key=test-key",
                        "ai.sdk.model=test-model",
                        "ai.sdk.provider-preset=deepseek")
                .run(context -> {
                    assertTrue(hasCauseMessage(context.getStartupFailure(), "ai.sdk.base-url must be configured"));
                });
    }

    @Test
    void explicitBaseUrlOverridesProviderPreset() throws Exception {
        startServer(exchange -> respond(exchange, 200, """
                {"choices":[{"message":{"content":"ok"}}]}
                """));

        contextRunner
                .withPropertyValues(requiredProperties())
                .withPropertyValues("ai.sdk.provider-preset=deepseek")
                .run(context -> {
                    String text = context.getBean(AiChatClient.class)
                            .chat(ChatRequest.builder()
                                    .message(ChatMessage.user("Hello"))
                                    .build())
                            .text();

                    assertEquals("ok", text);
                });
    }

    @Test
    void failsFastWhenProviderConfigurationIsInvalid() {
        contextRunner
                .withPropertyValues(
                        "ai.sdk.api-key=test-key",
                        "ai.sdk.model=test-model",
                        "ai.sdk.provider=unknown")
                .run(context -> {
                    assertTrue(hasCauseMessage(context.getStartupFailure(), "ai.sdk.provider"));
                });
    }

    @Test
    void backsOffWhenApplicationProvidesSdkClient() {
        contextRunner
                .withUserConfiguration(UserClientConfiguration.class)
                .withPropertyValues(
                        "ai.sdk.api-key=test-key",
                        "ai.sdk.model=test-model")
                .run(context -> {
                    assertSame(UserClientConfiguration.CLIENT, context.getBean("userChatClient"));
                    assertEquals(1, context.getBeansOfType(AiChatClient.class).size());
                    assertEquals(0, context.getBeansOfType(AiClient.class).size());
                });
    }

    @Test
    void appliesOptionalInfrastructureBeans() throws Exception {
        startServer(exchange -> respond(exchange, 200, """
                {"choices":[{"message":{"content":"ok"}}]}
                """));

        contextRunner
                .withUserConfiguration(ObservedClientConfiguration.class)
                .withPropertyValues(requiredProperties())
                .run(context -> {
                    context.getBean(AiChatClient.class).chat(ChatRequest.builder()
                            .message(ChatMessage.user("Hello"))
                            .build());

                    ObservedClientConfiguration configuration = context.getBean(ObservedClientConfiguration.class);
                    assertEquals(1, configuration.eventStarts.get());
                    assertEquals(1, configuration.payloadRequests.get());
                });
    }

    @Test
    void retryConfigurationControlsTransientStatusRetries() throws Exception {
        AtomicInteger attempts = new AtomicInteger();
        startServer(exchange -> {
            if (attempts.incrementAndGet() == 1) {
                respond(exchange, 503, """
                        {"error":{"message":"temporary","type":"server_error","code":"temporary"}}
                        """);
                return;
            }
            respond(exchange, 200, """
                    {"choices":[{"message":{"content":"ok"}}]}
                    """);
        });

        contextRunner
                .withPropertyValues(requiredProperties())
                .withPropertyValues(
                        "ai.sdk.retry.enabled=true",
                        "ai.sdk.retry.max-attempts=2",
                        "ai.sdk.retry.initial-delay=0ms",
                        "ai.sdk.retry.max-delay=0ms",
                        "ai.sdk.retry.status-codes=503")
                .run(context -> {
                    String text = context.getBean(AiChatClient.class)
                            .chat(ChatRequest.builder()
                                    .message(ChatMessage.user("Hello"))
                                    .build())
                            .text();

                    assertEquals("ok", text);
                    assertEquals(2, attempts.get());
                });
    }

    private String[] requiredProperties() {
        return new String[] {
                "ai.sdk.api-key=test-key",
                "ai.sdk.model=test-model",
                "ai.sdk.base-url=" + baseUrl()
        };
    }

    private String baseUrl() {
        if (server == null) {
            throw new IllegalStateException("test server must be started before building properties");
        }
        return "http://localhost:" + server.getAddress().getPort() + "/";
    }

    private static boolean hasCauseMessage(Throwable failure, String message) {
        Throwable current = failure;
        while (current != null) {
            if (current.getMessage() != null && current.getMessage().contains(message)) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private void startServer(ExchangeHandler handler) throws IOException {
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/", exchange -> {
            try {
                handler.handle(exchange);
            } catch (UncheckedIOException e) {
                throw e.getCause();
            }
        });
        server.start();
    }

    private static void respond(HttpExchange exchange, int statusCode, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(statusCode, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }

    @FunctionalInterface
    private interface ExchangeHandler {
        void handle(HttpExchange exchange) throws IOException;
    }

    @Configuration(proxyBeanMethods = false)
    static class UserClientConfiguration {
        static final FakeAiClient CLIENT = FakeAiClient.builder()
                .chatResponse("user")
                .responseResult("user")
                .build();

        @Bean
        AiChatClient userChatClient() {
            return CLIENT;
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class ObservedClientConfiguration {
        private final AtomicInteger eventStarts = new AtomicInteger();
        private final AtomicInteger payloadRequests = new AtomicInteger();

        @Bean
        AiEventListener aiEventListener() {
            return new AiEventListener() {
                @Override
                public void requestStarted(AiRequestEvent event) {
                    eventStarts.incrementAndGet();
                }
            };
        }

        @Bean
        AiPayloadDiagnosticsListener payloadDiagnosticsListener() {
            return new AiPayloadDiagnosticsListener() {
                @Override
                public void requestPayload(AiPayloadRequestEvent event) {
                    payloadRequests.incrementAndGet();
                }
            };
        }

        @Bean
        AiRedactionPolicy aiRedactionPolicy() {
            return AiRedactionPolicy.defaultPolicy();
        }

        @Bean
        HttpClient httpClient() {
            return HttpClient.newHttpClient();
        }
    }
}
