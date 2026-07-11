package com.cup.opsagent.approval;

import com.cup.opsagent.tool.core.RiskLevel;

import java.time.Instant;
import java.util.Map;

public record ApprovalRecord(
        String approvalId,
        String traceId,
        String stepId,
        String requesterId,
        String toolName,
        Map<String, Object> canonicalArguments,
        RiskLevel riskLevel,
        String actionHash,
        ApprovalStatus status,
        String reason,
        Instant createdAt,
        Instant expiresAt,
        Instant decidedAt,
        String decidedBy
) {
    public ApprovalRecord {
        if (approvalId == null || approvalId.isBlank()) {
            throw new IllegalArgumentException("approvalId must not be blank");
        }
        if (traceId == null || traceId.isBlank()) {
            throw new IllegalArgumentException("traceId must not be blank");
        }
        if (stepId == null || stepId.isBlank()) {
            throw new IllegalArgumentException("stepId must not be blank");
        }
        requesterId = requesterId == null || requesterId.isBlank() ? "anonymous" : requesterId;
        if (toolName == null || toolName.isBlank()) {
            throw new IllegalArgumentException("toolName must not be blank");
        }
        canonicalArguments = canonicalArguments == null ? Map.of() : Map.copyOf(canonicalArguments);
        if (riskLevel == null) {
            throw new IllegalArgumentException("riskLevel must not be null");
        }
        if (actionHash == null || actionHash.isBlank()) {
            throw new IllegalArgumentException("actionHash must not be blank");
        }
        if (status == null) {
            throw new IllegalArgumentException("status must not be null");
        }
        reason = reason == null ? "" : reason;
        createdAt = createdAt == null ? Instant.now() : createdAt;
        if (expiresAt == null || !expiresAt.isAfter(createdAt)) {
            throw new IllegalArgumentException("expiresAt must be after createdAt");
        }
        decidedBy = decidedBy == null ? "" : decidedBy;
    }

    public ApprovalRecord withStatus(ApprovalStatus nextStatus, String actor, Instant decidedAt) {
        return new ApprovalRecord(
                approvalId,
                traceId,
                stepId,
                requesterId,
                toolName,
                canonicalArguments,
                riskLevel,
                actionHash,
                nextStatus,
                reason,
                createdAt,
                expiresAt,
                decidedAt == null ? Instant.now() : decidedAt,
                actor
        );
    }
}
