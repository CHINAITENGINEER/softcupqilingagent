package com.cup.opsagent.rag;

import jakarta.validation.constraints.NotNull;

import java.util.Map;

public record VectorStoreQuery(
        @NotNull EmbeddingVector embedding,
        int maxResults,
        Map<String, String> filters
) {
    public static final int DEFAULT_MAX_RESULTS = 5;

    public VectorStoreQuery {
        embedding = embedding == null ? new EmbeddingVector(null) : embedding;
        maxResults = maxResults <= 0 ? DEFAULT_MAX_RESULTS : maxResults;
        filters = filters == null ? Map.of() : Map.copyOf(filters);
    }

    public static VectorStoreQuery of(EmbeddingVector embedding) {
        return new VectorStoreQuery(embedding, DEFAULT_MAX_RESULTS, Map.of());
    }
}
