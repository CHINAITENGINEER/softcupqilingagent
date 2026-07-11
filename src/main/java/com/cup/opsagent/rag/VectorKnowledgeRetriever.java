package com.cup.opsagent.rag;

import java.util.List;

public class VectorKnowledgeRetriever implements KnowledgeRetriever {

    private final EmbeddingClient embeddingClient;
    private final VectorStoreClient vectorStoreClient;
    private final int snippetMaxLength;

    public VectorKnowledgeRetriever(EmbeddingClient embeddingClient, VectorStoreClient vectorStoreClient) {
        this(embeddingClient, vectorStoreClient, RagProperties.DEFAULT_SNIPPET_MAX_LENGTH);
    }

    public VectorKnowledgeRetriever(EmbeddingClient embeddingClient, VectorStoreClient vectorStoreClient, int snippetMaxLength) {
        this.embeddingClient = embeddingClient;
        this.vectorStoreClient = vectorStoreClient;
        this.snippetMaxLength = snippetMaxLength <= 0 ? RagProperties.DEFAULT_SNIPPET_MAX_LENGTH : snippetMaxLength;
    }

    @Override
    public List<RetrievedKnowledge> retrieve(KnowledgeQuery query) {
        if (query == null || query.text().isBlank()) {
            return List.of();
        }
        EmbeddingVector queryEmbedding = embeddingClient.embed(query.text());
        if (queryEmbedding.isEmpty()) {
            return List.of();
        }
        VectorStoreQuery vectorQuery = new VectorStoreQuery(queryEmbedding, query.maxResults(), query.filters());
        List<VectorSearchResult> results = vectorStoreClient.search(vectorQuery);
        return results.stream()
                .map(result -> result.toRetrievedKnowledge(snippetMaxLength))
                .toList();
    }
}
