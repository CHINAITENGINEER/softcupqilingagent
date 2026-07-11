package com.cup.opsagent.rag;

public enum RagVectorStoreProvider {
    IN_MEMORY,
    MILVUS;

    public static RagVectorStoreProvider from(String value) {
        if (value == null || value.isBlank()) {
            return IN_MEMORY;
        }
        return RagVectorStoreProvider.valueOf(value.trim().toUpperCase().replace('-', '_'));
    }
}
