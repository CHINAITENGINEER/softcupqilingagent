package com.cup.opsagent.audit;

class InMemoryAuditTraceRepositoryTest extends AuditTraceRepositoryContractTest {

    @Override
    protected AuditTraceRepository repository() {
        return new InMemoryAuditTraceRepository();
    }
}
