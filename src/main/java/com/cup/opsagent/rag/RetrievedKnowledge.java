package com.cup.opsagent.rag;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.Map;

public record RetrievedKnowledge(
        @NotBlank String knowledgeId,
        @NotBlank String sourceType,
        @NotBlank String title,
        @NotBlank String snippet,
        double score,
        @NotNull Instant lastUpdatedAt,
        Map<String, String> metadata
) {
    public RetrievedKnowledge {
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }
}
