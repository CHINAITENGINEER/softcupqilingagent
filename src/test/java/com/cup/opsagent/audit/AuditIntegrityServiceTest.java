package com.cup.opsagent.audit;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class AuditIntegrityServiceTest {

    private final InMemoryAuditTraceRepository repository = new InMemoryAuditTraceRepository();
    private final AuditIntegrityService auditIntegrityService = new AuditIntegrityService(new AuditLogService(repository));

    @Test
    void shouldReportValidTraceIntegrity() {
        AuditTrace trace = traceWithOneEvent("trace-1");
        repository.save(trace);

        AuditIntegrityResult result = auditIntegrityService.check("trace-1");

        assertThat(result.found()).isTrue();
        assertThat(result.valid()).isTrue();
        assertThat(result.eventCount()).isEqualTo(1);
        assertThat(result.lastEventHash()).isNotBlank();
        assertThat(result.failureReason()).isBlank();
    }

    @Test
    void shouldReportMissingTrace() {
        AuditIntegrityResult result = auditIntegrityService.check("missing-trace");

        assertThat(result.found()).isFalse();
        assertThat(result.valid()).isFalse();
        assertThat(result.failureReason()).isEqualTo("trace not found");
    }

    @Test
    void shouldReportTamperedTrace() {
        AuditTrace original = traceWithOneEvent("trace-1");
        AuditEvent event = original.getEvents().getFirst();
        AuditEvent tampered = new AuditEvent(
                event.traceId(),
                event.eventType(),
                event.eventTime(),
                event.success(),
                Map.of("userInput", "tampered"),
                event.errorMessage(),
                event.previousHash(),
                event.eventHash()
        );
        repository.save(new AuditTrace(
                original.getTraceId(),
                original.getUserInput(),
                original.getStartedAt(),
                original.getEndedAt(),
                original.getStatus(),
                original.getFinalAnswer(),
                List.of(tampered)
        ));

        AuditIntegrityResult result = auditIntegrityService.check("trace-1");

        assertThat(result.found()).isTrue();
        assertThat(result.valid()).isFalse();
        assertThat(result.failureReason()).contains("eventHash mismatch");
    }

    private AuditTrace traceWithOneEvent(String traceId) {
        AuditTrace trace = new AuditTrace(traceId, "检查系统负载");
        trace.addEvent(AuditEvent.success(traceId, AuditEventType.RECEIVE_REQUEST, Map.of("userInput", "检查系统负载")));
        return trace;
    }
}
