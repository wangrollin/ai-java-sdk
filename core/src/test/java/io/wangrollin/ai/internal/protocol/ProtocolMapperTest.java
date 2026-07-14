package io.wangrollin.ai.internal.protocol;

import io.wangrollin.ai.chat.ChatDelta;
import io.wangrollin.ai.chat.ChatMessage;
import io.wangrollin.ai.chat.ChatRequest;
import io.wangrollin.ai.chat.ChatResponse;
import io.wangrollin.ai.chat.ChatResponseFormat;
import io.wangrollin.ai.chat.ChatTool;
import io.wangrollin.ai.chat.ChatToolCall;
import io.wangrollin.ai.chat.ChatToolChoice;
import io.wangrollin.ai.chat.ChatUsage;
import io.wangrollin.ai.response.ResponseDelta;
import io.wangrollin.ai.response.ResponseInputMessage;
import io.wangrollin.ai.response.ResponseInputPart;
import io.wangrollin.ai.response.ResponseRequest;
import io.wangrollin.ai.response.ResponseResult;
import io.wangrollin.ai.response.ResponseTextFormat;
import io.wangrollin.ai.response.ResponseTool;
import io.wangrollin.ai.response.ResponseToolCall;
import io.wangrollin.ai.response.ResponseUsage;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ProtocolMapperTest {
    @Test
    void mapsChatRequestToNeutralTurn() {
        AiTurnRequest turn = ProtocolMapper.fromChatRequest(ChatRequest.builder()
                .model("chat-model")
                .message(ChatMessage.system("Be precise."))
                .message(ChatMessage.user("Hello"))
                .message(ChatMessage.tool("call-1", "{\"ok\":true}"))
                .temperature(0.2)
                .topP(0.9)
                .maxTokens(128)
                .stopSequence("END")
                .responseFormat(ChatResponseFormat.jsonSchema("answer", "{\"type\":\"object\"}"))
                .tool(ChatTool.function("lookup", "Look up data.", "{\"type\":\"object\"}"))
                .toolChoice(ChatToolChoice.function("lookup"))
                .build());

        assertEquals("chat-model", turn.model());
        assertEquals(3, turn.inputItems().size());
        assertEquals("system", turn.inputItems().get(0).role());
        assertEquals("Hello", turn.inputItems().get(1).content().get(0).text());
        assertEquals(AiInputItem.Kind.TOOL_RESULT, turn.inputItems().get(2).kind());
        assertEquals("call-1", turn.inputItems().get(2).toolCallId());
        assertEquals(0.2, turn.temperature());
        assertEquals(0.9, turn.topP());
        assertEquals(128, turn.maxOutputTokens());
        assertEquals(List.of("END"), turn.stopSequences());
        assertEquals("json_schema", turn.outputFormat().type());
        assertEquals("answer", turn.outputFormat().name());
        assertEquals("lookup", turn.tools().get(0).name());
        assertEquals("function", turn.toolChoice().mode());
    }

    @Test
    void mapsResponseRequestToNeutralTurn() {
        AiTurnRequest turn = ProtocolMapper.fromResponseRequest(ResponseRequest.builder()
                .model("response-model")
                .instructions("Return JSON.")
                .inputMessage(ResponseInputMessage.user(
                        ResponseInputPart.text("Describe."),
                        ResponseInputPart.imageUrl("https://example.com/a.png", ResponseInputPart.ImageDetail.LOW)))
                .functionCallOutput("call-1", "{\"ok\":true}")
                .textFormat(ResponseTextFormat.jsonSchema("answer", "{\"type\":\"object\"}"))
                .tool(ResponseTool.function("lookup", "Look up data.", "{\"type\":\"object\"}", true))
                .previousResponseId("resp-1")
                .background(true)
                .build());

        assertEquals("response-model", turn.model());
        assertEquals("Return JSON.", turn.instructions());
        assertEquals(2, turn.inputItems().size());
        assertEquals("Describe.", turn.inputItems().get(0).content().get(0).text());
        assertEquals("https://example.com/a.png", turn.inputItems().get(0).content().get(1).imageUrl());
        assertEquals("low", turn.inputItems().get(0).content().get(1).detail());
        assertEquals(AiInputItem.Kind.TOOL_RESULT, turn.inputItems().get(1).kind());
        assertEquals("json_schema", turn.outputFormat().type());
        assertEquals("lookup", turn.tools().get(0).name());
        assertEquals(true, turn.tools().get(0).strict());
        assertEquals("resp-1", turn.previousResponseId());
        assertEquals(true, turn.background());
    }

    @Test
    void mapsNeutralResultsBackToPublicTypes() {
        AiTurnResult result = new AiTurnResult(
                "Done",
                "id-1",
                "model-1",
                "stop",
                "completed",
                new AiUsage(1, 2, 3),
                List.of(new AiToolCall("item-1", "call-1", "lookup", "{\"id\":1}")));

        assertEquals(new ChatResponse(
                "Done",
                "id-1",
                "model-1",
                "stop",
                new ChatUsage(1, 2, 3),
                List.of(new ChatToolCall("item-1", "lookup", "{\"id\":1}"))),
                ProtocolMapper.toChatResponse(result));
        assertEquals(new ResponseResult(
                "Done",
                "id-1",
                "model-1",
                "completed",
                new ResponseUsage(1, 2, 3),
                List.of(new ResponseToolCall("item-1", "call-1", "lookup", "{\"id\":1}"))),
                ProtocolMapper.toResponseResult(result));
    }

    @Test
    void mapsNeutralStreamEventsBackToPublicDeltas() {
        AiStreamEvent event = new AiStreamEvent(
                "Hel",
                true,
                "stop",
                List.of(new AiToolCall("item-1", "call-1", "lookup", "{\"id\"")));

        assertEquals(new ChatDelta(
                "Hel",
                "stop",
                List.of(new ChatToolCall("item-1", "lookup", "{\"id\""))),
                ProtocolMapper.toChatDelta(event));
        assertEquals(new ResponseDelta(
                "Hel",
                true,
                List.of(new ResponseToolCall("item-1", "call-1", "lookup", "{\"id\""))),
                ProtocolMapper.toResponseDelta(event));
    }
}
