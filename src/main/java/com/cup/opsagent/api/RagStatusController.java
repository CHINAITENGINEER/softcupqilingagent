package com.cup.opsagent.api;

import com.cup.opsagent.rag.InMemoryVectorStoreClient;
import com.cup.opsagent.rag.RagProperties;
import com.cup.opsagent.rag.VectorStoreClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/rag")
public class RagStatusController {

    private final RagProperties ragProperties;
    private final VectorStoreClient vectorStoreClient;

    public RagStatusController(RagProperties ragProperties, VectorStoreClient vectorStoreClient) {
        this.ragProperties = ragProperties;
        this.vectorStoreClient = vectorStoreClient;
    }

    @GetMapping("/status")
    public RagStatusResponse status() {
        return new RagStatusResponse(
                ragProperties.isEnabled(),
                safeValue(ragProperties.getRetrieverMode()),
                safeValue(ragProperties.getEmbeddingProvider()),
                safeValue(ragProperties.getEmbeddingModel()),
                safeValue(ragProperties.getVectorStoreProvider()),
                safeValue(ragProperties.getMilvusUri()),
                safeValue(ragProperties.getMilvusCollection()),
                ragProperties.getMilvusDimension(),
                safeValue(ragProperties.getMilvusMetricType()),
                safeValue(ragProperties.getMilvusIndexType()),
                ragProperties.isMilvusAutoCreateCollection(),
                ragProperties.isMilvusAutoLoadCollection(),
                ragProperties.isMilvusValidateIndex(),
                ragProperties.getMaxResults(),
                ragProperties.getContextMaxLength(),
                ragProperties.getSnippetMaxLength()
        );
    }

    @GetMapping("/stats")
    public RagStatsResponse stats() {
        int vectorRecordCount = vectorStoreClient instanceof InMemoryVectorStoreClient inMemory
                ? inMemory.records().size()
                : -1;
        return new RagStatsResponse(
                safeValue(ragProperties.getVectorStoreProvider()),
                safeValue(ragProperties.getMilvusCollection()),
                vectorRecordCount,
                vectorRecordCount >= 0 ? "known" : "unknown",
                Map.of(
                        "hnswM", ragProperties.getMilvusHnswM(),
                        "hnswEfConstruction", ragProperties.getMilvusHnswEfConstruction(),
                        "hnswEf", ragProperties.getMilvusHnswEf(),
                        "ivfFlatNlist", ragProperties.getMilvusIvfFlatNlist(),
                        "ivfFlatNprobe", ragProperties.getMilvusIvfFlatNprobe()
                )
        );
    }

    private String safeValue(String value) {
        return value == null ? "" : value;
    }

    public record RagStatusResponse(
            boolean enabled,
            String retrieverMode,
            String embeddingProvider,
            String embeddingModel,
            String vectorStoreProvider,
            String milvusUri,
            String milvusCollection,
            int milvusDimension,
            String milvusMetricType,
            String milvusIndexType,
            boolean milvusAutoCreateCollection,
            boolean milvusAutoLoadCollection,
            boolean milvusValidateIndex,
            int maxResults,
            int contextMaxLength,
            int snippetMaxLength
    ) {
    }

    public record RagStatsResponse(
            String vectorStoreProvider,
            String collection,
            int vectorRecordCount,
            String vectorRecordCountStatus,
            Map<String, Integer> indexParameters
    ) {
    }
}
