package com.cup.opsagent.rag;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.Map;

public record KnowledgeChunk(
        @NotBlank String chunkId,
        @NotBlank String parentKnowledgeId,
        @NotBlank String sourceType,
        @NotBlank String title,
        @NotBlank String content,
        int chunkIndex,
        @NotNull Instant lastUpdatedAt,
        Map<String, String> metadata
) {
    public KnowledgeChunk {
        chunkId = chunkId == null ? "" : chunkId.trim();
        parentKnowledgeId = parentKnowledgeId == null ? "" : parentKnowledgeId.trim();
        sourceType = sourceType == null ? "" : sourceType.trim();
        title = title == null ? "" : title.trim();
        content = content == null ? "" : content.strip();
        chunkIndex = Math.max(0, chunkIndex);
        lastUpdatedAt = lastUpdatedAt == null ? Instant.EPOCH : lastUpdatedAt;
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }
}
