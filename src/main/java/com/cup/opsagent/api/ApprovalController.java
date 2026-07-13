package com.cup.opsagent.api;

import com.cup.opsagent.approval.ApprovalRecord;
import com.cup.opsagent.approval.ApprovalService;
import com.cup.opsagent.approval.ApprovedActionExecutor;
import com.cup.opsagent.approval.ApprovedActionResult;
import com.cup.opsagent.approval.ExecutionLease;
import com.cup.opsagent.audit.AuditEvent;
import com.cup.opsagent.audit.AuditEventType;
import com.cup.opsagent.audit.AuditLogService;
import com.cup.opsagent.auth.Actor;
import com.cup.opsagent.auth.ApproverPolicy;
import com.cup.opsagent.auth.CurrentActor;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/approvals")
public class ApprovalController {

    private final ApprovalService approvalService;
    private final ApprovedActionExecutor approvedActionExecutor;
    private final AuditLogService auditLogService;
    private final ApproverPolicy approverPolicy;

    public ApprovalController(
            ApprovalService approvalService,
            ApprovedActionExecutor approvedActionExecutor,
            AuditLogService auditLogService,
            ApproverPolicy approverPolicy
    ) {
        this.approvalService = approvalService;
        this.approvedActionExecutor = approvedActionExecutor;
        this.auditLogService = auditLogService;
        this.approverPolicy = approverPolicy;
    }

    @GetMapping
    public List<ApprovalSummaryResponse> listApprovals() {
        return approvalService.listApprovals().stream()
                .map(ApprovalSummaryResponse::from)
                .toList();
    }

    @GetMapping("/{approvalId}")
    public ApprovalDetailResponse getApproval(@PathVariable String approvalId) {
        return approvalService.findApproval(approvalId)
                .map(ApprovalDetailResponse::from)
                .orElseThrow(() -> new java.util.NoSuchElementException("approval not found: " + approvalId));
    }

    @PostMapping("/approve")
    public ApprovalDecisionResponse approve(
            @Valid @RequestBody ApprovalDecisionRequest request,
            @CurrentActor Actor actor
    ) {
        ApprovalRecord pendingApproval = approvalService.findApproval(request.approvalId()).orElseThrow();
        approverPolicy.assertCanApprove(actor, pendingApproval);
        ExecutionLease lease = approvalService.approve(request.approvalId(), actor.actorId());
        ApprovalRecord approval = approvalService.findApproval(request.approvalId()).orElseThrow();
        auditLogService.append(approval.traceId(), AuditEvent.success(approval.traceId(), AuditEventType.APPROVAL_GRANTED, payload(
                "approvalId", approval.approvalId(),
                "leaseId", lease.leaseId(),
                "toolName", approval.toolName(),
                "actionHash", approval.actionHash(),
                "approvedBy", actor.actorId(),
                "approvedByType", actor.actorType().name(),
                "approvedByRoles", actor.roles().stream().map(Enum::name).sorted().toList(),
                "approvedByPermissions", actor.permissions().stream().map(Enum::name).sorted().toList(),
                "leaseExpiresAt", lease.expiresAt().toString()
        )));
        return ApprovalDecisionResponse.from(approval, lease);
    }

    @PostMapping("/reject")
    public ApprovalDecisionResponse reject(
            @Valid @RequestBody ApprovalDecisionRequest request,
            @CurrentActor Actor actor
    ) {
        ApprovalRecord pendingApproval = approvalService.findApproval(request.approvalId()).orElseThrow();
        approverPolicy.assertCanReject(actor, pendingApproval);
        ApprovalRecord approval = approvalService.reject(request.approvalId(), actor.actorId());
        auditLogService.append(approval.traceId(), AuditEvent.success(approval.traceId(), AuditEventType.APPROVAL_REJECTED, payload(
                "approvalId", approval.approvalId(),
                "toolName", approval.toolName(),
                "actionHash", approval.actionHash(),
                "rejectedBy", actor.actorId(),
                "rejectedByType", actor.actorType().name(),
                "rejectedByRoles", actor.roles().stream().map(Enum::name).sorted().toList(),
                "rejectedByPermissions", actor.permissions().stream().map(Enum::name).sorted().toList()
        )));
        return ApprovalDecisionResponse.rejected(approval);
    }

