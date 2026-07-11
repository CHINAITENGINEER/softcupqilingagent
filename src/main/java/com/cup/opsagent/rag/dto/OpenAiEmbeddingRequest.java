package com.cup.opsagent.rag.dto;

public record OpenAiEmbeddingRequest(
        String model,
        String input
) {
}
