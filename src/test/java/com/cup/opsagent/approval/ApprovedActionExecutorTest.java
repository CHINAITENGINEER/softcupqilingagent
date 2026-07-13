package com.cup.opsagent.approval;

import com.cup.opsagent.audit.AuditEventType;
import com.cup.opsagent.audit.AuditLogService;
import com.cup.opsagent.audit.AuditTrace;
import com.cup.opsagent.tool.builtin.OpenPortsTool;
import com.cup.opsagent.tool.builtin.PortUsageTool;
import com.cup.opsagent.tool.builtin.RestartServiceTool;
import com.cup.opsagent.tool.builtin.ServiceStatusTool;
import com.cup.opsagent.tool.builtin.SystemLoadTool;
import com.cup.opsagent.tool.builtin.TopProcessesTool;
import com.cup.opsagent.tool.core.OpsTool;
import com.cup.opsagent.tool.core.PermissionRequirement;
import com.cup.opsagent.tool.core.RiskLevel;
import com.cup.opsagent.tool.core.ToolCall;
import com.cup.opsagent.tool.core.ToolDefinition;
import com.cup.opsagent.tool.core.ToolExecutionResult;
import com.cup.opsagent.tool.core.ToolRegistry;
import com.cup.opsagent.verifier.ExecutionVerifier;
import com.cup.opsagent.verifier.VerificationContext;
import com.cup.opsagent.verifier.VerificationOrchestrator;
import com.cup.opsagent.verifier.VerificationResult;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ApprovedActionExecutorTest {

    @Test
    void shouldConsumeLeaseExecuteApprovedToolVerifyAndAudit() {
        AuditLogService auditLogService = new AuditLogService();
        AuditTrace trace = auditLogService.startTrace("重启 nginx");
        ApprovalService approvalService = new ApprovalService(new InMemoryApprovalRepository());
        ApprovalRecord approval = approvalService.requestApproval(
                trace.getTraceId(),
                "step-1",
                "requester-1",
                RestartServiceTool.NAME,
                Map.of("serviceName", "nginx"),
                RiskLevel.MEDIUM,
                "requires approval"
        );
        ExecutionLease lease = approvalService.approve(approval.approvalId(), "admin");
        ApprovedActionExecutor executor = executor(approvalService, auditLogService, new ToolExecutionResult(
                RestartServiceTool.NAME,
                true,
                "restart requested",
                "",
                0,
                5,
                Instant.now()
        ));

        ApprovedActionResult result = executor.execute(lease.leaseId(), RestartServiceTool.NAME, Map.of("serviceName", "nginx"));

        assertThat(result.lease().consumed()).isTrue();
        assertThat(result.executionResult().success()).isTrue();
        assertThat(result.verificationResult().verified()).isTrue();
        assertThat(approvalService.findApproval(approval.approvalId()).orElseThrow().status()).isEqualTo(ApprovalStatus.CONSUMED);

        AuditTrace updatedTrace = auditLogService.findTrace(trace.getTraceId()).orElseThrow();
        assertThat(updatedTrace.getStatus()).isEqualTo("SUCCESS");
        assertThat(updatedTrace.getEndedAt()).isNotNull();
        assertThat(updatedTrace.getEvents()).extracting(event -> event.eventType()).contains(
                AuditEventType.EXECUTION_LEASE_CONSUMED,
                AuditEventType.EXECUTION_STARTED,
                AuditEventType.TOOL_EXECUTED,
                AuditEventType.VERIFICATION_STARTED,
                AuditEventType.VERIFICATION_PASSED,
                AuditEventType.APPROVED_ACTION_EXECUTED
        );
    }

    @Test
    void shouldRejectApprovedExecutionWhenArgumentsAreTampered() {
        AuditLogService auditLogService = new AuditLogService();
        AuditTrace trace = auditLogService.startTrace("重启 nginx");
        ApprovalService approvalService = new ApprovalService(new InMemoryApprovalRepository());
        ApprovalRecord approval = approvalService.requestApproval(
                trace.getTraceId(),
                "step-1",
                "requester-1",
                RestartServiceTool.NAME,
                Map.of("serviceName", "nginx"),
                RiskLevel.MEDIUM,
                "requires approval"
        );
        ExecutionLease lease = approvalService.approve(approval.approvalId(), "admin");
        ApprovedActionExecutor executor = executor(approvalService, auditLogService, new ToolExecutionResult(
                RestartServiceTool.NAME,
                true,
                "restart requested",
                "",
                0,
                5,
                Instant.now()
        ));

        assertThatThrownBy(() -> executor.execute(lease.leaseId(), RestartServiceTool.NAME, Map.of("serviceName", "mysql")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("action hash mismatch");
        assertThat(approvalService.findApproval(approval.approvalId()).orElseThrow().status()).isEqualTo(ApprovalStatus.APPROVED);
    }

    private ApprovedActionExecutor executor(ApprovalService approvalService, AuditLogService auditLogService, ToolExecutionResult executionResult) {
        ToolRegistry toolRegistry = new ToolRegistry(List.of(
                readOnlyTool(SystemLoadTool.NAME),
                readOnlyTool(TopProcessesTool.NAME),
                readOnlyTool(OpenPortsTool.NAME),
                readOnlyTool(PortUsageTool.NAME),
                readOnlyTool(ServiceStatusTool.NAME),
                restartTool(executionResult)
        ));
        return new ApprovedActionExecutor(
                approvalService,
                toolRegistry,
                new VerificationOrchestrator(List.of(new PassingVerifier())),
                auditLogService
        );
    }

    private OpsTool readOnlyTool(String name) {
        return new StubOpsTool(new ToolDefinition(
                name,
                name + " test tool",
                Map.of(),
                RiskLevel.LOW,
                true,
                false,
                PermissionRequirement.READ_ONLY_OS,
                1000,
                1024,
                true
        ), new ToolExecutionResult(name, true, "ok", "", 0, 1, Instant.now()));
    }

    private OpsTool restartTool(ToolExecutionResult executionResult) {
        return new StubOpsTool(new ToolDefinition(
                RestartServiceTool.NAME,
                "restart test tool",
                Map.of("serviceName", "string"),
                RiskLevel.MEDIUM,
                false,
                true,
                PermissionRequirement.SERVICE_CONTROL,
                1000,
                1024,
                true
        ), executionResult);
    }

    private record StubOpsTool(ToolDefinition definition, ToolExecutionResult result) implements OpsTool {
        @Override
        public ToolExecutionResult execute(ToolCall call) {
            return result;
        }
    }

    private static class PassingVerifier implements ExecutionVerifier {
        @Override
        public String name() {
            return "PassingVerifier";
        }

        @Override
        public boolean supports(ToolCall toolCall, ToolDefinition toolDefinition) {
            return true;
        }

        @Override
        public VerificationResult verify(VerificationContext context) {
            return VerificationResult.passed(name(), "approved action verified", Map.of("toolName", context.toolCall().toolName()));
        }
    }
}
