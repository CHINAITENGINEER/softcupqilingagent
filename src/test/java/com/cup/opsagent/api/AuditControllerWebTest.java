package com.cup.opsagent.api;

import com.cup.opsagent.audit.AuditLogService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.blankOrNullString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class AuditControllerWebTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AuditLogService auditLogService;

    @Test
    void shouldReturnTraceIntegrity() throws Exception {
        String traceId = auditLogService.startTrace("检查系统负载").getTraceId();

        mockMvc.perform(get("/api/audit/traces/{traceId}/integrity", traceId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.traceId").value(traceId))
                .andExpect(jsonPath("$.found").value(true))
                .andExpect(jsonPath("$.valid").value(true))
                .andExpect(jsonPath("$.eventCount").value(1))
                .andExpect(jsonPath("$.lastEventHash", not(blankOrNullString())));
    }

    @Test
    void shouldReturnMissingTraceIntegrity() throws Exception {
        mockMvc.perform(get("/api/audit/traces/{traceId}/integrity", "missing-trace"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.traceId").value("missing-trace"))
                .andExpect(jsonPath("$.found").value(false))
                .andExpect(jsonPath("$.valid").value(false))
                .andExpect(jsonPath("$.failureReason").value("trace not found"));
    }
}
