package com.cup.opsagent.audit;

import java.util.Collection;
import java.util.Optional;

public interface AuditTraceRepository {

    void save(AuditTrace auditTrace);

    Optional<AuditTrace> findByTraceId(String traceId);

    Collection<AuditTrace> findAll();
}
