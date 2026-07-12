package io.wangrollin.ai.internal.protocol;

import java.util.List;

/**
 * Provider-neutral model turn request used by all provider adapters.
 *
 * @param model request-specific model override
 * @param inputText simple text input for text-first APIs
 * @param inputItems typed conversation/input items
 * @param instructions optional developer/system instructions
 * @param temperature optional sampling temperature
 * @param topP optional nucleus sampling value
 * @param maxOutputTokens optional output token cap
 * @param stopSequences optional stop sequences
 * @param outputFormat optional structured-output hint
 * @param previousResponseId optional provider continuation id
 * @param background optional provider-side background execution flag
 * @param tools advertised function tools
 * @param toolChoice optional tool selection policy
 */
public record AiTurnRequest(
        String model,
        String inputText,
        List<AiInputItem> inputItems,
        String instructions,
        Double temperature,
        Double topP,
        Integer maxOutputTokens,
        List<String> stopSequences,
        AiOutputFormat outputFormat,
        String previousResponseId,
        Boolean background,
        List<AiToolSpec> tools,
        AiToolChoice toolChoice) {
    public AiTurnRequest {
        inputItems = inputItems == null ? List.of() : List.copyOf(inputItems);
        stopSequences = stopSequences == null ? List.of() : List.copyOf(stopSequences);
        tools = tools == null ? List.of() : List.copyOf(tools);
    }
}
