package com.cup.opsagent.rag;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.reflect.TypeToken;
import io.milvus.v2.common.ConsistencyLevel;
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
import io.milvus.v2.service.vector.request.data.FloatVec;
import io.milvus.v2.service.vector.response.SearchResp;

import java.lang.reflect.Type;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.regex.Pattern;

public class MilvusVectorStoreClient implements VectorStoreClient {

    static final String FIELD_KNOWLEDGE_ID = "knowledge_id";
    static final String FIELD_SOURCE_TYPE = "source_type";
    static final String FIELD_TITLE = "title";
    static final String FIELD_CONTENT = "content";
    static final String FIELD_EMBEDDING = "embedding";
    static final String FIELD_LAST_UPDATED_AT = "last_updated_at";
    static final String FIELD_METADATA_JSON = "metadata_json";
    static final String METADATA_FIELD_PREFIX = "metadata_";

    private static final int KNOWLEDGE_ID_MAX_LENGTH = 512;
    private static final int SOURCE_TYPE_MAX_LENGTH = 128;
    private static final int TITLE_MAX_LENGTH = 1_024;
    private static final int CONTENT_MAX_LENGTH = 8_192;
    private static final int LAST_UPDATED_AT_MAX_LENGTH = 64;
    private static final int METADATA_JSON_MAX_LENGTH = 8_192;
    private static final Pattern SAFE_FIELD_NAME = Pattern.compile("[^A-Za-z0-9_]");
    private static final Pattern SAFE_FIELD_COLLAPSE = Pattern.compile("_+");
    private static final Type METADATA_MAP_TYPE = new TypeToken<Map<String, String>>() {
    }.getType();

    private final RagProperties properties;
    private final MilvusVectorClientOperations client;
    private final Gson gson = new Gson();
    private boolean collectionEnsured;

    public MilvusVectorStoreClient(RagProperties properties, MilvusVectorClientOperations client) {
        this.properties = properties;
        this.client = client;
    }

    @Override
    public void upsert(List<VectorStoreRecord> records) {
        properties.validateVectorStoreProviderConfig();
        if (records == null || records.isEmpty()) {
            return;
        }
        List<VectorStoreRecord> validRecords = records.stream()
                .filter(Objects::nonNull)
                .filter(record -> !record.knowledgeId().isBlank() && !record.embedding().isEmpty())
                .toList();
        for (VectorStoreRecord record : validRecords) {
            validateEmbeddingDimension(record.embedding(), "record " + record.knowledgeId());
        }
        List<JsonObject> rows = validRecords.stream()
                .map(this::toJsonObject)
                .toList();
        if (rows.isEmpty()) {
            return;
        }
        ensureCollection();
        try {
            client.upsert(UpsertReq.builder()
                    .collectionName(properties.getMilvusCollection())
                    .data(rows)
                    .build());
        } catch (RuntimeException exception) {
            throw new RagProviderException(RagProviderErrorCode.RAG_PROVIDER_SERVER_ERROR, "Milvus vector upsert failed", exception);
        }
    }

    @Override
    public List<VectorSearchResult> search(VectorStoreQuery query) {
        properties.validateVectorStoreProviderConfig();
        if (query == null || query.embedding().isEmpty()) {
            return List.of();
        }
        validateEmbeddingDimension(query.embedding(), "query");
        ensureCollection();
        try {
            int limit = searchLimit(query.maxResults());
            SearchReq searchRequest = SearchReq.builder()
                    .collectionName(properties.getMilvusCollection())
                    .annsField(FIELD_EMBEDDING)
                    .data(List.of(new FloatVec(toFloatList(query.embedding()))))
                    .metricType(milvusMetricType())
                    .consistencyLevel(ConsistencyLevel.STRONG)
                    .filter(toFilterExpression(query.filters()))
                    .limit(limit)
                    .outputFields(outputFields(query.filters()))
                    .build();
            Map<String, Object> searchParams = milvusSearchParams();
            searchRequest.setTopK(limit);
            if (!searchParams.isEmpty()) {
                searchRequest.setSearchParams(searchParams);
            }
            SearchResp response = client.search(searchRequest);
            return fromSearchResponse(response);
        } catch (RuntimeException exception) {
            throw new RagProviderException(RagProviderErrorCode.RAG_PROVIDER_SERVER_ERROR, "Milvus vector search failed", exception);
        }
    }

