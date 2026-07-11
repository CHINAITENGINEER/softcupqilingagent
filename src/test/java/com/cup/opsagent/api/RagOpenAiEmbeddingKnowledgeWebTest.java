package com.cup.opsagent.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.client.RestClient;

import java.util.Map;

import static org.hamcrest.Matchers.greaterThan;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "ops-agent.rag.embedding-provider=openai",
        "ops-agent.rag.embedding-base-url=https://api.example.com/v1",
        "ops-agent.rag.embedding-api-key=sk-validsecret123456789",
        "ops-agent.rag.embedding-model=text-embedding-test",
        "ops-agent.rag.vector-store-provider=in-memory",
        "ops-agent.rag.retriever-mode=vector"
})
@AutoConfigureMockMvc
class RagOpenAiEmbeddingKnowledgeWebTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private MockRestServiceServer embeddingServer;

    @Test
    void shouldIngestAndSearchKnowledgeThroughOpenAiEmbeddingClient() throws Exception {
        expectEmbeddingRequest("Redis memory pressure runbook. Check eviction policy.");
        expectEmbeddingRequest("redis eviction");

        mockMvc.perform(post("/api/rag/knowledge/ingest")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("documents", java.util.List.of(Map.of(
                                "knowledgeId", "runbook-redis-openai-embedding",
                                "sourceType", "runbook",
                                "title", "Redis memory pressure",
                                "content", "Redis memory pressure runbook. Check eviction policy.",
                                "metadata", Map.of("service", "redis")
                        ))))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.chunkCount").value(1));

        mockMvc.perform(get("/api/rag/knowledge/search")
                        .param("q", "redis eviction")
                        .param("maxResults", "3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resultCount", greaterThan(0)))
                .andExpect(jsonPath("$.results[0].knowledgeId").value("runbook-redis-openai-embedding#chunk-0"))
                .andExpect(jsonPath("$.results[0].metadata.parentKnowledgeId").value("runbook-redis-openai-embedding"))
                .andExpect(jsonPath("$.results[0].metadata.service").value("redis"));

        embeddingServer.verify();
    }

    private void expectEmbeddingRequest(String input) {
        embeddingServer.expect(once(), requestTo("https://api.example.com/v1/embeddings"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer sk-validsecret123456789"))
                .andExpect(org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath("$.model").value("text-embedding-test"))
                .andExpect(org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath("$.input").value(input))
                .andRespond(withSuccess("""
                        {
                          "object": "list",
                          "model": "text-embedding-test",
                          "data": [
                            {
                              "object": "embedding",
                              "index": 0,
                              "embedding": [1.0, 0.0, 0.0]
                            }
                          ]
                        }
                        """, MediaType.APPLICATION_JSON));
    }

    private String json(Object value) throws Exception {
        return objectMapper.writeValueAsString(value);
    }

    @TestConfiguration
    static class MockEmbeddingRestClientConfiguration {

        @Bean
        EmbeddingHttpMock embeddingHttpMock() {
            RestClient.Builder builder = RestClient.builder();
            MockRestServiceServer server = MockRestServiceServer.bindTo(builder).ignoreExpectOrder(true).build();
            return new EmbeddingHttpMock(builder, server);
        }

        @Bean
        MockRestServiceServer embeddingServer(EmbeddingHttpMock mock) {
            return mock.server();
        }

        @Bean
        @Primary
        RestClient.Builder restClientBuilder(EmbeddingHttpMock mock) {
            return mock.builder();
        }
    }

    record EmbeddingHttpMock(RestClient.Builder builder, MockRestServiceServer server) {
    }
}
