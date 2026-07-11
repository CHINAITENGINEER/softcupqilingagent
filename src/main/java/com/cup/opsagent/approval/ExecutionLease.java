package com.cup.opsagent.approval;

import java.time.Instant;
import java.util.Map;

public record ExecutionLease(
        String leaseId,
        String approvalId,
        String actionHash,
        String toolName,
        Map<String, Object> canonicalArguments,
        Instant issuedAt,
        Instant expiresAt,
        Instant consumedAt
) {
    public ExecutionLease {
        if (leaseId == null || leaseId.isBlank()) {
            throw new IllegalArgumentException("leaseId must not be blank");
        }
        if (approvalId == null || approvalId.isBlank()) {
            throw new IllegalArgumentException("approvalId must not be blank");
        }
        if (actionHash == null || actionHash.isBlank()) {
            throw new IllegalArgumentException("actionHash must not be blank");
        }
        if (toolName == null || toolName.isBlank()) {
            throw new IllegalArgumentException("toolName must not be blank");
        }
        canonicalArguments = canonicalArguments == null ? Map.of() : Map.copyOf(canonicalArguments);
        issuedAt = issuedAt == null ? Instant.now() : issuedAt;
        if (expiresAt == null || !expiresAt.isAfter(issuedAt)) {
            throw new IllegalArgumentException("expiresAt must be after issuedAt");
        }
    }

    public boolean consumed() {
        return consumedAt != null;
    }

    public boolean expiredAt(Instant now) {
        Instant checkedAt = now == null ? Instant.now() : now;
        return !checkedAt.isBefore(expiresAt);
    }

    public ExecutionLease consume(Instant consumedAt) {
        return new ExecutionLease(
                leaseId,
                approvalId,
                actionHash,
                toolName,
                canonicalArguments,
                issuedAt,
                expiresAt,
                consumedAt == null ? Instant.now() : consumedAt
        );
    }
}
