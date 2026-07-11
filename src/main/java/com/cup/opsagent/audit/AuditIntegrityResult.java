package com.cup.opsagent.audit;

public record AuditIntegrityResult(
        String traceId,
        boolean found,
        boolean valid,
        int eventCount,
        String lastEventHash,
        String failureReason
) {
    public AuditIntegrityResult {
        traceId = traceId == null ? "" : traceId;
        lastEventHash = lastEventHash == null ? "" : lastEventHash;
        failureReason = failureReason == null ? "" : failureReason;
    }

    static AuditIntegrityResult missing(String traceId) {
        return new AuditIntegrityResult(traceId, false, false, 0, "", "trace not found");
    }

    static AuditIntegrityResult valid(String traceId, int eventCount, String lastEventHash) {
        return new AuditIntegrityResult(traceId, true, true, eventCount, lastEventHash, "");
    }

    static AuditIntegrityResult invalid(String traceId, int eventCount, String lastEventHash, String failureReason) {
        return new AuditIntegrityResult(traceId, true, false, eventCount, lastEventHash, failureReason);
    }
}
