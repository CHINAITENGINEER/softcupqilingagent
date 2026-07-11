package com.cup.opsagent.rag;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.Map;

public record VectorStoreRecord(
        @NotBlank String knowledgeId,
        @NotBlank String sourceType,
        @NotBlank String title,
        @NotBlank String content,
        @NotNull EmbeddingVector embedding,
        @NotNull Instant lastUpdatedAt,
        Map<String, String> metadata
) {
    public VectorStoreRecord {
        knowledgeId = knowledgeId == null ? "" : knowledgeId.trim();
        sourceType = sourceType == null ? "" : sourceType.trim();
        title = title == null ? "" : title.trim();
        content = content == null ? "" : content.strip();
        embedding = embedding == null ? new EmbeddingVector(null) : embedding;
        lastUpdatedAt = lastUpdatedAt == null ? Instant.EPOCH : lastUpdatedAt;
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }
}
