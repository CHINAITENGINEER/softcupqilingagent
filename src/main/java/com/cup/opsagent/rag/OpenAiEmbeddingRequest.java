package com.cup.opsagent.rag;

public record OpenAiEmbeddingRequest(
        String model,
        String input
) {
}
