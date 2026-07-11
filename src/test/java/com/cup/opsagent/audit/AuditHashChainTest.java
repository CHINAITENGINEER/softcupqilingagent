package com.cup.opsagent.audit;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class AuditHashChainTest {

    @Test
    void shouldAttachHashChainWhenEventsAreAdded() {
        AuditTrace trace = new AuditTrace("trace-1", "检查系统负载");
        trace.addEvent(AuditEvent.success("trace-1", AuditEventType.RECEIVE_REQUEST, Map.of("userInput", "检查系统负载")));
        trace.addEvent(AuditEvent.success("trace-1", AuditEventType.PLAN_GENERATED, Map.of("summary", "check load")));

        List<AuditEvent> events = trace.getEvents();
        assertThat(events).hasSize(2);
        assertThat(events.get(0).previousHash()).isEqualTo(AuditHasher.GENESIS_HASH);
        assertThat(events.get(0).eventHash()).isNotBlank();
        assertThat(events.get(1).previousHash()).isEqualTo(events.get(0).eventHash());
        assertThat(trace.hasValidHashChain()).isTrue();
    }

    @Test
    void shouldDetectTamperedPayload() {
        AuditTrace trace = new AuditTrace("trace-1", "检查系统负载");
        trace.addEvent(AuditEvent.success("trace-1", AuditEventType.RECEIVE_REQUEST, Map.of("userInput", "检查系统负载")));
        AuditEvent original = trace.getEvents().getFirst();
        AuditEvent tampered = new AuditEvent(
                original.traceId(),
                original.eventType(),
                original.eventTime(),
                original.success(),
                Map.of("userInput", "篡改后的输入"),
                original.errorMessage(),
                original.previousHash(),
                original.eventHash()
        );
        AuditTrace tamperedTrace = new AuditTrace(
                trace.getTraceId(),
                trace.getUserInput(),
                trace.getStartedAt(),
                trace.getEndedAt(),
                trace.getStatus(),
                trace.getFinalAnswer(),
                List.of(tampered)
        );

        assertThat(tamperedTrace.hasValidHashChain()).isFalse();
    }
}