    private int searchLimit(int requestedMaxResults) {
        int configuredMaxResults = properties.getMaxResults();
        int safeConfiguredMaxResults = configuredMaxResults <= 0 ? RagProperties.DEFAULT_MAX_RESULTS : configuredMaxResults;
        return Math.min(requestedMaxResults, safeConfiguredMaxResults);
    }

    private Map<String, Object> milvusSearchParams() {
        if (properties.milvusIndexType() == RagMilvusIndexType.HNSW) {
            return Map.of("ef", properties.getMilvusHnswEf());
        }
        if (properties.milvusIndexType() == RagMilvusIndexType.IVF_FLAT) {
            return Map.of("nprobe", properties.getMilvusIvfFlatNprobe());
        }
        return Map.of();
    }

    private void validateEmbeddingDimension(EmbeddingVector embedding, String source) {
        int expected = properties.getMilvusDimension();
        int actual = embedding.dimensions();
        if (actual != expected) {
            throw new RagProviderException(
                    RagProviderErrorCode.RAG_PROVIDER_MISCONFIGURED,
                    "Milvus embedding dimension mismatch for " + source + ": expected " + expected + " but was " + actual
            );
        }
    }

    private synchronized void ensureCollection() {
        if (collectionEnsured) {
            return;
        }
        try {
            boolean exists = client.hasCollection(HasCollectionReq.builder()
                    .collectionName(properties.getMilvusCollection())
                    .build());
            if (!exists) {
                if (!properties.isMilvusAutoCreateCollection()) {
                    throw new RagProviderException(
                            RagProviderErrorCode.RAG_PROVIDER_MISCONFIGURED,
                            "Milvus collection does not exist and auto-create is disabled: " + properties.getMilvusCollection()
                    );
                }
                createCollection();
                createEmbeddingIndex();
            } else {
                validateCollectionSchema();
                validateIndexIfEnabled();
            }
            if (properties.isMilvusAutoLoadCollection()) {
                client.loadCollection(LoadCollectionReq.builder()
                        .collectionName(properties.getMilvusCollection())
                        .build());
            }
            collectionEnsured = true;
        } catch (RagProviderException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new RagProviderException(RagProviderErrorCode.RAG_PROVIDER_SERVER_ERROR, "Milvus collection ensure failed", exception);
        }
    }

    private void validateCollectionSchema() {
        DescribeCollectionResp response = client.describeCollection(DescribeCollectionReq.builder()
                .collectionName(properties.getMilvusCollection())
                .build());
        CreateCollectionReq.CollectionSchema schema = response.getCollectionSchema();
        if (schema == null) {
            throw schemaMismatch("missing collection schema");
        }
        requireField(schema, FIELD_KNOWLEDGE_ID, DataType.VarChar, null, true);
        requireField(schema, FIELD_SOURCE_TYPE, DataType.VarChar, null, false);
        requireField(schema, FIELD_TITLE, DataType.VarChar, null, false);
        requireField(schema, FIELD_CONTENT, DataType.VarChar, null, false);
        requireField(schema, FIELD_LAST_UPDATED_AT, DataType.VarChar, null, false);
        requireField(schema, FIELD_METADATA_JSON, DataType.VarChar, null, false);
        requireField(schema, FIELD_EMBEDDING, DataType.FloatVector, properties.getMilvusDimension(), false);
    }

    private void validateIndexIfEnabled() {
        if (!properties.isMilvusValidateIndex()) {
            return;
        }
        DescribeIndexResp response = client.describeIndex(DescribeIndexReq.builder()
                .collectionName(properties.getMilvusCollection())
                .fieldName(FIELD_EMBEDDING)
                .build());
        DescribeIndexResp.IndexDesc indexDesc = response == null ? null : response.getIndexDescByFieldName(FIELD_EMBEDDING);
        if (indexDesc == null) {
            throw indexMismatch("missing embedding index");
        }
        if (indexDesc.getIndexType() != milvusIndexType()) {
            throw indexMismatch("index type must be " + milvusIndexType() + " but was " + indexDesc.getIndexType());
        }
        if (indexDesc.getMetricType() != milvusMetricType()) {
            throw indexMismatch("metric type must be " + milvusMetricType() + " but was " + indexDesc.getMetricType());
        }
        validateIndexExtraParams(indexDesc.getExtraParams());
    }

