package com.cup.opsagent.approval;

import com.cup.opsagent.audit.AuditEvent;
import com.cup.opsagent.audit.AuditEventType;
import com.cup.opsagent.audit.AuditLogService;
import com.cup.opsagent.tool.core.OpsTool;
import com.cup.opsagent.tool.core.ToolCall;
import com.cup.opsagent.tool.core.ToolDefinition;
import com.cup.opsagent.tool.core.ToolExecutionResult;
import com.cup.opsagent.tool.core.ToolRegistry;
import com.cup.opsagent.verifier.VerificationContext;
import com.cup.opsagent.verifier.VerificationOrchestrator;
import com.cup.opsagent.verifier.VerificationResult;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class ApprovedActionExecutor {

    private final ApprovalService approvalService;
    private final ToolRegistry toolRegistry;
    private final VerificationOrchestrator verificationOrchestrator;
    private final AuditLogService auditLogService;

    public ApprovedActionExecutor(
            ApprovalService approvalService,
            ToolRegistry toolRegistry,
            VerificationOrchestrator verificationOrchestrator,
            AuditLogService auditLogService
    ) {
        this.approvalService = approvalService;
        this.toolRegistry = toolRegistry;
        this.verificationOrchestrator = verificationOrchestrator;
        this.auditLogService = auditLogService;
    }

    public ApprovedActionResult execute(String leaseId, String toolName, Map<String, Object> arguments) {
        OpsTool tool = toolRegistry.findTool(toolName)
                .orElseThrow(() -> new IllegalArgumentException("tool not found: " + toolName));
        ToolDefinition definition = tool.definition();
        ExecutionLease consumedLease = approvalService.consumeLease(leaseId, toolName, arguments, definition.riskLevel());
        ApprovalRecord approval = approvalService.findApproval(consumedLease.approvalId())
                .orElseThrow(() -> new IllegalStateException("approval not found after lease consumption"));
        String traceId = approval.traceId();

        auditLogService.append(traceId, AuditEvent.success(traceId, AuditEventType.EXECUTION_LEASE_CONSUMED, payload(
                "approvalId", approval.approvalId(),
                "leaseId", consumedLease.leaseId(),
                "toolName", toolName,
                "actionHash", consumedLease.actionHash()
        )));

        ToolCall toolCall = new ToolCall(toolName, consumedLease.canonicalArguments(), traceId, approval.stepId());
        auditLogService.append(traceId, AuditEvent.success(traceId, AuditEventType.EXECUTION_STARTED, payload(
                "approvalId", approval.approvalId(),
                "leaseId", consumedLease.leaseId(),
                "stepId", approval.stepId(),
                "toolName", toolName,
                "approved", true
        )));

        ToolExecutionResult executionResult;
        try {
            executionResult = tool.execute(toolCall);
        } catch (RuntimeException exception) {
            executionResult = new ToolExecutionResult(
                    toolName,
                    false,
                    "",
                    "tool execution failed: " + exception.getMessage(),
                    null,
                    0,
                    Instant.now()
            );
        }
        auditLogService.append(traceId, AuditEvent.success(traceId, AuditEventType.TOOL_EXECUTED, payload(
                "approvalId", approval.approvalId(),
                "leaseId", consumedLease.leaseId(),
                "stepId", approval.stepId(),
                "toolName", toolName,
                "success", executionResult.success(),
                "exitCode", executionResult.exitCode() == null ? "none" : executionResult.exitCode(),
                "failureCode", executionResult.failureCode() == null ? "none" : executionResult.failureCode().name(),
                "durationMs", executionResult.durationMs()
        )));

        VerificationResult verificationResult = verify(approval, toolCall, definition, executionResult);
        auditLogService.append(traceId, AuditEvent.success(
                traceId,
                verificationResult.verified() ? AuditEventType.VERIFICATION_PASSED : AuditEventType.VERIFICATION_FAILED,
                payload(
                        "approvalId", approval.approvalId(),
                        "leaseId", consumedLease.leaseId(),
                        "stepId", approval.stepId(),
                        "toolName", toolName,
                        "verifierName", verificationResult.verifierName(),
                        "verified", verificationResult.verified(),
                        "reason", verificationResult.reason(),
                        "evidence", verificationResult.evidence(),
                        "suggestedRecovery", verificationResult.suggestedRecovery()
                )
        ));
        auditLogService.append(traceId, AuditEvent.success(traceId, AuditEventType.APPROVED_ACTION_EXECUTED, payload(
                "approvalId", approval.approvalId(),
                "leaseId", consumedLease.leaseId(),
                "toolName", toolName,
                "executionSuccess", executionResult.success(),
                "verified", verificationResult.verified()
        )));
        String finalStatus = verificationResult.verified() ? "SUCCESS" : "VERIFICATION_FAILED";
        String finalAnswer = verificationResult.verified()
                ? "approved action executed and verified"
                : "approved action execution did not pass verification";
        auditLogService.conclude(traceId, finalStatus, finalAnswer);
        return new ApprovedActionResult(consumedLease, executionResult, verificationResult);
    }

    private VerificationResult verify(ApprovalRecord approval, ToolCall toolCall, ToolDefinition definition, ToolExecutionResult executionResult) {
        auditLogService.append(approval.traceId(), AuditEvent.success(approval.traceId(), AuditEventType.VERIFICATION_STARTED, payload(
                "approvalId", approval.approvalId(),
                "stepId", approval.stepId(),
                "toolName", toolCall.toolName()
        )));
        try {
            return verificationOrchestrator.verify(new VerificationContext(
                    "approved action",
                    approval.traceId(),
                    toolCall,
                    definition,
                    executionResult
            ));
        } catch (RuntimeException exception) {
            return VerificationResult.failed(
                    "VerificationException",
                    "verification failed with exception: " + exception.getMessage(),
                    payload("toolName", toolCall.toolName()),
                    List.of("检查 verifier 实现是否处理了该工具的执行结果")
            );
        }
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
}
