package com.cup.opsagent.rag;

import com.google.gson.JsonObject;
import io.milvus.v2.common.DataType;
import io.milvus.v2.common.IndexParam;
import io.milvus.v2.service.collection.request.CreateCollectionReq;
import io.milvus.v2.service.collection.request.DescribeCollectionReq;
import io.milvus.v2.service.collection.request.HasCollectionReq;
import io.milvus.v2.service.collection.request.LoadCollectionReq;
import io.milvus.v2.service.collection.response.DescribeCollectionResp;
import io.milvus.v2.service.index.request.CreateIndexReq;
import io.milvus.v2.service.index.request.DescribeIndexReq;
import io.milvus.v2.service.index.response.DescribeIndexResp;
import io.milvus.v2.service.vector.request.SearchReq;
import io.milvus.v2.service.vector.request.UpsertReq;
import io.milvus.v2.service.vector.response.SearchResp;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MilvusVectorStoreClientTest {

    @Test
    void shouldUpsertRecordsAsMilvusJsonRows() {
        FakeMilvusOperations operations = new FakeMilvusOperations();
        MilvusVectorStoreClient client = new MilvusVectorStoreClient(validProperties(), operations);

        client.upsert(List.of(record("runbook-redis#chunk-0")));

        assertThat(operations.upsertRequest).isNotNull();
        assertThat(operations.upsertRequest.getCollectionName()).isEqualTo("safeops_knowledge");
        assertThat(operations.upsertRequest.getData()).hasSize(1);
        JsonObject row = operations.upsertRequest.getData().getFirst();
        assertThat(row.get(MilvusVectorStoreClient.FIELD_KNOWLEDGE_ID).getAsString()).isEqualTo("runbook-redis#chunk-0");
        assertThat(row.get(MilvusVectorStoreClient.FIELD_SOURCE_TYPE).getAsString()).isEqualTo("runbook");
        assertThat(row.get(MilvusVectorStoreClient.FIELD_TITLE).getAsString()).isEqualTo("Redis memory pressure");
        assertThat(row.get(MilvusVectorStoreClient.FIELD_EMBEDDING).getAsJsonArray()).hasSize(3);
        assertThat(row.get("metadata_service").getAsString()).isEqualTo("redis");
        assertThat(row.get(MilvusVectorStoreClient.FIELD_METADATA_JSON).getAsString()).contains("redis");
    }

    @Test
    void shouldSearchMilvusAndConvertResults() {
        FakeMilvusOperations operations = new FakeMilvusOperations();
        operations.searchResponse = searchResponse();
        MilvusVectorStoreClient client = new MilvusVectorStoreClient(validProperties(), operations);

        List<VectorSearchResult> results = client.search(new VectorStoreQuery(new EmbeddingVector(new float[]{1.0f, 0.0f, 0.0f}), 5, Map.of("service", "redis")));

        assertThat(operations.searchRequest).isNotNull();
        assertThat(operations.searchRequest.getCollectionName()).isEqualTo("safeops_knowledge");
        assertThat(operations.searchRequest.getLimit()).isEqualTo(5);
        assertThat(operations.searchRequest.getFilter()).isEqualTo("metadata_service == \"redis\"");
        assertThat(operations.searchRequest.getOutputFields()).contains(MilvusVectorStoreClient.FIELD_KNOWLEDGE_ID, "metadata_service");
        assertThat(results).hasSize(1);
        VectorSearchResult result = results.getFirst();
        assertThat(result.score()).isEqualTo(0.91f);
        assertThat(result.record().knowledgeId()).isEqualTo("runbook-redis#chunk-0");
        assertThat(result.record().metadata()).containsEntry("service", "redis");
        assertThat(result.record().lastUpdatedAt()).isEqualTo(Instant.parse("2026-07-01T00:00:00Z"));
    }

    @Test
    void shouldCapSearchLimitToConfiguredMaxResults() {
        FakeMilvusOperations operations = new FakeMilvusOperations();
        operations.searchResponse = searchResponse();
        RagProperties properties = validProperties();
        properties.setMaxResults(3);
        MilvusVectorStoreClient client = new MilvusVectorStoreClient(properties, operations);

        client.search(new VectorStoreQuery(new EmbeddingVector(new float[]{1.0f, 0.0f, 0.0f}), 100, Map.of("service", "redis")));

        assertThat(operations.searchRequest.getLimit()).isEqualTo(3);
    }

    @Test
    void shouldUseConfiguredMetricTypeWhenSearching() {
        FakeMilvusOperations operations = new FakeMilvusOperations();
        operations.searchResponse = searchResponse();
        RagProperties properties = validProperties();
        properties.setMilvusMetricType("ip");
        MilvusVectorStoreClient client = new MilvusVectorStoreClient(properties, operations);

        client.search(new VectorStoreQuery(new EmbeddingVector(new float[]{1.0f, 0.0f, 0.0f}), 5, Map.of("service", "redis")));

        assertThat(operations.searchRequest.getMetricType()).isEqualTo(IndexParam.MetricType.IP);
    }

    @Test
    void shouldUseConfiguredHnswSearchParamsWhenSearching() {
        FakeMilvusOperations operations = new FakeMilvusOperations();
        operations.searchResponse = searchResponse();
        RagProperties properties = validProperties();
        properties.setMilvusIndexType("hnsw");
        properties.setMilvusHnswEf(128);
        MilvusVectorStoreClient client = new MilvusVectorStoreClient(properties, operations);

        client.search(new VectorStoreQuery(new EmbeddingVector(new float[]{1.0f, 0.0f, 0.0f}), 5, Map.of("service", "redis")));

        assertThat(operations.searchRequest.getSearchParams()).containsEntry("ef", 128);
    }

    @Test
    void shouldUseConfiguredIvfFlatSearchParamsWhenSearching() {
        FakeMilvusOperations operations = new FakeMilvusOperations();
        operations.searchResponse = searchResponse();
        RagProperties properties = validProperties();
        properties.setMilvusIndexType("ivf-flat");
        properties.setMilvusIvfFlatNprobe(32);
        MilvusVectorStoreClient client = new MilvusVectorStoreClient(properties, operations);

        client.search(new VectorStoreQuery(new EmbeddingVector(new float[]{1.0f, 0.0f, 0.0f}), 5, Map.of("service", "redis")));

        assertThat(operations.searchRequest.getSearchParams()).containsEntry("nprobe", 32);
    }

    @Test
    void shouldEscapeFilterValuesAndSortFiltersDeterministically() {
        FakeMilvusOperations operations = new FakeMilvusOperations();
        MilvusVectorStoreClient client = new MilvusVectorStoreClient(validProperties(), operations);
        Map<String, String> filters = new LinkedHashMap<>();
        filters.put("team/name", "platform\\core");
        filters.put("service", "redis \"cache\"");
        filters.put("note", "line1\nline2\tend");
        filters.put("!!!", "ignored");

        client.search(new VectorStoreQuery(new EmbeddingVector(new float[]{1.0f, 0.0f, 0.0f}), 5, filters));

        assertThat(operations.searchRequest.getFilter())
                .isEqualTo("metadata_note == \"line1\\nline2\\tend\" and metadata_service == \"redis \\\"cache\\\"\" and metadata_team_name == \"platform\\\\core\"");
        assertThat(operations.searchRequest.getOutputFields()).doesNotContain("metadata_unknown", "metadata_");
    }

    @Test
    void shouldIgnoreBlankFilterKeysAndDeduplicateOutputFields() {
        FakeMilvusOperations operations = new FakeMilvusOperations();
        MilvusVectorStoreClient client = new MilvusVectorStoreClient(validProperties(), operations);
        Map<String, String> filters = new LinkedHashMap<>();
        filters.put(" ", "ignored");
        filters.put("service", "redis");
        filters.put("service/name", "cache");
        filters.put("service-name", "mysql");

        client.search(new VectorStoreQuery(new EmbeddingVector(new float[]{1.0f, 0.0f, 0.0f}), 5, filters));

        assertThat(operations.searchRequest.getFilter()).isEqualTo("metadata_service == \"redis\" and metadata_service_name == \"mysql\"");
        assertThat(operations.searchRequest.getOutputFields()).contains(MilvusVectorStoreClient.FIELD_METADATA_JSON, "metadata_service", "metadata_service_name");
        assertThat(operations.searchRequest.getOutputFields()).containsOnlyOnce("metadata_service_name");
        assertThat(operations.searchRequest.getOutputFields()).doesNotContain("metadata_unknown", "metadata_");
    }

    @Test
    void shouldIgnoreBlankMetadataKeysDuringUpsert() {
        FakeMilvusOperations operations = new FakeMilvusOperations();
        MilvusVectorStoreClient client = new MilvusVectorStoreClient(validProperties(), operations);
        VectorStoreRecord record = new VectorStoreRecord(
                "runbook-redis#chunk-0",
                "runbook",
                "Redis memory pressure",
                "Redis memory pressure troubleshooting.",
                new EmbeddingVector(new float[]{1.0f, 0.0f, 0.0f}),
                Instant.parse("2026-07-01T00:00:00Z"),
                Map.of(" ", "ignored", "!!!", "ignored", "service/name", "redis")
        );

        client.upsert(List.of(record));

        JsonObject row = operations.upsertRequest.getData().getFirst();
        assertThat(row.has("metadata_service_name")).isTrue();
        assertThat(row.has("metadata_unknown")).isFalse();
        assertThat(row.has("metadata_")).isFalse();
    }

    @Test
    void shouldSkipEmptyUpsertPayload() {
        FakeMilvusOperations operations = new FakeMilvusOperations();
        MilvusVectorStoreClient client = new MilvusVectorStoreClient(validProperties(), operations);

        client.upsert(List.of(new VectorStoreRecord("", "runbook", "title", "content", new EmbeddingVector(new float[]{1.0f}), Instant.now(), Map.of())));

        assertThat(operations.upsertRequest).isNull();
    }

    @Test
    void shouldRejectUpsertEmbeddingWithWrongDimensionBeforeCallingMilvus() {
        FakeMilvusOperations operations = new FakeMilvusOperations();
        MilvusVectorStoreClient client = new MilvusVectorStoreClient(validProperties(), operations);
        VectorStoreRecord record = new VectorStoreRecord(
                "runbook-redis#chunk-0",
                "runbook",
                "Redis memory pressure",
                "Redis memory pressure troubleshooting.",
                new EmbeddingVector(new float[]{1.0f, 0.0f}),
                Instant.parse("2026-07-01T00:00:00Z"),
                Map.of("service", "redis")
        );

        assertThatThrownBy(() -> client.upsert(List.of(record)))
                .isInstanceOfSatisfying(RagProviderException.class, exception ->
                        assertThat(exception.code()).isEqualTo(RagProviderErrorCode.RAG_PROVIDER_MISCONFIGURED)
                )
                .hasMessageContaining("dimension mismatch")
                .hasMessageContaining("expected 3 but was 2");
        assertThat(operations.upsertRequest).isNull();
    }

    @Test
    void shouldRejectSearchEmbeddingWithWrongDimensionBeforeCallingMilvus() {
        FakeMilvusOperations operations = new FakeMilvusOperations();
        MilvusVectorStoreClient client = new MilvusVectorStoreClient(validProperties(), operations);

        assertThatThrownBy(() -> client.search(new VectorStoreQuery(new EmbeddingVector(new float[]{1.0f, 0.0f}), 5, Map.of())))
                .isInstanceOfSatisfying(RagProviderException.class, exception ->
                        assertThat(exception.code()).isEqualTo(RagProviderErrorCode.RAG_PROVIDER_MISCONFIGURED)
                )
                .hasMessageContaining("dimension mismatch")
                .hasMessageContaining("expected 3 but was 2");
        assertThat(operations.searchRequest).isNull();
    }

    @Test
    void shouldWrapMilvusUpsertFailure() {
        FakeMilvusOperations operations = new FakeMilvusOperations();
        operations.upsertFailure = new RuntimeException("boom");
        MilvusVectorStoreClient client = new MilvusVectorStoreClient(validProperties(), operations);

        assertThatThrownBy(() -> client.upsert(List.of(record("runbook-redis#chunk-0"))))
                .isInstanceOfSatisfying(RagProviderException.class, exception ->
                        assertThat(exception.code()).isEqualTo(RagProviderErrorCode.RAG_PROVIDER_SERVER_ERROR)
                )
                .hasMessageContaining("Milvus vector upsert failed");
    }

    @Test
    void shouldAutoCreateIndexAndLoadCollectionWhenEnabled() {
        FakeMilvusOperations operations = new FakeMilvusOperations();
        operations.collectionExists = false;
        RagProperties properties = validProperties();
        properties.setMilvusAutoCreateCollection(true);
        properties.setMilvusAutoLoadCollection(true);
        MilvusVectorStoreClient client = new MilvusVectorStoreClient(properties, operations);

        client.upsert(List.of(record("runbook-redis#chunk-0")));

        assertThat(operations.createCollectionRequest).isNotNull();
        assertThat(operations.createCollectionRequest.getCollectionName()).isEqualTo("safeops_knowledge");
        assertThat(operations.createIndexRequest).isNotNull();
        assertThat(operations.createIndexRequest.getCollectionName()).isEqualTo("safeops_knowledge");
        assertThat(operations.createIndexRequest.getIndexParams().getFirst().getIndexType()).isEqualTo(IndexParam.IndexType.AUTOINDEX);
        assertThat(operations.createIndexRequest.getIndexParams().getFirst().getMetricType()).isEqualTo(IndexParam.MetricType.COSINE);
        assertThat(operations.loadCollectionRequest).isNotNull();
        assertThat(operations.loadCollectionRequest.getCollectionName()).isEqualTo("safeops_knowledge");
        assertThat(operations.upsertRequest).isNotNull();
    }

    @Test
    void shouldUseConfiguredMetricTypeWhenCreatingIndex() {
        FakeMilvusOperations operations = new FakeMilvusOperations();
        operations.collectionExists = false;
        RagProperties properties = validProperties();
        properties.setMilvusAutoCreateCollection(true);
        properties.setMilvusMetricType("ip");
        MilvusVectorStoreClient client = new MilvusVectorStoreClient(properties, operations);

        client.upsert(List.of(record("runbook-redis#chunk-0")));

        assertThat(operations.createIndexRequest.getIndexParams().getFirst().getMetricType()).isEqualTo(IndexParam.MetricType.IP);
    }

    @Test
    void shouldUseConfiguredHnswIndexParamsWhenCreatingIndex() {
        FakeMilvusOperations operations = new FakeMilvusOperations();
        operations.collectionExists = false;
        RagProperties properties = validProperties();
        properties.setMilvusAutoCreateCollection(true);
        properties.setMilvusIndexType("hnsw");
        properties.setMilvusHnswM(32);
        properties.setMilvusHnswEfConstruction(300);
        MilvusVectorStoreClient client = new MilvusVectorStoreClient(properties, operations);

        client.upsert(List.of(record("runbook-redis#chunk-0")));

        IndexParam indexParam = operations.createIndexRequest.getIndexParams().getFirst();
        assertThat(indexParam.getIndexType()).isEqualTo(IndexParam.IndexType.HNSW);
        assertThat(indexParam.getExtraParams()).containsEntry("M", 32).containsEntry("efConstruction", 300);
    }

    @Test
    void shouldUseConfiguredIvfFlatIndexParamsWhenCreatingIndex() {
        FakeMilvusOperations operations = new FakeMilvusOperations();
        operations.collectionExists = false;
        RagProperties properties = validProperties();
        properties.setMilvusAutoCreateCollection(true);
        properties.setMilvusIndexType("ivf-flat");
        properties.setMilvusIvfFlatNlist(256);
        MilvusVectorStoreClient client = new MilvusVectorStoreClient(properties, operations);

        client.upsert(List.of(record("runbook-redis#chunk-0")));

        IndexParam indexParam = operations.createIndexRequest.getIndexParams().getFirst();
        assertThat(indexParam.getIndexType()).isEqualTo(IndexParam.IndexType.IVF_FLAT);
        assertThat(indexParam.getExtraParams()).containsEntry("nlist", 256);
    }

    @Test
    void shouldFailClosedWhenCollectionIsMissingAndAutoCreateIsDisabled() {
        FakeMilvusOperations operations = new FakeMilvusOperations();
        operations.collectionExists = false;
        MilvusVectorStoreClient client = new MilvusVectorStoreClient(validProperties(), operations);

        assertThatThrownBy(() -> client.upsert(List.of(record("runbook-redis#chunk-0"))))
                .isInstanceOfSatisfying(RagProviderException.class, exception ->
                        assertThat(exception.code()).isEqualTo(RagProviderErrorCode.RAG_PROVIDER_MISCONFIGURED)
                )
                .hasMessageContaining("auto-create is disabled");
    }

    @Test
    void shouldFailFastWhenExistingCollectionHasWrongEmbeddingDimension() {
        FakeMilvusOperations operations = new FakeMilvusOperations();
        operations.describeCollectionResponse = describeCollectionResponse(4);
        MilvusVectorStoreClient client = new MilvusVectorStoreClient(validProperties(), operations);

        assertThatThrownBy(() -> client.search(new VectorStoreQuery(new EmbeddingVector(new float[]{1.0f, 0.0f, 0.0f}), 5, Map.of())))
                .isInstanceOfSatisfying(RagProviderException.class, exception ->
                        assertThat(exception.code()).isEqualTo(RagProviderErrorCode.RAG_PROVIDER_MISCONFIGURED)
                )
                .hasMessageContaining("schema is incompatible")
                .hasMessageContaining("dimension");
    }

    @Test
    void shouldFailFastWhenExistingCollectionMissesRequiredField() {
        FakeMilvusOperations operations = new FakeMilvusOperations();
        operations.describeCollectionResponse = DescribeCollectionResp.builder()
                .collectionName("safeops_knowledge")
                .collectionSchema(CreateCollectionReq.CollectionSchema.builder()
                        .fieldSchemaList(List.of(
                                varcharField(MilvusVectorStoreClient.FIELD_KNOWLEDGE_ID, 512, true),
                                vectorField(3)
                        ))
                        .build())
                .build();
        MilvusVectorStoreClient client = new MilvusVectorStoreClient(validProperties(), operations);

        assertThatThrownBy(() -> client.search(new VectorStoreQuery(new EmbeddingVector(new float[]{1.0f, 0.0f, 0.0f}), 5, Map.of())))
                .isInstanceOfSatisfying(RagProviderException.class, exception ->
                        assertThat(exception.code()).isEqualTo(RagProviderErrorCode.RAG_PROVIDER_MISCONFIGURED)
                )
                .hasMessageContaining("missing field")
                .hasMessageContaining(MilvusVectorStoreClient.FIELD_SOURCE_TYPE);
    }

    @Test
    void shouldSkipExistingIndexValidationByDefault() {
        FakeMilvusOperations operations = new FakeMilvusOperations();
        MilvusVectorStoreClient client = new MilvusVectorStoreClient(validProperties(), operations);

        client.search(new VectorStoreQuery(new EmbeddingVector(new float[]{1.0f, 0.0f, 0.0f}), 5, Map.of()));

        assertThat(operations.describeIndexRequest).isNull();
    }

    @Test
    void shouldValidateExistingIndexWhenEnabled() {
        FakeMilvusOperations operations = new FakeMilvusOperations();
        RagProperties properties = validProperties();
        properties.setMilvusValidateIndex(true);
        MilvusVectorStoreClient client = new MilvusVectorStoreClient(properties, operations);

        client.search(new VectorStoreQuery(new EmbeddingVector(new float[]{1.0f, 0.0f, 0.0f}), 5, Map.of()));

        assertThat(operations.describeIndexRequest).isNotNull();
        assertThat(operations.describeIndexRequest.getCollectionName()).isEqualTo("safeops_knowledge");
        assertThat(operations.describeIndexRequest.getFieldName()).isEqualTo(MilvusVectorStoreClient.FIELD_EMBEDDING);
    }

    @Test
    void shouldFailFastWhenExistingIndexHasWrongMetricType() {
        FakeMilvusOperations operations = new FakeMilvusOperations();
        operations.describeIndexResponse = describeIndexResponse(IndexParam.IndexType.AUTOINDEX, IndexParam.MetricType.L2, Map.of());
        RagProperties properties = validProperties();
        properties.setMilvusValidateIndex(true);
        MilvusVectorStoreClient client = new MilvusVectorStoreClient(properties, operations);

        assertThatThrownBy(() -> client.search(new VectorStoreQuery(new EmbeddingVector(new float[]{1.0f, 0.0f, 0.0f}), 5, Map.of())))
                .isInstanceOfSatisfying(RagProviderException.class, exception ->
                        assertThat(exception.code()).isEqualTo(RagProviderErrorCode.RAG_PROVIDER_MISCONFIGURED)
                )
                .hasMessageContaining("index")
                .hasMessageContaining("metric type");
    }

    @Test
    void shouldFailFastWhenExistingHnswIndexHasWrongParam() {
        FakeMilvusOperations operations = new FakeMilvusOperations();
        operations.describeIndexResponse = describeIndexResponse(IndexParam.IndexType.HNSW, IndexParam.MetricType.COSINE, Map.of("M", "16", "efConstruction", "100"));
        RagProperties properties = validProperties();
        properties.setMilvusValidateIndex(true);
        properties.setMilvusIndexType("hnsw");
        properties.setMilvusHnswEfConstruction(300);
        MilvusVectorStoreClient client = new MilvusVectorStoreClient(properties, operations);

        assertThatThrownBy(() -> client.search(new VectorStoreQuery(new EmbeddingVector(new float[]{1.0f, 0.0f, 0.0f}), 5, Map.of())))
                .isInstanceOfSatisfying(RagProviderException.class, exception ->
                        assertThat(exception.code()).isEqualTo(RagProviderErrorCode.RAG_PROVIDER_MISCONFIGURED)
                )
                .hasMessageContaining("efConstruction")
                .hasMessageContaining("300");
    }

    private SearchResp searchResponse() {
        SearchResp.SearchResult result = SearchResp.SearchResult.builder()
                .id("runbook-redis#chunk-0")
                .score(0.91f)
                .entity(Map.of(
                        MilvusVectorStoreClient.FIELD_KNOWLEDGE_ID, "runbook-redis#chunk-0",
                        MilvusVectorStoreClient.FIELD_SOURCE_TYPE, "runbook",
                        MilvusVectorStoreClient.FIELD_TITLE, "Redis memory pressure",
                        MilvusVectorStoreClient.FIELD_CONTENT, "Redis memory pressure troubleshooting.",
                        MilvusVectorStoreClient.FIELD_LAST_UPDATED_AT, "2026-07-01T00:00:00Z",
                        MilvusVectorStoreClient.FIELD_METADATA_JSON, "{\"service\":\"redis\"}"
                ))
                .build();
        return SearchResp.builder().searchResults(List.of(List.of(result))).build();
    }

    private DescribeIndexResp describeIndexResponse(IndexParam.IndexType indexType, IndexParam.MetricType metricType, Map<String, String> extraParams) {
        return DescribeIndexResp.builder()
                .indexDescriptions(List.of(DescribeIndexResp.IndexDesc.builder()
                        .fieldName(MilvusVectorStoreClient.FIELD_EMBEDDING)
                        .indexName(MilvusVectorStoreClient.FIELD_EMBEDDING + "_idx")
                        .indexType(indexType)
                        .metricType(metricType)
                        .extraParams(extraParams)
                        .build()))
                .build();
    }

    private DescribeCollectionResp describeCollectionResponse(int dimension) {
        return DescribeCollectionResp.builder()
                .collectionName("safeops_knowledge")
                .collectionSchema(CreateCollectionReq.CollectionSchema.builder()
                        .fieldSchemaList(List.of(
                                varcharField(MilvusVectorStoreClient.FIELD_KNOWLEDGE_ID, 512, true),
                                varcharField(MilvusVectorStoreClient.FIELD_SOURCE_TYPE, 128, false),
                                varcharField(MilvusVectorStoreClient.FIELD_TITLE, 1_024, false),
                                varcharField(MilvusVectorStoreClient.FIELD_CONTENT, 8_192, false),
                                vectorField(dimension),
                                varcharField(MilvusVectorStoreClient.FIELD_LAST_UPDATED_AT, 64, false),
                                varcharField(MilvusVectorStoreClient.FIELD_METADATA_JSON, 8_192, false)
                        ))
                        .build())
                .build();
    }

    private CreateCollectionReq.FieldSchema varcharField(String name, int maxLength, boolean primaryKey) {
        return CreateCollectionReq.FieldSchema.builder()
                .name(name)
                .dataType(DataType.VarChar)
                .maxLength(maxLength)
                .isPrimaryKey(primaryKey)
                .build();
    }

    private CreateCollectionReq.FieldSchema vectorField(int dimension) {
        return CreateCollectionReq.FieldSchema.builder()
                .name(MilvusVectorStoreClient.FIELD_EMBEDDING)
                .dataType(DataType.FloatVector)
                .dimension(dimension)
                .build();
    }

    private VectorStoreRecord record(String knowledgeId) {
        return new VectorStoreRecord(
                knowledgeId,
                "runbook",
                "Redis memory pressure",
                "Redis memory pressure troubleshooting.",
                new EmbeddingVector(new float[]{1.0f, 0.0f, 0.0f}),
                Instant.parse("2026-07-01T00:00:00Z"),
                Map.of("service", "redis")
        );
    }

    private RagProperties validProperties() {
        RagProperties properties = new RagProperties();
        properties.setVectorStoreProvider("milvus");
        properties.setMilvusUri("http://localhost:19530");
        properties.setMilvusCollection("safeops_knowledge");
        properties.setMilvusDimension(3);
        return properties;
    }

    private class FakeMilvusOperations implements MilvusVectorClientOperations {
        private UpsertReq upsertRequest;
        private SearchReq searchRequest;
        private CreateCollectionReq createCollectionRequest;
        private CreateIndexReq createIndexRequest;
        private DescribeIndexReq describeIndexRequest;
        private LoadCollectionReq loadCollectionRequest;
        private DescribeCollectionResp describeCollectionResponse;
        private DescribeIndexResp describeIndexResponse;
        private SearchResp searchResponse = SearchResp.builder().searchResults(List.of()).build();
        private RuntimeException upsertFailure;
        private boolean collectionExists = true;

        @Override
        public boolean hasCollection(HasCollectionReq request) {
            return collectionExists;
        }

        @Override
        public void createCollection(CreateCollectionReq request) {
            this.createCollectionRequest = request;
            this.collectionExists = true;
        }

        @Override
        public void createIndex(CreateIndexReq request) {
            this.createIndexRequest = request;
        }

        @Override
        public DescribeIndexResp describeIndex(DescribeIndexReq request) {
            this.describeIndexRequest = request;
            return describeIndexResponse == null
                    ? describeIndexResponse(IndexParam.IndexType.AUTOINDEX, IndexParam.MetricType.COSINE, Map.of())
                    : describeIndexResponse;
        }

        @Override
        public DescribeCollectionResp describeCollection(DescribeCollectionReq request) {
            return describeCollectionResponse == null ? describeCollectionResponse(3) : describeCollectionResponse;
        }

        @Override
        public void loadCollection(LoadCollectionReq request) {
            this.loadCollectionRequest = request;
        }

        @Override
        public void upsert(UpsertReq request) {
            if (upsertFailure != null) {
                throw upsertFailure;
            }
            this.upsertRequest = request;
        }

        @Override
        public SearchResp search(SearchReq request) {
            this.searchRequest = request;
            return searchResponse;
        }
    }
}