    private void validateIndexExtraParams(Map<String, ?> actualParams) {
        Map<String, Object> expectedParams = milvusIndexExtraParams();
        if (expectedParams.isEmpty()) {
            return;
        }
        Map<String, ?> safeActualParams = actualParams == null ? Map.of() : actualParams;
        for (Map.Entry<String, Object> entry : expectedParams.entrySet()) {
            Object actualValue = safeActualParams.get(entry.getKey());
            if (!Objects.equals(String.valueOf(entry.getValue()), String.valueOf(actualValue))) {
                throw indexMismatch("index param " + entry.getKey() + " must be " + entry.getValue() + " but was " + actualValue);
            }
        }
    }

    private RagProviderException indexMismatch(String reason) {
        return new RagProviderException(
                RagProviderErrorCode.RAG_PROVIDER_MISCONFIGURED,
                "Milvus embedding index is incompatible for " + properties.getMilvusCollection() + ": " + reason
        );
    }

    private void requireField(
            CreateCollectionReq.CollectionSchema schema,
            String fieldName,
            DataType expectedDataType,
            Integer expectedDimension,
            boolean expectedPrimaryKey
    ) {
        CreateCollectionReq.FieldSchema field = schema.getField(fieldName);
        if (field == null) {
            throw schemaMismatch("missing field: " + fieldName);
        }
        if (field.getDataType() != expectedDataType) {
            throw schemaMismatch("field " + fieldName + " type must be " + expectedDataType + " but was " + field.getDataType());
        }
        if (expectedDimension != null && !Objects.equals(field.getDimension(), expectedDimension)) {
            throw schemaMismatch("field " + fieldName + " dimension must be " + expectedDimension + " but was " + field.getDimension());
        }
        if (expectedPrimaryKey && !Boolean.TRUE.equals(field.getIsPrimaryKey())) {
            throw schemaMismatch("field " + fieldName + " must be the primary key");
        }
    }

    private RagProviderException schemaMismatch(String reason) {
        return new RagProviderException(
                RagProviderErrorCode.RAG_PROVIDER_MISCONFIGURED,
                "Milvus collection schema is incompatible for " + properties.getMilvusCollection() + ": " + reason
        );
    }

    private void createCollection() {
        client.createCollection(CreateCollectionReq.builder()
                .collectionName(properties.getMilvusCollection())
                .collectionSchema(CreateCollectionReq.CollectionSchema.builder()
                        .enableDynamicField(true)
                        .fieldSchemaList(List.of(
                                varcharField(FIELD_KNOWLEDGE_ID, KNOWLEDGE_ID_MAX_LENGTH, true),
                                varcharField(FIELD_SOURCE_TYPE, SOURCE_TYPE_MAX_LENGTH, false),
                                varcharField(FIELD_TITLE, TITLE_MAX_LENGTH, false),
                                varcharField(FIELD_CONTENT, CONTENT_MAX_LENGTH, false),
                                vectorField(),
                                varcharField(FIELD_LAST_UPDATED_AT, LAST_UPDATED_AT_MAX_LENGTH, false),
                                varcharField(FIELD_METADATA_JSON, METADATA_JSON_MAX_LENGTH, false)
                        ))
                        .build())
                .build());
    }

    private CreateCollectionReq.FieldSchema varcharField(String name, int maxLength, boolean primaryKey) {
        return CreateCollectionReq.FieldSchema.builder()
                .name(name)
                .dataType(DataType.VarChar)
                .isPrimaryKey(primaryKey)
                .maxLength(maxLength)
                .build();
    }

    private CreateCollectionReq.FieldSchema vectorField() {
        return CreateCollectionReq.FieldSchema.builder()
                .name(FIELD_EMBEDDING)
                .dataType(DataType.FloatVector)
                .dimension(properties.getMilvusDimension())
                .build();
    }

