package com.cup.opsagent.auth;

import com.cup.opsagent.approval.ActionHasher;
import com.cup.opsagent.approval.ApprovalRecord;
import com.cup.opsagent.approval.ApprovalStatus;
import com.cup.opsagent.tool.core.RiskLevel;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ApproverPolicyTest {

    private final ApproverPolicy approverPolicy = new ApproverPolicy();

    @Test
    void shouldAllowApproverToApproveOtherRequesterAction() {
        Actor approver = new Actor("approver-1", Set.of(Role.APPROVER));

        assertThatNoException().isThrownBy(() -> approverPolicy.assertCanApprove(approver, approvalRequestedBy("requester-1")));
    }

    @Test
    void shouldAllowDirectPermissionToApproveWithoutApproverRole() {
        Actor customApprover = new Actor(
                "custom-approver-1",
                ActorType.HUMAN,
                Set.of(Permission.APPROVE_MEDIUM_RISK_ACTION),
                Set.of(Role.OPERATOR)
        );

        assertThatNoException().isThrownBy(() -> approverPolicy.assertCanApprove(customApprover, approvalRequestedBy("requester-1")));
    }

    @Test
    void shouldRejectApprovalWhenActorHasNoApprovePermission() {
        Actor operator = new Actor("operator-1", Set.of(Role.OPERATOR));

        assertThatThrownBy(() -> approverPolicy.assertCanApprove(operator, approvalRequestedBy("requester-1")))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("not allowed to approve");
    }

    @Test
    void shouldRejectApprovalWhenRequesterApprovesOwnAction() {
        Actor approver = new Actor("requester-1", Set.of(Role.APPROVER));

        assertThatThrownBy(() -> approverPolicy.assertCanApprove(approver, approvalRequestedBy("requester-1")))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("requester cannot approve");
    }

    @Test
    void shouldRejectApprovalWhenActorIsNotHuman() {
        Actor agent = new Actor(
                "safeops-agent",
                ActorType.AGENT,
                Set.of(Permission.APPROVE_MEDIUM_RISK_ACTION),
                Set.of()
        );

        assertThatThrownBy(() -> approverPolicy.assertCanApprove(agent, approvalRequestedBy("requester-1")))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("only human actors can approve approvals");
    }

    @Test
    void shouldAllowExecutorToExecuteApprovedAction() {
        Actor executor = new Actor("executor-1", Set.of(Role.EXECUTOR));

        assertThatNoException().isThrownBy(() -> approverPolicy.assertCanExecuteApprovedAction(executor, approvalApprovedBy("approver-1")));
    }

    @Test
    void shouldAllowAgentWithExecutePermissionToExecuteApprovedAction() {
        Actor agent = new Actor(
                "safeops-agent",
                ActorType.AGENT,
                Set.of(Permission.EXECUTE_APPROVED_ACTION),
                Set.of()
        );

        assertThatNoException().isThrownBy(() -> approverPolicy.assertCanExecuteApprovedAction(agent, approvalApprovedBy("approver-1")));
    }

    @Test
    void shouldRejectHumanApproverExecutingTheirOwnApprovedAction() {
        Actor approverAndExecutor = new Actor("approver-1", Set.of(Role.APPROVER, Role.EXECUTOR));

        assertThatThrownBy(() -> approverPolicy.assertCanExecuteApprovedAction(
                approverAndExecutor,
                approvalApprovedBy("approver-1")
        ))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("approver cannot execute");
    }

    @Test
    void shouldRejectExecutionWithoutRecordedApprover() {
        Actor executor = new Actor("executor-1", Set.of(Role.EXECUTOR));

        assertThatThrownBy(() -> approverPolicy.assertCanExecuteApprovedAction(executor, approvalRequestedBy("requester-1")))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("recorded approver");
    }

    @Test
    void shouldRejectApprovedActionExecutionWithoutExecutePermission() {
        Actor approver = new Actor("approver-1", Set.of(Role.APPROVER));

        assertThatThrownBy(() -> approverPolicy.assertCanExecuteApprovedAction(approver, approvalRequestedBy("requester-1")))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("not allowed to execute approved action");
    }

    private ApprovalRecord approvalApprovedBy(String approverId) {
        Instant now = Instant.parse("2026-06-29T10:00:00Z");
        return approvalRequestedBy("requester-1").withStatus(ApprovalStatus.APPROVED, approverId, now.plusSeconds(30));
    }

    private ApprovalRecord approvalRequestedBy(String requesterId) {
        Instant now = Instant.parse("2026-06-29T10:00:00Z");
        Map<String, Object> arguments = Map.of("serviceName", "nginx");
        return new ApprovalRecord(
                "approval-1",
                "trace-1",
                "step-1",
                requesterId,
                "restart_service",
                arguments,
                RiskLevel.MEDIUM,
                ActionHasher.hash("restart_service", arguments, RiskLevel.MEDIUM),
                ApprovalStatus.PENDING,
                "requires approval",
                now,
                now.plusSeconds(900),
                null,
                ""
        );
    }
}
