package io.wangrollin.ai.examples.rag;

import java.util.List;

/** HTTP response containing the generated answer and retrieved synthetic sources. */
public record RagAnswer(String answer, List<String> sourceIds) {
    public RagAnswer {
        sourceIds = List.copyOf(sourceIds);
    }
}
