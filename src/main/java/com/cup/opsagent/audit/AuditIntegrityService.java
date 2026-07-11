package com.cup.opsagent.audit;

import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class AuditIntegrityService {

    private final AuditLogService auditLogService;

    public AuditIntegrityService(AuditLogService auditLogService) {
        this.auditLogService = auditLogService;
    }

    public AuditIntegrityResult check(String traceId) {
        if (traceId == null || traceId.isBlank()) {
            throw new IllegalArgumentException("traceId must not be blank");
        }
        return auditLogService.findTrace(traceId)
                .map(this::checkTrace)
                .orElseGet(() -> AuditIntegrityResult.missing(traceId));
    }

    private AuditIntegrityResult checkTrace(AuditTrace trace) {
        List<AuditEvent> events = trace.getEvents();
        String previousHash = AuditHasher.GENESIS_HASH;
        for (int index = 0; index < events.size(); index++) {
            AuditEvent event = events.get(index);
            String lastEventHash = lastEventHash(events);
            if (!previousHash.equals(event.previousHash())) {
                return AuditIntegrityResult.invalid(
                        trace.getTraceId(),
                        events.size(),
                        lastEventHash,
                        "event[" + index + "] previousHash mismatch"
                );
            }
            String expectedHash = AuditHasher.hashEvent(event, previousHash);
            if (!expectedHash.equals(event.eventHash())) {
                return AuditIntegrityResult.invalid(
                        trace.getTraceId(),
                        events.size(),
                        lastEventHash,
                        "event[" + index + "] eventHash mismatch"
                );
            }
            previousHash = event.eventHash();
        }
        return AuditIntegrityResult.valid(trace.getTraceId(), events.size(), lastEventHash(events));
    }

    private String lastEventHash(List<AuditEvent> events) {
        return events == null || events.isEmpty() ? "" : events.getLast().eventHash();
    }
}
