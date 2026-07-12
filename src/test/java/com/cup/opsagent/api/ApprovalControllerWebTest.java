package com.cup.opsagent.api;

import com.cup.opsagent.approval.ApprovalRecord;
import com.cup.opsagent.approval.ApprovalService;
import com.cup.opsagent.auth.ActorResolver;
import com.cup.opsagent.tool.core.RiskLevel;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;

import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.blankOrNullString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class ApprovalControllerWebTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ApprovalService approvalService;

    @Test
    void shouldRejectApproveWhenActorHasNoPermission() throws Exception {
        ApprovalRecord approval = requestApproval("requester-1");

        mockMvc.perform(post("/api/approvals/approve")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(ActorResolver.ACTOR_ID_HEADER, "operator-1")
                        .header(ActorResolver.ACTOR_ROLES_HEADER, "OPERATOR")
                        .content(json(Map.of("approvalId", approval.approvalId()))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"))
                .andExpect(jsonPath("$.path").value("/api/approvals/approve"));
    }

    @Test
    void shouldRejectApproveWhenActorHeaderIsMissing() throws Exception {
        ApprovalRecord approval = requestApproval("requester-2");

        mockMvc.perform(post("/api/approvals/approve")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(ActorResolver.ACTOR_ROLES_HEADER, "APPROVER")
                        .content(json(Map.of("approvalId", approval.approvalId()))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"));
    }

    @Test
    void shouldApproveAndReturnLeaseWhenActorHasPermission() throws Exception {
        ApprovalRecord approval = requestApproval("requester-3");

        mockMvc.perform(post("/api/approvals/approve")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(ActorResolver.ACTOR_ID_HEADER, "approver-1")
                        .header(ActorResolver.ACTOR_ROLES_HEADER, "APPROVER")
                        .content(json(Map.of("approvalId", approval.approvalId()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.approvalId").value(approval.approvalId()))
                .andExpect(jsonPath("$.status").value("APPROVED"))
                .andExpect(jsonPath("$.leaseId", not(blankOrNullString())))
                .andExpect(jsonPath("$.actionHash").value(approval.actionHash()));
    }

    @Test
    void shouldReturnConflictWhenApprovalStateNoLongerAllowsApprove() throws Exception {
        ApprovalRecord approval = requestApproval("requester-conflict");
        String request = json(Map.of("approvalId", approval.approvalId()));

        mockMvc.perform(post("/api/approvals/approve")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(ActorResolver.ACTOR_ID_HEADER, "approver-conflict")
                        .header(ActorResolver.ACTOR_ROLES_HEADER, "APPROVER")
                        .content(request))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/approvals/approve")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(ActorResolver.ACTOR_ID_HEADER, "approver-conflict")
                        .header(ActorResolver.ACTOR_ROLES_HEADER, "APPROVER")
                        .content(request))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("STATE_CONFLICT"))
                .andExpect(jsonPath("$.path").value("/api/approvals/approve"));
    }

    @Test
    void shouldListAndGetApprovalDetails() throws Exception {
        ApprovalRecord approval = requestApproval("requester-list");

        mockMvc.perform(get("/api/approvals"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].approvalId").value(approval.approvalId()))
                .andExpect(jsonPath("$[0].traceId").value(approval.traceId()))
                .andExpect(jsonPath("$[0].toolName").value("restart_service"))
                .andExpect(jsonPath("$[0].serviceName").value("nginx"))
                .andExpect(jsonPath("$[0].riskLevel").value("MEDIUM"))
                .andExpect(jsonPath("$[0].status").value("PENDING"));

        mockMvc.perform(get("/api/approvals/{approvalId}", approval.approvalId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.approvalId").value(approval.approvalId()))
                .andExpect(jsonPath("$.arguments.serviceName").value("nginx"))
                .andExpect(jsonPath("$.actionHash").value(approval.actionHash()));
    }

    @Test
    void shouldReturnNotFoundForMissingApprovalDetail() throws Exception {
        mockMvc.perform(get("/api/approvals/{approvalId}", "missing-approval"))
                .andExpect(status().isNotFound());
    }

    private ApprovalRecord requestApproval(String requesterId) {
        return approvalService.requestApproval(
                "trace-web-test-" + requesterId,
                "step-1",
                requesterId,
                "restart_service",
                Map.of("serviceName", "nginx"),
                RiskLevel.MEDIUM,
                "requires approval"
        );
    }

    private String json(Object value) throws Exception {
        return objectMapper.writeValueAsString(value);
    }
}