    @PostMapping("/execute")
    public ApprovedExecutionResponse execute(
            @Valid @RequestBody ApprovedExecutionRequest request,
            @CurrentActor Actor actor
    ) {
        ExecutionLease lease = approvalService.findLease(request.leaseId()).orElseThrow();
        ApprovalRecord approval = approvalService.findApproval(lease.approvalId()).orElseThrow();
        approverPolicy.assertCanExecuteApprovedAction(actor, approval);
        ApprovedActionResult result = approvedActionExecutor.execute(request.leaseId(), request.toolName(), request.arguments());
        return ApprovedExecutionResponse.from(result);
    }

    private Map<String, Object> payload(Object... entries) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (int index = 0; index + 1 < entries.length; index += 2) {
            Object key = entries[index];
            if (key != null) {
                result.put(String.valueOf(key), entries[index + 1] == null ? "" : entries[index + 1]);
            }
        }
        return Map.copyOf(result);
    }

    public record ApprovalSummaryResponse(
            String approvalId,
            String traceId,
            String stepId,
            String requesterId,
            String toolName,
            String serviceName,
            String riskLevel,
            String status,
            String reason,
            String actionHash,
            Instant createdAt,
            Instant expiresAt,
            Instant decidedAt,
            String decidedBy
    ) {
        static ApprovalSummaryResponse from(ApprovalRecord approval) {
            return new ApprovalSummaryResponse(
                    approval.approvalId(),
                    approval.traceId(),
                    approval.stepId(),
                    approval.requesterId(),
                    approval.toolName(),
                    extractServiceName(approval),
                    approval.riskLevel().name(),
                    approval.status().name(),
                    approval.reason(),
                    approval.actionHash(),
                    approval.createdAt(),
                    approval.expiresAt(),
                    approval.decidedAt(),
                    approval.decidedBy()
            );
        }
    }

    public record ApprovalDetailResponse(
            String approvalId,
            String traceId,
            String stepId,
            String requesterId,
            String toolName,
            String serviceName,
            Map<String, Object> arguments,
            String riskLevel,
            String status,
            String reason,
            String actionHash,
            Instant createdAt,
            Instant expiresAt,
            Instant decidedAt,
            String decidedBy
    ) {
        static ApprovalDetailResponse from(ApprovalRecord approval) {
            return new ApprovalDetailResponse(
                    approval.approvalId(),
                    approval.traceId(),
                    approval.stepId(),
                    approval.requesterId(),
                    approval.toolName(),
                    extractServiceName(approval),
                    approval.canonicalArguments(),
                    approval.riskLevel().name(),
                    approval.status().name(),
                    approval.reason(),
                    approval.actionHash(),
                    approval.createdAt(),
                    approval.expiresAt(),
                    approval.decidedAt(),
                    approval.decidedBy()
            );
        }
    }

    private static String extractServiceName(ApprovalRecord approval) {
        Object value = approval.canonicalArguments().get("serviceName");
        return value == null ? "" : String.valueOf(value);
    }

    public record ApprovalDecisionRequest(
            @NotBlank String approvalId
    ) {
    }

    public record ApprovalDecisionResponse(
            String approvalId,
            String status,
            String leaseId,
            String actionHash,
            String leaseExpiresAt
    ) {
        static ApprovalDecisionResponse from(ApprovalRecord approval, ExecutionLease lease) {
            return new ApprovalDecisionResponse(
                    approval.approvalId(),
                    approval.status().name(),
                    lease.leaseId(),
                    lease.actionHash(),
                    lease.expiresAt().toString()
            );
        }

        static ApprovalDecisionResponse rejected(ApprovalRecord approval) {
            return new ApprovalDecisionResponse(
                    approval.approvalId(),
                    approval.status().name(),
                    "none",
                    approval.actionHash(),
                    "none"
            );
        }
    }

    public record ApprovedExecutionRequest(
            @NotBlank String leaseId,
            @NotBlank String toolName,
            @NotNull Map<String, Object> arguments
    ) {
    }

    public record ApprovedExecutionResponse(
            String leaseId,
            boolean executionSuccess,
            boolean verified,
            String failureCode,
            String verificationReason
    ) {
        static ApprovedExecutionResponse from(ApprovedActionResult result) {
            return new ApprovedExecutionResponse(
                    result.lease().leaseId(),
                    result.executionResult().success(),
                    result.verificationResult().verified(),
                    result.executionResult().failureCode() == null ? "none" : result.executionResult().failureCode().name(),
                    result.verificationResult().reason()
            );
        }
    }
}
