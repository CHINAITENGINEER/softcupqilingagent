package com.cup.opsagent.rag;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.Map;

public record KnowledgeDocument(
        @NotBlank String knowledgeId,
        @NotBlank String sourceType,
        @NotBlank String title,
        @NotBlank String content,
        @NotNull Instant lastUpdatedAt,
        Map<String, String> metadata
) {
    public KnowledgeDocument {
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }
}
