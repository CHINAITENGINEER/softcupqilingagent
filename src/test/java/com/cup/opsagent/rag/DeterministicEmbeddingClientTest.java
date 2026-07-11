package com.cup.opsagent.rag;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DeterministicEmbeddingClientTest {

    @Test
    void shouldCreateStableEmbeddingsForSameText() {
        DeterministicEmbeddingClient client = new DeterministicEmbeddingClient(16);

        EmbeddingVector first = client.embed("Nginx 502");
        EmbeddingVector second = client.embed(" nginx 502 ");

        assertThat(first.values()).containsExactly(second.values());
        assertThat(first.dimensions()).isEqualTo(16);
    }

    @Test
    void shouldCreateDifferentEmbeddingsForDifferentText() {
        DeterministicEmbeddingClient client = new DeterministicEmbeddingClient(16);

        EmbeddingVector nginx = client.embed("nginx 502");
        EmbeddingVector redis = client.embed("redis memory");

        assertThat(nginx.values()).isNotEqualTo(redis.values());
    }

    @Test
    void shouldReturnZeroVectorForBlankText() {
        DeterministicEmbeddingClient client = new DeterministicEmbeddingClient(8);

        EmbeddingVector vector = client.embed(" ");

        assertThat(vector.dimensions()).isEqualTo(8);
        assertThat(vector.values()).containsOnly(0.0f);
    }

    @Test
    void shouldFallbackToDefaultDimensionsForInvalidDimension() {
        DeterministicEmbeddingClient client = new DeterministicEmbeddingClient(0);

        assertThat(client.dimensions()).isEqualTo(DeterministicEmbeddingClient.DEFAULT_DIMENSIONS);
        assertThat(client.embed("nginx").dimensions()).isEqualTo(DeterministicEmbeddingClient.DEFAULT_DIMENSIONS);
    }

    @Test
    void shouldNormalizeNonBlankVector() {
        DeterministicEmbeddingClient client = new DeterministicEmbeddingClient(16);

        EmbeddingVector vector = client.embed("nginx 502 upstream");

        double norm = 0;
        for (float value : vector.values()) {
            norm += value * value;
        }
        assertThat(Math.sqrt(norm)).isCloseTo(1.0, org.assertj.core.data.Offset.offset(0.0001));
    }
}
