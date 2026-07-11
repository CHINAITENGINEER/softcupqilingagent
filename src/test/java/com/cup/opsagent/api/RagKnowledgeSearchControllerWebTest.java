package com.cup.opsagent.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;

import static org.hamcrest.Matchers.greaterThan;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "ops-agent.rag.retriever-mode=vector")
@AutoConfigureMockMvc
class RagKnowledgeSearchControllerWebTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void shouldSearchIngestedKnowledgeWithVectorRetriever() throws Exception {
        mockMvc.perform(post("/api/rag/knowledge/ingest")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("documents", java.util.List.of(Map.of(
                                "knowledgeId", "runbook-redis-memory-search",
                                "sourceType", "runbook",
                                "title", "Redis memory runbook",
                                "content", "Redis memory pressure troubleshooting. Check eviction policy and used_memory.",
                                "metadata", Map.of("service", "redis")
                        ))))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.chunkCount").value(1));

        mockMvc.perform(get("/api/rag/knowledge/search")
                        .param("q", "redis memory eviction")
                        .param("maxResults", "3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.query").value("redis memory eviction"))
                .andExpect(jsonPath("$.maxResults").value(3))
                .andExpect(jsonPath("$.resultCount", greaterThan(0)))
                .andExpect(jsonPath("$.results[0].knowledgeId").value("runbook-redis-memory-search#chunk-0"))
                .andExpect(jsonPath("$.results[0].title").value("Redis memory runbook"))
                .andExpect(jsonPath("$.results[0].snippet").value(org.hamcrest.Matchers.containsString("Redis memory pressure")))
                .andExpect(jsonPath("$.results[0].metadata.parentKnowledgeId").value("runbook-redis-memory-search"))
                .andExpect(jsonPath("$.results[0].metadata.service").value("redis"));
    }

    @Test
    void shouldRejectBlankSearchQuery() throws Exception {
        mockMvc.perform(get("/api/rag/knowledge/search")
                        .param("q", " "))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"));
    }

    @Test
    void shouldRejectTooLargeMaxResults() throws Exception {
        mockMvc.perform(get("/api/rag/knowledge/search")
                        .param("q", "redis")
                        .param("maxResults", "21"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"));
    }

    private String json(Object value) throws Exception {
        return objectMapper.writeValueAsString(value);
    }
}
