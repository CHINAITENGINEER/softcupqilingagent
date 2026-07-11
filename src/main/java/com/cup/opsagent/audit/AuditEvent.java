package com.cup.opsagent.audit;

import java.time.Instant;
import java.util.Map;

public record AuditEvent(
        String traceId,
        AuditEventType eventType,
        Instant eventTime,
        boolean success,
        Map<String, Object> payload,
        String errorMessage,
        String previousHash,
        String eventHash
) {
    public AuditEvent {
        if (traceId == null || traceId.isBlank()) {
            throw new IllegalArgumentException("traceId must not be blank");
        }
        if (eventType == null) {
            throw new IllegalArgumentException("eventType must not be null");
        }
        eventTime = eventTime == null ? Instant.now() : eventTime;
        payload = payload == null ? Map.of() : Map.copyOf(payload);
        errorMessage = errorMessage == null ? "" : errorMessage;
        previousHash = previousHash == null ? "" : previousHash;
        eventHash = eventHash == null ? "" : eventHash;
    }

    public static AuditEvent success(String traceId, AuditEventType eventType, Map<String, Object> payload) {
        return new AuditEvent(traceId, eventType, Instant.now(), true, payload, null, "", "");
    }

    public static AuditEvent failure(String traceId, AuditEventType eventType, Map<String, Object> payload, String errorMessage) {
        return new AuditEvent(traceId, eventType, Instant.now(), false, payload, errorMessage, "", "");
    }

    public AuditEvent withHashes(String previousHash, String eventHash) {
        return new AuditEvent(traceId, eventType, eventTime, success, payload, errorMessage, previousHash, eventHash);
    }
}
