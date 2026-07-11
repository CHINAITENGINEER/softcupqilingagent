package com.cup.opsagent.rag;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class InMemoryVectorStoreClient implements VectorStoreClient {

    private final Map<String, VectorStoreRecord> records = new LinkedHashMap<>();

    public InMemoryVectorStoreClient() {
    }

    public InMemoryVectorStoreClient(List<VectorStoreRecord> records) {
        upsert(records);
    }

    @Override
    public synchronized void upsert(List<VectorStoreRecord> records) {
        if (records == null || records.isEmpty()) {
            return;
        }
        for (VectorStoreRecord record : records) {
            if (record != null && !record.knowledgeId().isBlank() && !record.embedding().isEmpty()) {
                this.records.put(record.knowledgeId(), record);
            }
        }
    }

    @Override
    public synchronized List<VectorSearchResult> search(VectorStoreQuery query) {
        if (query == null || query.embedding().isEmpty()) {
            return List.of();
        }
        List<VectorSearchResult> results = new ArrayList<>();
        for (VectorStoreRecord record : records.values()) {
            if (!matchesFilters(record, query.filters())) {
                continue;
            }
            double score = cosineSimilarity(query.embedding(), record.embedding());
            if (score > 0) {
                results.add(new VectorSearchResult(record, score));
            }
        }
        return results.stream()
                .sorted(Comparator.comparingDouble(VectorSearchResult::score).reversed()
                        .thenComparing(result -> result.record().knowledgeId()))
                .limit(query.maxResults())
                .toList();
    }

    public synchronized List<VectorStoreRecord> records() {
        return List.copyOf(records.values());
    }

    private boolean matchesFilters(VectorStoreRecord record, Map<String, String> filters) {
        if (filters == null || filters.isEmpty()) {
            return true;
        }
        for (Map.Entry<String, String> filter : filters.entrySet()) {
            String actual = record.metadata().get(filter.getKey());
            if (!filter.getValue().equals(actual)) {
                return false;
            }
        }
        return true;
    }

    private double cosineSimilarity(EmbeddingVector left, EmbeddingVector right) {
        float[] leftValues = left.values();
        float[] rightValues = right.values();
        if (leftValues.length == 0 || leftValues.length != rightValues.length) {
            return 0;
        }
        double dotProduct = 0;
        double leftNorm = 0;
        double rightNorm = 0;
        for (int index = 0; index < leftValues.length; index++) {
            dotProduct += leftValues[index] * rightValues[index];
            leftNorm += leftValues[index] * leftValues[index];
            rightNorm += rightValues[index] * rightValues[index];
        }
        if (leftNorm == 0 || rightNorm == 0) {
            return 0;
        }
        return dotProduct / (Math.sqrt(leftNorm) * Math.sqrt(rightNorm));
    }
}
