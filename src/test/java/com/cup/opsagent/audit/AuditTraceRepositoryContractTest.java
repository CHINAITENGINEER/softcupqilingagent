package com.cup.opsagent.audit;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

abstract class AuditTraceRepositoryContractTest {

    protected abstract AuditTraceRepository repository();

    @Test
    void shouldSaveAndFindTrace() {
        AuditTraceRepository repository = repository();
        AuditTrace trace = trace("trace-1", "检查系统负载");

        repository.save(trace);

        AuditTrace saved = repository.findByTraceId("trace-1").orElseThrow();
        assertSameTrace(saved, trace);
    }

    @Test
    void shouldReturnEmptyWhenTraceDoesNotExist() {
        assertThat(repository().findByTraceId("missing-trace")).isEmpty();
    }

    @Test
    void shouldListSavedTraces() {
        AuditTraceRepository repository = repository();
        AuditTrace first = trace("trace-1", "检查系统负载");
        AuditTrace second = trace("trace-2", "查看端口");

        repository.save(first);
        repository.save(second);

        assertThat(repository.findAll()).extracting(AuditTrace::getTraceId).containsExactlyInAnyOrder("trace-1", "trace-2");
    }

    @Test
    void shouldOverwriteTraceWithSameId() {
        AuditTraceRepository repository = repository();
        AuditTrace running = trace("trace-1", "检查系统负载");
        AuditTrace finished = trace("trace-1", "检查系统负载");
        finished.finish("COMPLETED", "系统负载正常");

        repository.save(running);
        repository.save(finished);

        AuditTrace saved = repository.findByTraceId("trace-1").orElseThrow();
        assertSameTrace(saved, finished);
        assertThat(saved.getStatus()).isEqualTo("COMPLETED");
    }

    protected AuditTrace trace(String traceId, String userInput) {
        AuditTrace trace = new AuditTrace(traceId, userInput);
        trace.addEvent(AuditEvent.success(traceId, AuditEventType.RECEIVE_REQUEST, Map.of("userInput", userInput)));
        return trace;
    }

    private void assertSameTrace(AuditTrace actual, AuditTrace expected) {
        assertThat(actual.getTraceId()).isEqualTo(expected.getTraceId());
        assertThat(actual.getUserInput()).isEqualTo(expected.getUserInput());
        assertThat(actual.getStatus()).isEqualTo(expected.getStatus());
        assertThat(actual.getFinalAnswer()).isEqualTo(expected.getFinalAnswer());
        assertThat(actual.getEvents()).hasSize(expected.getEvents().size());
        assertThat(actual.getEvents()).extracting(AuditEvent::eventType)
                .containsExactlyElementsOf(expected.getEvents().stream().map(AuditEvent::eventType).toList());
        assertThat(actual.hasValidHashChain()).isTrue();
    }
}
