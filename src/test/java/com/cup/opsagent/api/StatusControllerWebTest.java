package com.cup.opsagent.api;

import com.cup.opsagent.approval.ApprovalService;
import com.cup.opsagent.tool.core.RiskLevel;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class StatusControllerWebTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ApprovalService approvalService;

    @Test
    void shouldExposeSystemStatusForConsole() throws Exception {
        approvalService.requestApproval(
                "trace-system-status",
                "step-1",
                "operator-1",
                "restart_service",
                Map.of("serviceName", "nginx"),
                RiskLevel.HIGH,
                "console status fixture"
        );

        mockMvc.perform(get("/api/system/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.serviceName").value("QilingOS SafeOps Agent"))
                .andExpect(jsonPath("$.agent.status").value("ONLINE"))
                .andExpect(jsonPath("$.agent.registeredToolCount").isNumber())
                .andExpect(jsonPath("$.approvals.pending").isNumber())
                .andExpect(jsonPath("$.approvals.highRiskPending").isNumber())
                .andExpect(jsonPath("$.audit.integrityMode").value("ENABLED"))
                .andExpect(jsonPath("$.rag.vectorStoreProvider").value("in-memory"))
                .andExpect(jsonPath("$.runtime.javaVersion").isString());
    }

    @Test
    void shouldExposeRagStatusAndStatsForConsole() throws Exception {
        mockMvc.perform(get("/api/rag/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(false))
                .andExpect(jsonPath("$.retrieverMode").value("keyword"))
                .andExpect(jsonPath("$.embeddingProvider").value("deterministic"))
                .andExpect(jsonPath("$.vectorStoreProvider").value("in-memory"))
                .andExpect(jsonPath("$.milvusIndexType").value("autoindex"))
                .andExpect(jsonPath("$.maxResults").value(5));

        mockMvc.perform(get("/api/rag/stats"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.vectorStoreProvider").value("in-memory"))
                .andExpect(jsonPath("$.vectorRecordCount").isNumber())
                .andExpect(jsonPath("$.vectorRecordCountStatus").value("known"))
                .andExpect(jsonPath("$.indexParameters.hnswM").value(16))
                .andExpect(jsonPath("$.indexParameters.ivfFlatNprobe").value(16));
    }
}