    private void createEmbeddingIndex() {
        IndexParam indexParam = IndexParam.builder()
                .fieldName(FIELD_EMBEDDING)
                .indexName(FIELD_EMBEDDING + "_idx")
                .indexType(milvusIndexType())
                .metricType(milvusMetricType())
                .build();
        Map<String, Object> extraParams = milvusIndexExtraParams();
        if (!extraParams.isEmpty()) {
            indexParam.setExtraParams(extraParams);
        }
        client.createIndex(CreateIndexReq.builder()
                .collectionName(properties.getMilvusCollection())
                .indexParams(List.of(indexParam))
                .build());
    }

    private Map<String, Object> milvusIndexExtraParams() {
        if (properties.milvusIndexType() == RagMilvusIndexType.HNSW) {
            return Map.of(
                    "M", properties.getMilvusHnswM(),
                    "efConstruction", properties.getMilvusHnswEfConstruction()
            );
        }
        if (properties.milvusIndexType() == RagMilvusIndexType.IVF_FLAT) {
            return Map.of("nlist", properties.getMilvusIvfFlatNlist());
        }
        return Map.of();
    }

    private IndexParam.IndexType milvusIndexType() {
        return IndexParam.IndexType.valueOf(properties.milvusIndexType().name());
    }

    private IndexParam.MetricType milvusMetricType() {
        return IndexParam.MetricType.valueOf(properties.milvusMetricType().name());
    }

    private JsonObject toJsonObject(VectorStoreRecord record) {
        JsonObject row = new JsonObject();
        row.addProperty(FIELD_KNOWLEDGE_ID, record.knowledgeId());
        row.addProperty(FIELD_SOURCE_TYPE, record.sourceType());
        row.addProperty(FIELD_TITLE, record.title());
        row.addProperty(FIELD_CONTENT, record.content());
        row.add(FIELD_EMBEDDING, gson.toJsonTree(toFloatList(record.embedding())));
        row.addProperty(FIELD_LAST_UPDATED_AT, record.lastUpdatedAt().toString());
        row.addProperty(FIELD_METADATA_JSON, gson.toJson(record.metadata()));
        record.metadata().forEach((key, value) -> {
            String fieldName = toMetadataFieldName(key);
            if (!fieldName.isBlank()) {
                row.addProperty(fieldName, value);
            }
        });
        return row;
    }

    private List<VectorSearchResult> fromSearchResponse(SearchResp response) {
        if (response == null || response.getSearchResults() == null || response.getSearchResults().isEmpty()) {
            return List.of();
        }
        List<VectorSearchResult> results = new ArrayList<>();
        for (List<SearchResp.SearchResult> group : response.getSearchResults()) {
            if (group == null) {
                continue;
            }
            for (SearchResp.SearchResult result : group) {
                if (result == null || result.getEntity() == null) {
                    continue;
                }
                JsonObject entity = gson.toJsonTree(result.getEntity()).getAsJsonObject();
                results.add(toSearchResult(result, entity));
            }
        }
        return results.stream()
                .sorted(Comparator.comparingDouble(VectorSearchResult::score).reversed()
                        .thenComparing(result -> result.record().knowledgeId()))
                .toList();
    }

    private VectorSearchResult toSearchResult(SearchResp.SearchResult result, JsonObject entity) {
        VectorStoreRecord record = new VectorStoreRecord(
                stringValue(entity, FIELD_KNOWLEDGE_ID, String.valueOf(result.getId())),
                stringValue(entity, FIELD_SOURCE_TYPE, ""),
                stringValue(entity, FIELD_TITLE, ""),
                stringValue(entity, FIELD_CONTENT, ""),
                new EmbeddingVector(null),
                instantValue(entity, FIELD_LAST_UPDATED_AT),
                metadata(entity)
        );
        return new VectorSearchResult(record, result.getScore());
    }

