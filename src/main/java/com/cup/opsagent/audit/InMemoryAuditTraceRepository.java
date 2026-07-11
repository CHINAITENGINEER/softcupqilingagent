package com.cup.opsagent.audit;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Repository
@Profile("!postgres")
public class InMemoryAuditTraceRepository implements AuditTraceRepository {

    private final Map<String, AuditTrace> traces = new ConcurrentHashMap<>();

    @Override
    public void save(AuditTrace auditTrace) {
        traces.put(auditTrace.getTraceId(), auditTrace);
    }

    @Override
    public Optional<AuditTrace> findByTraceId(String traceId) {
        return Optional.ofNullable(traces.get(traceId));
    }

    @Override
    public Collection<AuditTrace> findAll() {
        return traces.values();
    }
}
