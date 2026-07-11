package com.cup.opsagent.rag;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class KnowledgeIngestionServiceTest {

    @Test
    void shouldChunkEmbedAndUpsertDocuments() {
        DeterministicEmbeddingClient embeddingClient = new DeterministicEmbeddingClient(16);
        InMemoryVectorStoreClient vectorStoreClient = new InMemoryVectorStoreClient();
        KnowledgeIngestionService ingestionService = new KnowledgeIngestionService(embeddingClient, vectorStoreClient, 30);

        KnowledgeIngestionResult result = ingestionService.ingest(List.of(document(
                "runbook-nginx",
                "Check nginx status before restart.\n\nCheck upstream health."
        )));

        assertThat(result.documentCount()).isEqualTo(1);
        assertThat(result.chunkCount()).isEqualTo(3);
        assertThat(result.knowledgeIds()).containsExactly("runbook-nginx");
        assertThat(result.chunkIds()).containsExactly(
                "runbook-nginx#chunk-0",
                "runbook-nginx#chunk-1",
                "runbook-nginx#chunk-2"
        );
        assertThat(vectorStoreClient.records()).hasSize(3);
        assertThat(vectorStoreClient.records())
                .allSatisfy(record -> {
                    assertThat(record.embedding().dimensions()).isEqualTo(16);
                    assertThat(record.metadata()).containsEntry("parentKnowledgeId", "runbook-nginx");
                });
    }

    @Test
    void shouldAllowVectorRetrieverToSearchIngestedKnowledge() {
        DeterministicEmbeddingClient embeddingClient = new DeterministicEmbeddingClient(16);
        InMemoryVectorStoreClient vectorStoreClient = new InMemoryVectorStoreClient();
        KnowledgeIngestionService ingestionService = new KnowledgeIngestionService(embeddingClient, vectorStoreClient, 100);
        ingestionService.ingest(List.of(
                document("runbook-nginx", "nginx 502 upstream timeout troubleshooting"),
                document("runbook-redis", "redis memory eviction troubleshooting")
        ));
        VectorKnowledgeRetriever retriever = new VectorKnowledgeRetriever(embeddingClient, vectorStoreClient, 100);

        List<RetrievedKnowledge> results = retriever.retrieve(KnowledgeQuery.of("nginx 502 upstream"));

        assertThat(results).isNotEmpty();
        assertThat(results.getFirst().metadata()).containsEntry("parentKnowledgeId", "runbook-nginx");
        assertThat(results.getFirst().snippet()).contains("nginx");
    }

    @Test
    void shouldReturnEmptyResultForNullOrEmptyDocuments() {
        KnowledgeIngestionService ingestionService = new KnowledgeIngestionService(
                new DeterministicEmbeddingClient(16),
                new InMemoryVectorStoreClient()
        );

        assertThat(ingestionService.ingest(null).chunkCount()).isZero();
        assertThat(ingestionService.ingest(List.of()).chunkCount()).isZero();
    }

    @Test
    void shouldSkipInvalidDocuments() {
        InMemoryVectorStoreClient vectorStoreClient = new InMemoryVectorStoreClient();
        KnowledgeIngestionService ingestionService = new KnowledgeIngestionService(
                new DeterministicEmbeddingClient(16),
                vectorStoreClient
        );

        KnowledgeIngestionResult result = ingestionService.ingest(List.of(
                document("valid", "valid content"),
                document("blank-content", " ")
        ));

        assertThat(result.documentCount()).isEqualTo(1);
        assertThat(result.chunkCount()).isEqualTo(1);
        assertThat(result.knowledgeIds()).containsExactly("valid");
        assertThat(vectorStoreClient.records()).hasSize(1);
    }

    @Test
    void shouldPreserveMetadataAndAddChunkMetadata() {
        KnowledgeIngestionService ingestionService = new KnowledgeIngestionService(
                new DeterministicEmbeddingClient(16),
                new InMemoryVectorStoreClient(),
                100
        );

        List<KnowledgeChunk> chunks = ingestionService.chunk(new KnowledgeDocument(
                "doc-1",
                "runbook",
                "Nginx",
                "first paragraph\n\nsecond paragraph",
                Instant.parse("2026-07-01T00:00:00Z"),
                Map.of("service", "nginx")
        ));

        assertThat(chunks).hasSize(2);
        assertThat(chunks.getFirst().metadata())
                .containsEntry("service", "nginx")
                .containsEntry("parentKnowledgeId", "doc-1")
                .containsEntry("chunkIndex", "0")
                .containsEntry("chunkCount", "2");
        assertThat(chunks.get(1).chunkId()).isEqualTo("doc-1#chunk-1");
    }

    @Test
    void shouldRejectMissingDependencies() {
        assertThatThrownBy(() -> new KnowledgeIngestionService(null, new InMemoryVectorStoreClient()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("embeddingClient");
        assertThatThrownBy(() -> new KnowledgeIngestionService(new DeterministicEmbeddingClient(), null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("vectorStoreClient");
    }

    private KnowledgeDocument document(String knowledgeId, String content) {
        return new KnowledgeDocument(
                knowledgeId,
                "runbook",
                knowledgeId + " title",
                content,
                Instant.parse("2026-07-01T00:00:00Z"),
                Map.of("source", "test")
        );
    }
}
