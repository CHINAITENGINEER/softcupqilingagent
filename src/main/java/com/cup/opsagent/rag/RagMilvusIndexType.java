package com.cup.opsagent.rag;

import java.util.Locale;

public enum RagMilvusIndexType {
    AUTOINDEX,
    HNSW,
    IVF_FLAT;

    public static RagMilvusIndexType from(String value) {
        if (value == null || value.isBlank()) {
            return AUTOINDEX;
        }
        String normalized = value.trim().replace('-', '_').toUpperCase(Locale.ROOT);
        if (normalized.replace("_", "").equals("AUTOINDEX")) {
            return AUTOINDEX;
        }
        return RagMilvusIndexType.valueOf(normalized);
    }
}
