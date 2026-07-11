package com.cup.opsagent.rag;

import java.util.Locale;

public enum RagMilvusMetricType {
    COSINE,
    L2,
    IP;

    public static RagMilvusMetricType from(String value) {
        if (value == null || value.isBlank()) {
            return COSINE;
        }
        return RagMilvusMetricType.valueOf(value.trim().replace('-', '_').toUpperCase(Locale.ROOT));
    }
}
