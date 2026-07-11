package com.cup.opsagent.rag;

public enum RagRetrieverMode {
    KEYWORD,
    VECTOR;

    public static RagRetrieverMode from(String value) {
        if (value == null || value.isBlank()) {
            return KEYWORD;
        }
        return RagRetrieverMode.valueOf(value.trim().toUpperCase().replace('-', '_'));
    }
}
