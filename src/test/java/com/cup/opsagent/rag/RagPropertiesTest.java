package com.cup.opsagent.rag;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RagPropertiesTest {

    @Test
    void shouldDisableRagByDefault() {
        RagProperties properties = new RagProperties();

        assertThat(properties.isEnabled()).isFalse();
        assertThat(properties.getRetrieverMode()).isEqualTo("keyword");
        assertThat(properties.retrieverMode()).isEqualTo(RagRetrieverMode.KEYWORD);
        assertThat(properties.getEmbeddingProvider()).isEqualTo("deterministic");
        assertThat(properties.embeddingProvider()).isEqualTo(RagEmbeddingProvider.DETERMINISTIC);
        assertThat(properties.getEmbeddingBaseUrl()).isEmpty();
        assertThat(properties.getEmbeddingApiKey()).isEmpty();
        assertThat(properties.getEmbeddingModel()).isEmpty();
        assertThat(properties.getVectorStoreProvider()).isEqualTo("in-memory");
        assertThat(properties.vectorStoreProvider()).isEqualTo(RagVectorStoreProvider.IN_MEMORY);
        assertThat(properties.getMilvusUri()).isEmpty();
        assertThat(properties.getMilvusToken()).isEmpty();
        assertThat(properties.getMilvusCollection()).isEmpty();
        assertThat(properties.getMilvusDimension()).isZero();
        assertThat(properties.getMilvusMetricType()).isEqualTo("cosine");
        assertThat(properties.milvusMetricType()).isEqualTo(RagMilvusMetricType.COSINE);
        assertThat(properties.getMilvusIndexType()).isEqualTo("autoindex");
        assertThat(properties.milvusIndexType()).isEqualTo(RagMilvusIndexType.AUTOINDEX);
        assertThat(properties.getMilvusHnswM()).isEqualTo(16);
        assertThat(properties.getMilvusHnswEfConstruction()).isEqualTo(200);
        assertThat(properties.getMilvusHnswEf()).isEqualTo(64);
        assertThat(properties.getMilvusIvfFlatNlist()).isEqualTo(128);
        assertThat(properties.getMilvusIvfFlatNprobe()).isEqualTo(16);
        assertThat(properties.isMilvusValidateIndex()).isFalse();
        assertThat(properties.isMilvusAutoCreateCollection()).isFalse();
        assertThat(properties.isMilvusAutoLoadCollection()).isTrue();
        assertThat(properties.getMaxResults()).isEqualTo(RagProperties.DEFAULT_MAX_RESULTS);
        assertThat(properties.getContextMaxLength()).isEqualTo(RagProperties.DEFAULT_CONTEXT_MAX_LENGTH);
        assertThat(properties.getSnippetMaxLength()).isEqualTo(RagProperties.DEFAULT_SNIPPET_MAX_LENGTH);
    }

    @Test
    void shouldValidatePositiveConfiguration() {
        RagProperties properties = new RagProperties();
        properties.setEnabled(true);
        properties.setRetrieverMode("vector");
        properties.setEmbeddingProvider("deterministic");
        properties.setVectorStoreProvider("in-memory");
        properties.setMaxResults(3);
        properties.setContextMaxLength(1_000);
        properties.setSnippetMaxLength(200);

        properties.validate();

        assertThat(properties.isEnabled()).isTrue();
        assertThat(properties.retrieverMode()).isEqualTo(RagRetrieverMode.VECTOR);
        assertThat(properties.embeddingProvider()).isEqualTo(RagEmbeddingProvider.DETERMINISTIC);
        assertThat(properties.vectorStoreProvider()).isEqualTo(RagVectorStoreProvider.IN_MEMORY);
    }

    @Test
    void shouldValidateOpenAiEmbeddingProviderConfiguration() {
        RagProperties properties = new RagProperties();
        properties.setEmbeddingProvider("openai");
        properties.setEmbeddingBaseUrl("https://api.example.com/v1");
        properties.setEmbeddingApiKey("sk-validsecret123456789");
        properties.setEmbeddingModel("text-embedding-test");

        properties.validateEmbeddingProviderConfig();

        assertThat(properties.embeddingProvider()).isEqualTo(RagEmbeddingProvider.OPENAI);
    }

    @Test
    void shouldValidateMilvusVectorStoreProviderConfiguration() {
        RagProperties properties = new RagProperties();
        properties.setVectorStoreProvider("milvus");
        properties.setMilvusUri("http://localhost:19530");
        properties.setMilvusCollection("safeops_knowledge");
        properties.setMilvusDimension(1536);

        properties.validateVectorStoreProviderConfig();

        assertThat(properties.vectorStoreProvider()).isEqualTo(RagVectorStoreProvider.MILVUS);
    }

    @Test
    void shouldValidateMilvusMetricType() {
        RagProperties properties = new RagProperties();
        properties.setVectorStoreProvider("milvus");
        properties.setMilvusUri("http://localhost:19530");
        properties.setMilvusCollection("safeops_knowledge");
        properties.setMilvusDimension(1536);
        properties.setMilvusMetricType("ip");

        properties.validateVectorStoreProviderConfig();

        assertThat(properties.milvusMetricType()).isEqualTo(RagMilvusMetricType.IP);
    }

    @Test
    void shouldValidateMilvusIndexType() {
        RagProperties properties = new RagProperties();
        properties.setVectorStoreProvider("milvus");
        properties.setMilvusUri("http://localhost:19530");
        properties.setMilvusCollection("safeops_knowledge");
        properties.setMilvusDimension(1536);
        properties.setMilvusIndexType("auto-index");

        properties.validateVectorStoreProviderConfig();

        assertThat(properties.milvusIndexType()).isEqualTo(RagMilvusIndexType.AUTOINDEX);
    }

    @Test
    void shouldFailClosedWhenRetrieverModeIsInvalid() {
        RagProperties properties = new RagProperties();
        properties.setRetrieverMode("milvus");

        assertThatThrownBy(properties::validate)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("retrieverMode")
                .hasMessageContaining("keyword, vector");
    }

    @Test
    void shouldFailClosedWhenEmbeddingProviderIsInvalid() {
        RagProperties properties = new RagProperties();
        properties.setEmbeddingProvider("bad-provider");

        assertThatThrownBy(properties::validate)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("embeddingProvider")
                .hasMessageContaining("deterministic, openai");
    }

    @Test
    void shouldFailClosedWhenOpenAiEmbeddingConfigIsMissing() {
        RagProperties properties = new RagProperties();
        properties.setEmbeddingProvider("openai");
        properties.setEmbeddingBaseUrl("https://api.example.com/v1");
        properties.setEmbeddingApiKey(" ");
        properties.setEmbeddingModel("text-embedding-test");

        assertThatThrownBy(properties::validateEmbeddingProviderConfig)
                .isInstanceOfSatisfying(RagProviderException.class, exception ->
                        assertThat(exception.code()).isEqualTo(RagProviderErrorCode.RAG_PROVIDER_MISCONFIGURED)
                )
                .hasMessageContaining("embeddingApiKey");
    }

    @Test
    void shouldFailClosedWhenOpenAiEmbeddingBaseUrlIsUnsafe() {
        RagProperties properties = new RagProperties();
        properties.setEmbeddingProvider("openai");
        properties.setEmbeddingBaseUrl("http://api.example.com/v1");
        properties.setEmbeddingApiKey("sk-validsecret123456789");
        properties.setEmbeddingModel("text-embedding-test");

        assertThatThrownBy(properties::validateEmbeddingProviderConfig)
                .isInstanceOfSatisfying(RagProviderException.class, exception ->
                        assertThat(exception.code()).isEqualTo(RagProviderErrorCode.RAG_PROVIDER_MISCONFIGURED)
                )
                .hasMessageContaining("https");
    }

    @Test
    void shouldFailClosedWhenVectorStoreProviderIsInvalid() {
        RagProperties properties = new RagProperties();
        properties.setVectorStoreProvider("bad-vector-store");

        assertThatThrownBy(properties::validate)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("vectorStoreProvider")
                .hasMessageContaining("in-memory, milvus");
    }

    @Test
    void shouldFailClosedWhenMilvusConfigIsMissing() {
        RagProperties properties = new RagProperties();
        properties.setVectorStoreProvider("milvus");
        properties.setMilvusUri("http://localhost:19530");
        properties.setMilvusCollection(" ");
        properties.setMilvusDimension(1536);

        assertThatThrownBy(properties::validateVectorStoreProviderConfig)
                .isInstanceOfSatisfying(RagProviderException.class, exception ->
                        assertThat(exception.code()).isEqualTo(RagProviderErrorCode.RAG_PROVIDER_MISCONFIGURED)
                )
                .hasMessageContaining("milvusCollection");
    }

    @Test
    void shouldFailClosedWhenMilvusDimensionIsInvalid() {
        RagProperties properties = new RagProperties();
        properties.setVectorStoreProvider("milvus");
        properties.setMilvusUri("http://localhost:19530");
        properties.setMilvusCollection("safeops_knowledge");
        properties.setMilvusDimension(0);

        assertThatThrownBy(properties::validateVectorStoreProviderConfig)
                .isInstanceOfSatisfying(RagProviderException.class, exception ->
                        assertThat(exception.code()).isEqualTo(RagProviderErrorCode.RAG_PROVIDER_MISCONFIGURED)
                )
                .hasMessageContaining("milvusDimension")
                .hasMessageContaining("positive");
    }

    @Test
    void shouldFailClosedWhenMilvusMetricTypeIsInvalid() {
        RagProperties properties = new RagProperties();
        properties.setVectorStoreProvider("milvus");
        properties.setMilvusUri("http://localhost:19530");
        properties.setMilvusCollection("safeops_knowledge");
        properties.setMilvusDimension(1536);
        properties.setMilvusMetricType("euclidean");

        assertThatThrownBy(properties::validateVectorStoreProviderConfig)
                .isInstanceOfSatisfying(RagProviderException.class, exception ->
                        assertThat(exception.code()).isEqualTo(RagProviderErrorCode.RAG_PROVIDER_MISCONFIGURED)
                )
                .hasMessageContaining("milvusMetricType")
                .hasMessageContaining("cosine, l2, ip");
    }

    @Test
    void shouldFailClosedWhenMilvusIndexTypeIsInvalid() {
        RagProperties properties = new RagProperties();
        properties.setVectorStoreProvider("milvus");
        properties.setMilvusUri("http://localhost:19530");
        properties.setMilvusCollection("safeops_knowledge");
        properties.setMilvusDimension(1536);
        properties.setMilvusIndexType("annoy");

        assertThatThrownBy(properties::validateVectorStoreProviderConfig)
                .isInstanceOfSatisfying(RagProviderException.class, exception ->
                        assertThat(exception.code()).isEqualTo(RagProviderErrorCode.RAG_PROVIDER_MISCONFIGURED)
                )
                .hasMessageContaining("milvusIndexType")
                .hasMessageContaining("autoindex, hnsw, ivf-flat");
    }

    @Test
    void shouldValidateMilvusHnswIndexType() {
        RagProperties properties = new RagProperties();
        properties.setVectorStoreProvider("milvus");
        properties.setMilvusUri("http://localhost:19530");
        properties.setMilvusCollection("safeops_knowledge");
        properties.setMilvusDimension(1536);
        properties.setMilvusIndexType("hnsw");
        properties.setMilvusHnswM(32);
        properties.setMilvusHnswEfConstruction(300);
        properties.setMilvusHnswEf(128);

        properties.validateVectorStoreProviderConfig();

        assertThat(properties.milvusIndexType()).isEqualTo(RagMilvusIndexType.HNSW);
    }

    @Test
    void shouldFailClosedWhenMilvusHnswMIsInvalid() {
        RagProperties properties = new RagProperties();
        properties.setVectorStoreProvider("milvus");
        properties.setMilvusUri("http://localhost:19530");
        properties.setMilvusCollection("safeops_knowledge");
        properties.setMilvusDimension(1536);
        properties.setMilvusIndexType("hnsw");
        properties.setMilvusHnswM(0);

        assertThatThrownBy(properties::validateVectorStoreProviderConfig)
                .isInstanceOfSatisfying(RagProviderException.class, exception ->
                        assertThat(exception.code()).isEqualTo(RagProviderErrorCode.RAG_PROVIDER_MISCONFIGURED)
                )
                .hasMessageContaining("milvusHnswM")
                .hasMessageContaining("positive");
    }

    @Test
    void shouldFailClosedWhenMilvusHnswEfConstructionIsInvalid() {
        RagProperties properties = new RagProperties();
        properties.setVectorStoreProvider("milvus");
        properties.setMilvusUri("http://localhost:19530");
        properties.setMilvusCollection("safeops_knowledge");
        properties.setMilvusDimension(1536);
        properties.setMilvusIndexType("hnsw");
        properties.setMilvusHnswEfConstruction(0);

        assertThatThrownBy(properties::validateVectorStoreProviderConfig)
                .isInstanceOfSatisfying(RagProviderException.class, exception ->
                        assertThat(exception.code()).isEqualTo(RagProviderErrorCode.RAG_PROVIDER_MISCONFIGURED)
                )
                .hasMessageContaining("milvusHnswEfConstruction")
                .hasMessageContaining("positive");
    }

    @Test
    void shouldFailClosedWhenMilvusHnswEfIsInvalid() {
        RagProperties properties = new RagProperties();
        properties.setVectorStoreProvider("milvus");
        properties.setMilvusUri("http://localhost:19530");
        properties.setMilvusCollection("safeops_knowledge");
        properties.setMilvusDimension(1536);
        properties.setMilvusIndexType("hnsw");
        properties.setMilvusHnswEf(0);

        assertThatThrownBy(properties::validateVectorStoreProviderConfig)
                .isInstanceOfSatisfying(RagProviderException.class, exception ->
                        assertThat(exception.code()).isEqualTo(RagProviderErrorCode.RAG_PROVIDER_MISCONFIGURED)
                )
                .hasMessageContaining("milvusHnswEf")
                .hasMessageContaining("positive");
    }

    @Test
    void shouldValidateMilvusIvfFlatIndexType() {
        RagProperties properties = new RagProperties();
        properties.setVectorStoreProvider("milvus");
        properties.setMilvusUri("http://localhost:19530");
        properties.setMilvusCollection("safeops_knowledge");
        properties.setMilvusDimension(1536);
        properties.setMilvusIndexType("ivf-flat");
        properties.setMilvusIvfFlatNlist(256);
        properties.setMilvusIvfFlatNprobe(32);

        properties.validateVectorStoreProviderConfig();

        assertThat(properties.milvusIndexType()).isEqualTo(RagMilvusIndexType.IVF_FLAT);
    }

    @Test
    void shouldFailClosedWhenMilvusIvfFlatNlistIsInvalid() {
        RagProperties properties = new RagProperties();
        properties.setVectorStoreProvider("milvus");
        properties.setMilvusUri("http://localhost:19530");
        properties.setMilvusCollection("safeops_knowledge");
        properties.setMilvusDimension(1536);
        properties.setMilvusIndexType("ivf-flat");
        properties.setMilvusIvfFlatNlist(0);

        assertThatThrownBy(properties::validateVectorStoreProviderConfig)
                .isInstanceOfSatisfying(RagProviderException.class, exception ->
                        assertThat(exception.code()).isEqualTo(RagProviderErrorCode.RAG_PROVIDER_MISCONFIGURED)
                )
                .hasMessageContaining("milvusIvfFlatNlist")
                .hasMessageContaining("positive");
    }

    @Test
    void shouldFailClosedWhenMilvusIvfFlatNprobeIsInvalid() {
        RagProperties properties = new RagProperties();
        properties.setVectorStoreProvider("milvus");
        properties.setMilvusUri("http://localhost:19530");
        properties.setMilvusCollection("safeops_knowledge");
        properties.setMilvusDimension(1536);
        properties.setMilvusIndexType("ivf-flat");
        properties.setMilvusIvfFlatNprobe(0);

        assertThatThrownBy(properties::validateVectorStoreProviderConfig)
                .isInstanceOfSatisfying(RagProviderException.class, exception ->
                        assertThat(exception.code()).isEqualTo(RagProviderErrorCode.RAG_PROVIDER_MISCONFIGURED)
                )
                .hasMessageContaining("milvusIvfFlatNprobe")
                .hasMessageContaining("positive");
    }

    @Test
    void shouldFailClosedWhenMilvusUriIsInvalid() {
        RagProperties properties = new RagProperties();
        properties.setVectorStoreProvider("milvus");
        properties.setMilvusUri("ftp://localhost:19530");
        properties.setMilvusCollection("safeops_knowledge");
        properties.setMilvusDimension(1536);

        assertThatThrownBy(properties::validateVectorStoreProviderConfig)
                .isInstanceOfSatisfying(RagProviderException.class, exception ->
                        assertThat(exception.code()).isEqualTo(RagProviderErrorCode.RAG_PROVIDER_MISCONFIGURED)
                )
                .hasMessageContaining("http or https");
    }

    @Test
    void shouldFailClosedWhenMaxResultsIsInvalid() {
        RagProperties properties = new RagProperties();
        properties.setMaxResults(0);

        assertThatThrownBy(properties::validate)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("maxResults")
                .hasMessageContaining("positive");
    }

    @Test
    void shouldFailClosedWhenContextMaxLengthIsInvalid() {
        RagProperties properties = new RagProperties();
        properties.setContextMaxLength(-1);

        assertThatThrownBy(properties::validate)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("contextMaxLength")
                .hasMessageContaining("positive");
    }

    @Test
    void shouldFailClosedWhenSnippetMaxLengthIsInvalid() {
        RagProperties properties = new RagProperties();
        properties.setSnippetMaxLength(0);

        assertThatThrownBy(properties::validate)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("snippetMaxLength")
                .hasMessageContaining("positive");
    }
}
