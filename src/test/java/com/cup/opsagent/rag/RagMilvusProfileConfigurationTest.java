package com.cup.opsagent.rag;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = "ops-agent.rag.embedding-api-key=test-key")
@ActiveProfiles("rag-milvus")
class RagMilvusProfileConfigurationTest {

    @Autowired
    private RagProperties properties;

    @Autowired
    private VectorStoreClient vectorStoreClient;

    @Autowired
    private EmbeddingClient embeddingClient;

    @Test
    void shouldLoadRagMilvusProfileWithoutConnectingToMilvus() {
        assertThat(properties.isEnabled()).isTrue();
        assertThat(properties.retrieverMode()).isEqualTo(RagRetrieverMode.VECTOR);
        assertThat(properties.embeddingProvider()).isEqualTo(RagEmbeddingProvider.OPENAI);
        assertThat(properties.vectorStoreProvider()).isEqualTo(RagVectorStoreProvider.MILVUS);
        assertThat(properties.getEmbeddingBaseUrl()).isEqualTo("http://localhost:11434/v1");
        assertThat(properties.getEmbeddingModel()).isEqualTo("text-embedding-3-small");
        assertThat(properties.getMilvusUri()).isEqualTo("http://localhost:19530");
        assertThat(properties.getMilvusCollection()).isEqualTo("safeops_knowledge");
        assertThat(properties.getMilvusDimension()).isEqualTo(1536);
        assertThat(properties.milvusMetricType()).isEqualTo(RagMilvusMetricType.COSINE);
        assertThat(properties.milvusIndexType()).isEqualTo(RagMilvusIndexType.AUTOINDEX);
        assertThat(properties.getMilvusHnswM()).isEqualTo(16);
        assertThat(properties.getMilvusHnswEfConstruction()).isEqualTo(200);
        assertThat(properties.getMilvusHnswEf()).isEqualTo(64);
        assertThat(properties.getMilvusIvfFlatNlist()).isEqualTo(128);
        assertThat(properties.getMilvusIvfFlatNprobe()).isEqualTo(16);
        assertThat(properties.isMilvusValidateIndex()).isFalse();
        assertThat(properties.isMilvusAutoCreateCollection()).isFalse();
        assertThat(properties.isMilvusAutoLoadCollection()).isTrue();
        assertThat(vectorStoreClient).isInstanceOf(MilvusVectorStoreClient.class);
        assertThat(embeddingClient).isInstanceOf(OpenAiCompatibleEmbeddingClient.class);
    }
}