    private List<String> outputFields(Map<String, String> filters) {
        List<String> fields = new ArrayList<>(List.of(
                FIELD_KNOWLEDGE_ID,
                FIELD_SOURCE_TYPE,
                FIELD_TITLE,
                FIELD_CONTENT,
                FIELD_LAST_UPDATED_AT,
                FIELD_METADATA_JSON
        ));
        if (filters != null) {
            filters.keySet().stream()
                    .map(this::toMetadataFieldName)
                    .filter(fieldName -> !fieldName.isBlank())
                    .distinct()
                    .sorted()
                    .forEach(fields::add);
        }
        return fields;
    }

    private String toFilterExpression(Map<String, String> filters) {
        if (filters == null || filters.isEmpty()) {
            return "";
        }
        Map<String, String> byFieldName = new TreeMap<>();
        filters.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> {
                    String fieldName = toMetadataFieldName(entry.getKey());
                    if (!fieldName.isBlank()) {
                        byFieldName.putIfAbsent(fieldName, entry.getValue());
                    }
                });
        return byFieldName.entrySet().stream()
                .map(entry -> entry.getKey() + " == \"" + escapeFilterValue(entry.getValue()) + "\"")
                .reduce((left, right) -> left + " and " + right)
                .orElse("");
    }

    private String escapeFilterValue(String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        StringBuilder escaped = new StringBuilder(value.length());
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            switch (character) {
                case '\\' -> escaped.append("\\\\");
                case '"' -> escaped.append("\\\"");
                case '\n' -> escaped.append("\\n");
                case '\r' -> escaped.append("\\r");
                case '\t' -> escaped.append("\\t");
                default -> {
                    if (Character.isISOControl(character)) {
                        escaped.append(String.format("\\u%04x", (int) character));
                    } else {
                        escaped.append(character);
                    }
                }
            }
        }
        return escaped.toString();
    }

    private boolean hasUsableMetadataKey(String key) {
        return !toMetadataFieldName(key).isBlank();
    }

    private String toMetadataFieldName(String key) {
        if (key == null || key.isBlank()) {
            return "";
        }
        String safeKey = SAFE_FIELD_NAME.matcher(key.trim()).replaceAll("_");
        safeKey = SAFE_FIELD_COLLAPSE.matcher(safeKey).replaceAll("_");
        safeKey = trimUnderscores(safeKey);
        return safeKey.isBlank() ? "" : METADATA_FIELD_PREFIX + safeKey;
    }

    private String trimUnderscores(String value) {
        int start = 0;
        int end = value.length();
        while (start < end && value.charAt(start) == '_') {
            start++;
        }
        while (end > start && value.charAt(end - 1) == '_') {
            end--;
        }
        return value.substring(start, end);
    }

    private List<Float> toFloatList(EmbeddingVector embedding) {
        float[] values = embedding.values();
        List<Float> result = new ArrayList<>(values.length);
        for (float value : values) {
            result.add(value);
        }
        return result;
    }

    private String stringValue(JsonObject object, String fieldName, String fallback) {
        JsonElement element = object.get(fieldName);
        if (element == null || element.isJsonNull()) {
            return fallback;
        }
        return element.getAsString();
    }

    private Instant instantValue(JsonObject object, String fieldName) {
        String value = stringValue(object, fieldName, "");
        if (value.isBlank()) {
            return Instant.EPOCH;
        }
        try {
            return Instant.parse(value);
        } catch (DateTimeParseException exception) {
            return Instant.EPOCH;
        }
    }

    private Map<String, String> metadata(JsonObject entity) {
        String metadataJson = stringValue(entity, FIELD_METADATA_JSON, "");
        if (!metadataJson.isBlank()) {
            try {
                Map<String, String> parsed = gson.fromJson(metadataJson, METADATA_MAP_TYPE);
                return parsed == null ? Map.of() : parsed;
            } catch (RuntimeException ignored) {
                return Map.of();
            }
        }
        Map<String, String> result = new LinkedHashMap<>();
        for (Map.Entry<String, JsonElement> entry : entity.entrySet()) {
            if (entry.getKey().startsWith(METADATA_FIELD_PREFIX) && entry.getValue() != null && !entry.getValue().isJsonNull()) {
                result.put(entry.getKey().substring(METADATA_FIELD_PREFIX.length()), entry.getValue().getAsString());
            }
        }
        return Map.copyOf(result);
    }
}
