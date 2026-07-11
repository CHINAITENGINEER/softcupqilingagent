package com.cup.opsagent.rag;

import jakarta.validation.constraints.NotNull;

public record VectorSearchResult(
        @NotNull VectorStoreRecord record,
        double score
) {
    public VectorSearchResult {
        if (record == null) {
            throw new IllegalArgumentException("vector search result record must not be null");
        }
    }

    public RetrievedKnowledge toRetrievedKnowledge(int snippetMaxLength) {
        return new RetrievedKnowledge(
                record.knowledgeId(),
                record.sourceType(),
                record.title(),
                snippet(record.content(), snippetMaxLength),
                score,
                record.lastUpdatedAt(),
                record.metadata()
        );
    }

    private String snippet(String content, int snippetMaxLength) {
        String safeContent = content == null ? "" : content.strip();
        int safeMaxLength = snippetMaxLength <= 0 ? RagProperties.DEFAULT_SNIPPET_MAX_LENGTH : snippetMaxLength;
        if (safeContent.length() <= safeMaxLength) {
            return safeContent;
        }
        return safeContent.substring(0, safeMaxLength) + "...";
    }
}
