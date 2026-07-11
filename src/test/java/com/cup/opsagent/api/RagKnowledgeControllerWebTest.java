package com.cup.opsagent.api;

import com.cup.opsagent.audit.AuditEventType;
import com.cup.opsagent.audit.AuditLogService;
import com.cup.opsagent.audit.AuditTrace;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.blankOrNullString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class RagKnowledgeControllerWebTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private AuditLogService auditLogService;

    @Test
    void shouldIngestKnowledgeAndWriteAuditEvents() throws Exception {
        String response = mockMvc.perform(post("/api/rag/knowledge/ingest")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("documents", java.util.List.of(Map.of(
                                "knowledgeId", "runbook-nginx-web",
                                "sourceType", "runbook",
                                "title", "Nginx troubleshooting",
                                "content", "Check nginx status.\n\nCheck upstream health.",
                                "metadata", Map.of("service", "nginx")
                        ))))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.traceId", not(blankOrNullString())))
                .andExpect(jsonPath("$.documentCount").value(1))
                .andExpect(jsonPath("$.chunkCount").value(2))
                .andExpect(jsonPath("$.knowledgeIds[0]").value("runbook-nginx-web"))
                .andExpect(jsonPath("$.chunkIds[0]").value("runbook-nginx-web#chunk-0"))
                .andReturn()
                .getResponse()
                .getContentAsString();

        String traceId = objectMapper.readTree(response).get("traceId").asText();
        AuditTrace trace = auditLogService.findTrace(traceId).orElseThrow();

        assertThat(trace.getStatus()).isEqualTo("RAG_KNOWLEDGE_INGESTED");
        assertThat(trace.getEvents())
                .extracting(event -> event.eventType())
                .contains(
                        AuditEventType.RAG_KNOWLEDGE_INGESTION_STARTED,
                        AuditEventType.RAG_KNOWLEDGE_INGESTION_COMPLETED,
                        AuditEventType.FINAL_RESPONSE
                );
        assertThat(trace.getEvents())
                .filteredOn(event -> event.eventType() == AuditEventType.RAG_KNOWLEDGE_INGESTION_COMPLETED)
                .singleElement()
                .satisfies(event -> {
                    assertThat(event.payload()).containsEntry("documentCount", 1);
                    assertThat(event.payload()).containsEntry("chunkCount", 2);
                    assertThat(event.payload()).doesNotContainKey("content");
                });
    }

    @Test
    void shouldRejectBlankKnowledgeContent() throws Exception {
        mockMvc.perform(post("/api/rag/knowledge/ingest")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("documents", java.util.List.of(Map.of(
                                "knowledgeId", "blank-doc",
                                "sourceType", "runbook",
                                "title", "Blank",
                                "content", " "
                        ))))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    private String json(Object value) throws Exception {
        return objectMapper.writeValueAsString(value);
    }
}
