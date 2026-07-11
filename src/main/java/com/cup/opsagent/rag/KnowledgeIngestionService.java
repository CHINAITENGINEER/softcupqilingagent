package com.cup.opsagent.rag;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class KnowledgeIngestionService {

    public static final int DEFAULT_CHUNK_MAX_LENGTH = 1000;

    private final EmbeddingClient embeddingClient;
    private final VectorStoreClient vectorStoreClient;
    private final int chunkMaxLength;

    public KnowledgeIngestionService(EmbeddingClient embeddingClient, VectorStoreClient vectorStoreClient) {
        this(embeddingClient, vectorStoreClient, DEFAULT_CHUNK_MAX_LENGTH);
    }

    public KnowledgeIngestionService(EmbeddingClient embeddingClient, VectorStoreClient vectorStoreClient, int chunkMaxLength) {
        if (embeddingClient == null) {
            throw new IllegalArgumentException("embeddingClient must not be null");
        }
        if (vectorStoreClient == null) {
            throw new IllegalArgumentException("vectorStoreClient must not be null");
        }
        this.embeddingClient = embeddingClient;
        this.vectorStoreClient = vectorStoreClient;
        this.chunkMaxLength = chunkMaxLength <= 0 ? DEFAULT_CHUNK_MAX_LENGTH : chunkMaxLength;
    }

    public KnowledgeIngestionResult ingest(List<KnowledgeDocument> documents) {
        if (documents == null || documents.isEmpty()) {
            return new KnowledgeIngestionResult(0, 0, List.of(), List.of());
        }
        List<KnowledgeDocument> validDocuments = documents.stream()
                .filter(this::isIngestible)
                .toList();
        List<KnowledgeChunk> chunks = validDocuments.stream()
                .flatMap(document -> chunk(document).stream())
                .toList();
        List<VectorStoreRecord> records = chunks.stream()
                .map(this::toVectorStoreRecord)
                .filter(record -> !record.embedding().isEmpty())
                .toList();
        vectorStoreClient.upsert(records);
        return new KnowledgeIngestionResult(
                validDocuments.size(),
                records.size(),
                validDocuments.stream().map(KnowledgeDocument::knowledgeId).toList(),
                records.stream().map(VectorStoreRecord::knowledgeId).toList()
        );
    }

    public List<KnowledgeChunk> chunk(KnowledgeDocument document) {
        if (!isIngestible(document)) {
            return List.of();
        }
        List<String> chunkTexts = splitContent(document.content());
        List<KnowledgeChunk> chunks = new ArrayList<>();
        for (int index = 0; index < chunkTexts.size(); index++) {
            String chunkText = chunkTexts.get(index);
            chunks.add(new KnowledgeChunk(
                    chunkId(document.knowledgeId(), index),
                    document.knowledgeId(),
                    document.sourceType(),
                    document.title(),
                    chunkText,
                    index,
                    document.lastUpdatedAt(),
                    chunkMetadata(document, index, chunkTexts.size())
            ));
        }
        return chunks;
    }

    private boolean isIngestible(KnowledgeDocument document) {
        return document != null
                && document.knowledgeId() != null
                && !document.knowledgeId().isBlank()
                && document.content() != null
                && !document.content().isBlank();
    }

    private VectorStoreRecord toVectorStoreRecord(KnowledgeChunk chunk) {
        return new VectorStoreRecord(
                chunk.chunkId(),
                chunk.sourceType(),
                chunk.title(),
                chunk.content(),
                embeddingClient.embed(chunk.content()),
                chunk.lastUpdatedAt(),
                chunk.metadata()
        );
    }

    private List<String> splitContent(String content) {
        String normalized = content == null ? "" : content.strip();
        if (normalized.isBlank()) {
            return List.of();
        }
        List<String> chunks = new ArrayList<>();
        for (String paragraph : normalized.split("\\R\\s*\\R")) {
            appendParagraphChunks(chunks, paragraph.strip());
        }
        return chunks;
    }

    private void appendParagraphChunks(List<String> chunks, String paragraph) {
        if (paragraph.isBlank()) {
            return;
        }
        if (paragraph.length() <= chunkMaxLength) {
            chunks.add(paragraph);
            return;
        }
        for (int start = 0; start < paragraph.length(); start += chunkMaxLength) {
            int end = Math.min(start + chunkMaxLength, paragraph.length());
            chunks.add(paragraph.substring(start, end).strip());
        }
    }

    private Map<String, String> chunkMetadata(KnowledgeDocument document, int chunkIndex, int chunkCount) {
        Map<String, String> metadata = new LinkedHashMap<>(document.metadata());
        metadata.put("parentKnowledgeId", document.knowledgeId());
        metadata.put("chunkIndex", String.valueOf(chunkIndex));
        metadata.put("chunkCount", String.valueOf(chunkCount));
        return Map.copyOf(metadata);
    }

    private String chunkId(String knowledgeId, int chunkIndex) {
        return knowledgeId + "#chunk-" + chunkIndex;
    }
}
