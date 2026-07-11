package com.cup.opsagent.rag;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InMemoryVectorStoreClientTest {

    @Test
    void shouldReturnNearestRecordsOrderedByCosineScore() {
        InMemoryVectorStoreClient client = new InMemoryVectorStoreClient(List.of(
                record("doc-nginx", new float[]{1.0f, 0.0f}, Map.of("source", "runbook")),
                record("doc-redis", new float[]{0.0f, 1.0f}, Map.of("source", "runbook")),
                record("doc-nginx-copy", new float[]{0.9f, 0.1f}, Map.of("source", "runbook"))
        ));

        List<VectorSearchResult> results = client.search(new VectorStoreQuery(new EmbeddingVector(new float[]{1.0f, 0.0f}), 2, Map.of()));

        assertThat(results).hasSize(2);
        assertThat(results)
                .extracting(result -> result.record().knowledgeId())
                .containsExactly("doc-nginx", "doc-nginx-copy");
        assertThat(results.getFirst().score()).isGreaterThan(results.get(1).score());
    }

    @Test
    void shouldRespectMetadataFilters() {
        InMemoryVectorStoreClient client = new InMemoryVectorStoreClient(List.of(
                record("doc-runbook", new float[]{1.0f, 0.0f}, Map.of("source", "runbook")),
                record("doc-postmortem", new float[]{1.0f, 0.0f}, Map.of("source", "postmortem"))
        ));

        List<VectorSearchResult> results = client.search(new VectorStoreQuery(
                new EmbeddingVector(new float[]{1.0f, 0.0f}),
                5,
                Map.of("source", "postmortem")
        ));

        assertThat(results)
                .extracting(result -> result.record().knowledgeId())
                .containsExactly("doc-postmortem");
    }

    @Test
    void shouldUpsertByKnowledgeId() {
        InMemoryVectorStoreClient client = new InMemoryVectorStoreClient();
        client.upsert(List.of(record("doc-1", new float[]{1.0f}, Map.of("version", "old"))));
        client.upsert(List.of(record("doc-1", new float[]{1.0f}, Map.of("version", "new"))));

        assertThat(client.records()).hasSize(1);
        assertThat(client.records().getFirst().metadata()).containsEntry("version", "new");
    }

    @Test
    void shouldReturnEmptyForInvalidQueryOrDimensionMismatch() {
        InMemoryVectorStoreClient client = new InMemoryVectorStoreClient(List.of(
                record("doc-1", new float[]{1.0f, 0.0f}, Map.of())
        ));

        assertThat(client.search(null)).isEmpty();
        assertThat(client.search(VectorStoreQuery.of(new EmbeddingVector(null)))).isEmpty();
        assertThat(client.search(VectorStoreQuery.of(new EmbeddingVector(new float[]{1.0f, 0.0f, 0.0f})))).isEmpty();
    }

    @Test
    void shouldDefensivelyCopyEmbeddingValues() {
        float[] values = new float[]{1.0f, 0.0f};
        EmbeddingVector vector = new EmbeddingVector(values);
        values[0] = 0.0f;

        assertThat(vector.values()).containsExactly(1.0f, 0.0f);

        float[] returnedValues = vector.values();
        returnedValues[0] = 0.0f;

        assertThat(vector.values()).containsExactly(1.0f, 0.0f);
    }

    @Test
    void shouldConvertSearchResultToRetrievedKnowledgeWithSnippetLimit() {
        VectorSearchResult result = new VectorSearchResult(
                record("doc-1", new float[]{1.0f}, Map.of()),
                0.95
        );

        RetrievedKnowledge knowledge = result.toRetrievedKnowledge(10);

        assertThat(knowledge.knowledgeId()).isEqualTo("doc-1");
        assertThat(knowledge.snippet()).hasSize(13).endsWith("...");
        assertThat(knowledge.score()).isEqualTo(0.95);
    }

    @Test
    void shouldRejectNullSearchResultRecord() {
        assertThatThrownBy(() -> new VectorSearchResult(null, 0.5))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("record must not be null");
    }

    private VectorStoreRecord record(String id, float[] embedding, Map<String, String> metadata) {
        return new VectorStoreRecord(
                id,
                "runbook",
                "Nginx troubleshooting",
                "Check nginx status and upstream health before restart.",
                new EmbeddingVector(embedding),
                Instant.parse("2026-07-01T00:00:00Z"),
                metadata
        );
    }
}
