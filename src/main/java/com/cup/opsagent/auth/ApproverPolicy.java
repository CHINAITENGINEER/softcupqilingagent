package com.cup.opsagent.auth;

import com.cup.opsagent.approval.ApprovalRecord;
import com.cup.opsagent.tool.core.RiskLevel;
import org.springframework.stereotype.Service;

@Service
public class ApproverPolicy {

    public void assertCanApprove(Actor actor, ApprovalRecord approvalRecord) {
        assertKnownActor(actor);
        assertHumanActor(actor, "approve");
        if (!actor.hasPermission(permissionForApprove(approvalRecord.riskLevel()))) {
            throw new SecurityException("actor is not allowed to approve risk level: " + approvalRecord.riskLevel());
        }
        if (approvalRecord.requesterId().equals(actor.actorId())) {
            throw new SecurityException("requester cannot approve their own action");
        }
    }

    public void assertCanReject(Actor actor, ApprovalRecord approvalRecord) {
        assertKnownActor(actor);
        assertHumanActor(actor, "reject");
        if (!actor.hasPermission(permissionForReject(approvalRecord.riskLevel()))) {
            throw new SecurityException("actor is not allowed to reject risk level: " + approvalRecord.riskLevel());
        }
    }

    public void assertCanExecuteApprovedAction(Actor actor, ApprovalRecord approvalRecord) {
        assertKnownActor(actor);
        if (!actor.hasPermission(Permission.EXECUTE_APPROVED_ACTION)) {
            throw new SecurityException("actor is not allowed to execute approved action");
        }
        if (approvalRecord == null || approvalRecord.decidedBy() == null || approvalRecord.decidedBy().isBlank()) {
            throw new SecurityException("approved action requires a recorded approver");
        }
        if (actor.actorType() == ActorType.HUMAN && approvalRecord.decidedBy().equals(actor.actorId())) {
            throw new SecurityException("approver cannot execute their own approved action");
        }
    }

    private Permission permissionForApprove(RiskLevel riskLevel) {
        if (riskLevel == RiskLevel.MEDIUM) {
            return Permission.APPROVE_MEDIUM_RISK_ACTION;
        }
        return Permission.APPROVE_MEDIUM_RISK_ACTION;
    }

    private Permission permissionForReject(RiskLevel riskLevel) {
        if (riskLevel == RiskLevel.MEDIUM) {
            return Permission.REJECT_MEDIUM_RISK_ACTION;
        }
        return Permission.REJECT_MEDIUM_RISK_ACTION;
    }

    private void assertKnownActor(Actor actor) {
        if (actor == null) {
            throw new SecurityException("actor is required");
        }
    }

    private void assertHumanActor(Actor actor, String decision) {
        if (actor.actorType() != ActorType.HUMAN) {
            throw new SecurityException("only human actors can " + decision + " approvals");
        }
    }
}
