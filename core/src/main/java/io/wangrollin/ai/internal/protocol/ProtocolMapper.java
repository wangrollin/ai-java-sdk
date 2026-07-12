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
import io.wangrollin.ai.response.ResponseFunctionCallOutput;
import io.wangrollin.ai.response.ResponseInputItem;
import io.wangrollin.ai.response.ResponseInputMessage;
import io.wangrollin.ai.response.ResponseInputPart;
import io.wangrollin.ai.response.ResponseRequest;
import io.wangrollin.ai.response.ResponseResult;
import io.wangrollin.ai.response.ResponseTextFormat;
import io.wangrollin.ai.response.ResponseTool;
import io.wangrollin.ai.response.ResponseToolCall;
import io.wangrollin.ai.response.ResponseUsage;

import java.util.List;

/**
 * Lossless mapper between public SDK models and the internal provider-neutral protocol.
 */
public final class ProtocolMapper {
    private ProtocolMapper() {
    }

    public static AiTurnRequest fromChatRequest(ChatRequest request) {
        return new AiTurnRequest(
                request.model(),
                null,
                request.messages().stream().map(ProtocolMapper::fromChatMessage).toList(),
                null,
                request.temperature(),
                request.topP(),
                request.maxTokens(),
                request.stopSequences(),
                fromChatOutputFormat(request.responseFormat()),
                null,
                null,
                request.tools().stream().map(ProtocolMapper::fromChatTool).toList(),
                fromChatToolChoice(request.toolChoice()));
    }

    public static AiTurnRequest fromResponseRequest(ResponseRequest request) {
        return new AiTurnRequest(
                request.model(),
                request.input(),
                request.inputItems().stream().map(ProtocolMapper::fromResponseInputItem).toList(),
                request.instructions(),
                request.temperature(),
                request.topP(),
                request.maxOutputTokens(),
                List.of(),
                fromResponseOutputFormat(request.textFormat()),
                request.previousResponseId(),
                request.background(),
                request.tools().stream().map(ProtocolMapper::fromResponseTool).toList(),
                null);
    }

    public static ChatResponse toChatResponse(AiTurnResult result) {
        return new ChatResponse(
                result.text(),
                result.id(),
                result.model(),
                result.finishReason(),
                toChatUsage(result.usage()),
                result.toolCalls().stream().map(ProtocolMapper::toChatToolCall).toList());
    }

    public static ResponseResult toResponseResult(AiTurnResult result) {
        return new ResponseResult(
                result.text(),
                result.id(),
                result.model(),
                result.status(),
                toResponseUsage(result.usage()),
                result.toolCalls().stream().map(ProtocolMapper::toResponseToolCall).toList());
    }

    public static ChatDelta toChatDelta(AiStreamEvent event) {
        return new ChatDelta(
                event.textDelta(),
                event.finishReason(),
                event.toolCalls().stream().map(ProtocolMapper::toChatToolCall).toList());
    }

    public static ResponseDelta toResponseDelta(AiStreamEvent event) {
        return new ResponseDelta(
                event.textDelta(),
                event.done(),
                event.toolCalls().stream().map(ProtocolMapper::toResponseToolCall).toList());
    }

    private static AiInputItem fromChatMessage(ChatMessage message) {
        if ("tool".equals(message.role())) {
            return AiInputItem.toolResult(message.toolCallId(), message.content());
        }
        return AiInputItem.message(message.role(), List.of(AiContentBlock.text(message.content())));
    }

    private static AiInputItem fromResponseInputItem(ResponseInputItem item) {
        if (item instanceof ResponseInputMessage message) {
            return AiInputItem.message(message.role(), message.content().stream()
                    .map(ProtocolMapper::fromResponseInputPart)
                    .toList());
        }
        if (item instanceof ResponseFunctionCallOutput output) {
            return AiInputItem.toolResult(output.callId(), output.output());
        }
        throw new IllegalArgumentException("Unsupported response input item: " + item.getClass().getName());
    }

    private static AiContentBlock fromResponseInputPart(ResponseInputPart part) {
        return switch (part.kind()) {
            case TEXT -> AiContentBlock.text(part.text());
            case IMAGE_URL -> AiContentBlock.imageUrl(part.imageUrl(), part.detailValue());
            case IMAGE_FILE_ID -> AiContentBlock.imageFileId(part.fileId(), part.detailValue());
        };
    }

    private static AiToolSpec fromChatTool(ChatTool tool) {
        return new AiToolSpec(tool.name(), tool.description(), tool.parametersJson(), null);
    }

    private static AiToolSpec fromResponseTool(ResponseTool tool) {
        return new AiToolSpec(tool.name(), tool.description(), tool.parametersJson(), tool.strict());
    }

    private static AiToolChoice fromChatToolChoice(ChatToolChoice toolChoice) {
        if (toolChoice == null) {
            return null;
        }
        return new AiToolChoice(toolChoice.mode(), toolChoice.functionName());
    }

    private static AiOutputFormat fromChatOutputFormat(ChatResponseFormat responseFormat) {
        if (responseFormat == null) {
            return null;
        }
        return new AiOutputFormat(
                responseFormat.type(),
                responseFormat.jsonSchemaName(),
                null,
                responseFormat.jsonSchema(),
                responseFormat.strict());
    }

    private static AiOutputFormat fromResponseOutputFormat(ResponseTextFormat textFormat) {
        if (textFormat == null) {
            return null;
        }
        return new AiOutputFormat(
                textFormat.type(),
                textFormat.jsonSchemaName(),
                textFormat.jsonSchemaDescription(),
                textFormat.jsonSchema(),
                textFormat.strict());
    }

    private static ChatToolCall toChatToolCall(AiToolCall toolCall) {
        return new ChatToolCall(toolCall.id(), toolCall.name(), toolCall.argumentsJson());
    }

    private static ResponseToolCall toResponseToolCall(AiToolCall toolCall) {
        String callId = toolCall.callId() == null ? toolCall.id() : toolCall.callId();
        return new ResponseToolCall(toolCall.id(), callId, toolCall.name(), toolCall.argumentsJson());
    }

    private static ChatUsage toChatUsage(AiUsage usage) {
        if (usage == null) {
            return null;
        }
        return new ChatUsage(usage.inputTokens(), usage.outputTokens(), usage.totalTokens());
    }

    private static ResponseUsage toResponseUsage(AiUsage usage) {
        if (usage == null) {
            return null;
        }
        return new ResponseUsage(usage.inputTokens(), usage.outputTokens(), usage.totalTokens());
    }
}
