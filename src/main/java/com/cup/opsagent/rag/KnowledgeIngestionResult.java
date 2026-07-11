package com.cup.opsagent.rag;

import java.util.List;

public record KnowledgeIngestionResult(
        int documentCount,
        int chunkCount,
        List<String> knowledgeIds,
        List<String> chunkIds
) {
    public KnowledgeIngestionResult {
        documentCount = Math.max(0, documentCount);
        chunkCount = Math.max(0, chunkCount);
        knowledgeIds = knowledgeIds == null ? List.of() : List.copyOf(knowledgeIds);
        chunkIds = chunkIds == null ? List.of() : List.copyOf(chunkIds);
    }
}
