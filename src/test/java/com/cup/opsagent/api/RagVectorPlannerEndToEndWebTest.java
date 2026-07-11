package com.cup.opsagent.api;

import com.cup.opsagent.audit.AuditEventType;
import com.cup.opsagent.audit.AuditLogService;
import com.cup.opsagent.audit.AuditTrace;
import com.cup.opsagent.planner.llm.provider.OpenAiCompatibleLlmPlannerClient;
import com.cup.opsagent.tool.builtin.ServiceStatusTool;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.blankOrNullString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "ops-agent.planner.mode=llm",
        "ops-agent.rag.enabled=true",
        "ops-agent.rag.retriever-mode=vector"
})
@AutoConfigureMockMvc
class RagVectorPlannerEndToEndWebTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private AuditLogService auditLogService;

    @MockBean
    private OpenAiCompatibleLlmPlannerClient llmPlannerClient;

    @Test
    void shouldUseIngestedVectorKnowledgeDuringLlmPlanning() throws Exception {
        when(llmPlannerClient.createPlanJson(eq("nginx 502 upstream status"), argThat(context ->
                context != null
                        && context.contains("Nginx 502 runbook")
                        && context.contains("Check upstream health before restart")
        ))).thenReturn("""
                {
                  "intentType":"SERVICE_STATUS_INSPECTION",
                  "summary":"Check nginx status using retrieved runbook context",
                  "suggestedRiskLevel":"LOW",
                  "steps":[
                    {
                      "stepId":"step-1",
                      "actionName":"inspect-service-status",
                      "toolName":"get_service_status",
                      "arguments":{"serviceName":"nginx"},
                      "reason":"The retrieved runbook suggests checking nginx status first"
                    }
                  ]
                }
                """);

        mockMvc.perform(post("/api/rag/knowledge/ingest")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("documents", java.util.List.of(Map.of(
                                "knowledgeId", "runbook-nginx-502-e2e",
                                "sourceType", "runbook",
                                "title", "Nginx 502 runbook",
                                "content", "Nginx 502 runbook. Check upstream health before restart.",
                                "metadata", Map.of("service", "nginx")
                        ))))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.chunkCount").value(1));

        String response = mockMvc.perform(post("/api/agent/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "message", "nginx 502 upstream status",
                                "sessionId", "session-rag-vector-e2e",
                                "userId", "operator-rag-e2e"
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.traceId", not(blankOrNullString())))
                .andExpect(jsonPath("$.steps[0].toolName").value(ServiceStatusTool.NAME))
                .andReturn()
                .getResponse()
                .getContentAsString();

        String traceId = objectMapper.readTree(response).get("traceId").asText();
        AuditTrace trace = auditLogService.findTrace(traceId).orElseThrow();

        assertThat(trace.getEvents())
                .extracting(event -> event.eventType())
                .contains(
                        AuditEventType.RAG_RETRIEVAL_STARTED,
                        AuditEventType.RAG_RETRIEVAL_COMPLETED,
                        AuditEventType.LLM_PLANNING_COMPLETED,
                        AuditEventType.PLAN_GENERATED
                );
        assertThat(trace.getEvents())
                .filteredOn(event -> event.eventType() == AuditEventType.RAG_RETRIEVAL_COMPLETED)
                .singleElement()
                .satisfies(event -> {
                    assertThat(event.payload()).containsEntry("resultCount", 1);
                    assertThat(event.payload().get("topKnowledgeIds").toString()).contains("runbook-nginx-502-e2e#chunk-0");
                });
        verify(llmPlannerClient).createPlanJson(eq("nginx 502 upstream status"), anyString());
    }

    private String json(Object value) throws Exception {
        return objectMapper.writeValueAsString(value);
    }
}
