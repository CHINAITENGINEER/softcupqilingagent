package com.cup.opsagent.api;

import com.cup.opsagent.rag.DeterministicEmbeddingClient;
import io.milvus.v2.client.ConnectConfig;
import io.milvus.v2.client.MilvusClientV2;
import io.milvus.v2.common.IndexParam;
import io.milvus.v2.service.collection.request.DropCollectionReq;
import io.milvus.v2.service.collection.request.HasCollectionReq;
import io.milvus.v2.service.index.request.DescribeIndexReq;
import io.milvus.v2.service.index.response.DescribeIndexResp;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.greaterThan;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "ops-agent.rag.retriever-mode=vector",
        "ops-agent.rag.embedding-provider=deterministic",
        "ops-agent.rag.vector-store-provider=milvus",
        "ops-agent.rag.milvus-dimension=" + DeterministicEmbeddingClient.DEFAULT_DIMENSIONS,
        "ops-agent.rag.milvus-index-type=hnsw",
        "ops-agent.rag.milvus-metric-type=cosine",
        "ops-agent.rag.milvus-hnsw-m=16",
        "ops-agent.rag.milvus-hnsw-ef-construction=200",
        "ops-agent.rag.milvus-hnsw-ef=64",
        "ops-agent.rag.milvus-validate-index=true",
        "ops-agent.rag.milvus-auto-create-collection=true",
        "ops-agent.rag.milvus-auto-load-collection=true"
})
@AutoConfigureMockMvc
@EnabledIfEnvironmentVariable(named = "SAFEOPS_RAG_MILVUS_IT", matches = "true")
class RagMilvusKnowledgeIntegrationTest {

    private static final String DEFAULT_MILVUS_URI = "http://localhost:19530";
    private static final String DEFAULT_COLLECTION = "safeops_knowledge_it_" + System.currentTimeMillis();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @DynamicPropertySource
    static void milvusProperties(DynamicPropertyRegistry registry) {
        registry.add("ops-agent.rag.milvus-uri", () -> envOrDefault("SAFEOPS_RAG_MILVUS_URI", DEFAULT_MILVUS_URI));
        registry.add("ops-agent.rag.milvus-token", () -> envOrDefault("SAFEOPS_RAG_MILVUS_TOKEN", ""));
        registry.add("ops-agent.rag.milvus-collection", () -> envOrDefault("SAFEOPS_RAG_MILVUS_COLLECTION", DEFAULT_COLLECTION));
    }

    @AfterEach
    void dropTemporaryCollection() {
        String collection = envOrDefault("SAFEOPS_RAG_MILVUS_COLLECTION", DEFAULT_COLLECTION);
        if (shouldKeepCollection(collection, "safeops_knowledge_it_")) {
            return;
        }
        MilvusClientV2 client = new MilvusClientV2(ConnectConfig.builder()
                .uri(envOrDefault("SAFEOPS_RAG_MILVUS_URI", DEFAULT_MILVUS_URI))
                .token(envOrDefault("SAFEOPS_RAG_MILVUS_TOKEN", ""))
                .build());
        try {
            if (client.hasCollection(HasCollectionReq.builder().collectionName(collection).build())) {
                client.dropCollection(DropCollectionReq.builder()
                        .collectionName(collection)
                        .build());
            }
        } finally {
            client.close();
        }
    }

    @Test
    void shouldIngestAndSearchKnowledgeThroughRealMilvus() throws Exception {
        String suffix = String.valueOf(System.currentTimeMillis());
        String knowledgeId = "runbook-milvus-it-" + suffix;

        mockMvc.perform(post("/api/rag/knowledge/ingest")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("documents", java.util.List.of(Map.of(
                                "knowledgeId", knowledgeId,
                                "sourceType", "runbook",
                                "title", "Milvus Redis memory runbook",
                                "content", "Redis memory pressure troubleshooting for Milvus integration test. Check eviction policy and used_memory.",
                                "metadata", Map.of("service", "redis", "testRun", suffix)
                        ))))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.chunkCount").value(1));

        assertEmbeddingIndexCreatedWithConfiguredHnswParams();

        mockMvc.perform(get("/api/rag/knowledge/search")
                        .param("q", "redis memory eviction")
                        .param("maxResults", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resultCount", greaterThan(0)))
                .andExpect(jsonPath("$.results[0].knowledgeId").value(knowledgeId + "#chunk-0"))
                .andExpect(jsonPath("$.results[0].metadata.parentKnowledgeId").value(knowledgeId))
                .andExpect(jsonPath("$.results[0].metadata.service").value("redis"));
    }

    private void assertEmbeddingIndexCreatedWithConfiguredHnswParams() {
        MilvusClientV2 client = new MilvusClientV2(ConnectConfig.builder()
                .uri(envOrDefault("SAFEOPS_RAG_MILVUS_URI", DEFAULT_MILVUS_URI))
                .token(envOrDefault("SAFEOPS_RAG_MILVUS_TOKEN", ""))
                .build());
        try {
            DescribeIndexResp response = client.describeIndex(DescribeIndexReq.builder()
                    .collectionName(envOrDefault("SAFEOPS_RAG_MILVUS_COLLECTION", DEFAULT_COLLECTION))
                    .fieldName("embedding")
                    .build());
            DescribeIndexResp.IndexDesc indexDesc = response.getIndexDescByFieldName("embedding");

            assertThat(indexDesc).isNotNull();
            assertThat(indexDesc.getIndexType()).isEqualTo(IndexParam.IndexType.HNSW);
            assertThat(indexDesc.getMetricType()).isEqualTo(IndexParam.MetricType.COSINE);
            assertThat(indexDesc.getExtraParams()).containsKeys("M", "efConstruction");
            assertThat(String.valueOf(indexDesc.getExtraParams().get("M"))).isEqualTo("16");
            assertThat(String.valueOf(indexDesc.getExtraParams().get("efConstruction"))).isEqualTo("200");
        } finally {
            client.close();
        }
    }

    private static boolean shouldKeepCollection(String collection, String temporaryPrefix) {
        return Boolean.parseBoolean(envOrDefault("SAFEOPS_RAG_MILVUS_KEEP_COLLECTION", "false"))
                || !collection.startsWith(temporaryPrefix);
    }

    private static String envOrDefault(String name, String defaultValue) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? defaultValue : value;
    }

    private String json(Object value) throws Exception {
        return objectMapper.writeValueAsString(value);
    }
}
