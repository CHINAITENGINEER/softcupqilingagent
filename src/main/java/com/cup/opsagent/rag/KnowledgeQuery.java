package com.cup.opsagent.rag;

import jakarta.validation.constraints.NotBlank;

import java.util.Map;

public record KnowledgeQuery(
        @NotBlank String text,
        int maxResults,
        Map<String, String> filters
) {
    public static final int DEFAULT_MAX_RESULTS = 5;

    public KnowledgeQuery {
        text = text == null ? "" : text.trim();
        maxResults = maxResults <= 0 ? DEFAULT_MAX_RESULTS : maxResults;
        filters = filters == null ? Map.of() : Map.copyOf(filters);
    }

    public static KnowledgeQuery of(String text) {
        return new KnowledgeQuery(text, DEFAULT_MAX_RESULTS, Map.of());
    }
}
