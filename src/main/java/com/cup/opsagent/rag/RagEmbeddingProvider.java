package com.cup.opsagent.rag;

public enum RagEmbeddingProvider {
    DETERMINISTIC,
    OPENAI;

    public static RagEmbeddingProvider from(String value) {
        if (value == null || value.isBlank()) {
            return DETERMINISTIC;
        }
        return RagEmbeddingProvider.valueOf(value.trim().toUpperCase().replace('-', '_'));
    }
}
