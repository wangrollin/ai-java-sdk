# Testing AI-Integrated Application Code

`FakeAiClient` lets application tests exercise the SDK's `AiChatClient`, `AiResponseClient`, and
`AiEmbeddingClient`
contracts without API keys, network access, or provider availability. Configure outcomes in expected
call order, pass the fake through the narrow client interface used by the application, and assert the
requests recorded after the application code runs.

The examples below use JUnit 5. A compilable framework-neutral version lives in
`core/src/examples/java/io/wangrollin/ai/examples/FakeAiClientExample.java` and is checked by the
normal Maven build.

## Assert Embedding Batches and Retrieval Inputs

Embedding workflows use the same ordered-outcome model. Configure a complete batch with
`embeddingResult(...)` or a convenient single vector with `embeddingVector(...)`, then inspect
`embeddingRequests()` to verify document batching, model selection, dimensions, and query inputs.

```java
FakeAiClient fake = FakeAiClient.builder()
    .embeddingVector(0.1, 0.2, 0.3)
    .build();

fake.embed(EmbeddingRequest.builder()
    .model("text-embedding-test")
    .input("synthetic document")
    .build());

assertEquals("text-embedding-test", fake.embeddingRequests().get(0).model());
```

The compiled `examples/knowledge-base-rag` module demonstrates batch indexing, query embedding,
cosine retrieval, and grounded prompt assertions without a provider account or vector database.

## Assert Prompt Assembly and Structured Output

Test the request produced by the application rather than only checking the returned model text. This
catches prompt regressions, dropped schema hints, and accidental model overrides without coupling the
test to provider JSON.

```java
ChatResponseFormat format = ChatResponseFormat.jsonSchema("ticket_triage", schemaJson);
FakeAiClient fake = FakeAiClient.builder()
    .chatResponse("{\"queue\":\"billing\"}")
    .build();

serviceUsing(fake).triage("The invoice export is failing.");

ChatRequest recorded = fake.requests().get(0);
assertEquals(format, recorded.responseFormat());
assertEquals("The invoice export is failing.", recorded.messages().get(1).content());
```

For Spring Boot service and controller examples, see the tests under
`examples/support-ticket-triage/src/test`.

## Return Tool Calls Without Executing Real Tools

The fake returns a complete `ChatResponse`, so an application can test its trusted tool dispatcher
independently from the provider. Tool execution remains application-owned; the SDK only transports
definitions, calls, and tool-result messages.

```java
ChatToolCall call = new ChatToolCall(
    "call-1", "lookup_account", "{\"accountId\":\"account-42\"}");
FakeAiClient fake = FakeAiClient.builder()
    .chatResponse(new ChatResponse("", null, null, "tool_calls", null, List.of(call)))
    .build();

ChatResponse response = fake.chat(requestAdvertisingLookupAccount);

assertEquals(List.of(call), response.toolCalls());
assertEquals("lookup_account", fake.requests().get(0).tools().get(0).name());
```

Use fixed, synthetic arguments in repository tests. Do not copy real customer prompts, model output,
credentials, or production payloads into fixtures.

## Script Failures and Application Fallback

Outcomes are consumed in configuration order. This makes retries and fallback owned by application
code deterministic without teaching the fake provider-specific retry behavior.

```java
FakeAiClient fake = FakeAiClient.builder()
    .chatFailure(new AiException("primary model unavailable"))
    .chatResponse("fallback response")
    .build();

String result = applicationFallbackUsing(fake).generate(request);

assertEquals("fallback response", result);
assertEquals(2, fake.requests().size());
```

Use `chatFailure(...)` and `responseFailure(...)` for synchronous calls. Use `streamFailure(...)` and
`responseStreamFailure(...)` when the application must handle a failure before a response stream is
returned.

## Test Stream Consumption Failures

Opening a stream and consuming it are separate failure points. Malformed-event outcomes return a
stream successfully and raise `AiException` only when application code starts iteration.

```java
FakeAiClient fake = FakeAiClient.builder()
    .streamMalformedEvent("{\"choices\":[")
    .build();

try (ChatStream stream = fake.stream(request)) {
    AiException failure = assertThrows(AiException.class, () -> stream.iterator().hasNext());
    assertEquals("Failed to parse chat stream event", failure.getMessage());
}
```

The corresponding Responses API helpers are `responseStreamMalformedEvent(...)` and
`responseStreamDeltas(...)`. Always close streams with try-with-resources so tests exercise the same
resource lifecycle expected in production code.

## Verification

Run the focused fake-client tests with:

```shell
mvn -q -pl core -Dtest=FakeAiClientTest test
```

Run the complete project verification with:

```shell
mvn verify
```

Some core transport tests bind an in-process HTTP server to an ephemeral local port. Restricted
sandboxes may require permission for that local bind even though no external AI provider is called.
