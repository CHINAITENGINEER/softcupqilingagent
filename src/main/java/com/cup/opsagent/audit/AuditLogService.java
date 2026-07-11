package com.cup.opsagent.audit;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.Comparator;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
public class AuditLogService {

    private final AuditTraceRepository auditTraceRepository;

    @Autowired
    public AuditLogService(AuditTraceRepository auditTraceRepository) {
        this.auditTraceRepository = auditTraceRepository;
    }

    public AuditLogService() {
        this(new InMemoryAuditTraceRepository());
    }

    public AuditTrace startTrace(String userInput) {
        String traceId = UUID.randomUUID().toString();
        AuditTrace trace = new AuditTrace(traceId, userInput);
        auditTraceRepository.save(trace);
        append(traceId, AuditEvent.success(traceId, AuditEventType.RECEIVE_REQUEST, Map.of("userInput", userInput)));
        return trace;
    }

    public void append(String traceId, AuditEvent event) {
        auditTraceRepository.findByTraceId(traceId).ifPresent(trace -> {
            trace.addEvent(event);
            auditTraceRepository.save(trace);
        });
    }

    public void finish(String traceId, String status, String finalAnswer) {
        auditTraceRepository.findByTraceId(traceId).ifPresent(trace -> {
            trace.finish(status, finalAnswer);
            auditTraceRepository.save(trace);
            append(traceId, AuditEvent.success(traceId, AuditEventType.FINAL_RESPONSE, Map.of(
                    "status", status,
                    "finalAnswer", finalAnswer
            )));
        });
    }

    public Optional<AuditTrace> findTrace(String traceId) {
        return auditTraceRepository.findByTraceId(traceId);
    }

    public Collection<AuditTrace> listTraces() {
        return auditTraceRepository.findAll().stream()
                .sorted(Comparator.comparing(AuditTrace::getStartedAt).reversed())
                .toList();
    }
}
