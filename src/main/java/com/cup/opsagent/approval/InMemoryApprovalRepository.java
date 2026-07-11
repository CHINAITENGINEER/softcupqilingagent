package com.cup.opsagent.approval;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Repository
@Profile("!postgres")
public class InMemoryApprovalRepository implements ApprovalRepository {

    private final Map<String, ApprovalRecord> approvals = new ConcurrentHashMap<>();
    private final Map<String, ExecutionLease> leases = new ConcurrentHashMap<>();

    @Override
    public void saveApproval(ApprovalRecord approvalRecord) {
        approvals.put(approvalRecord.approvalId(), approvalRecord);
    }

    @Override
    public Optional<ApprovalRecord> findApproval(String approvalId) {
        return Optional.ofNullable(approvals.get(approvalId));
    }

    @Override
    public void saveLease(ExecutionLease executionLease) {
        leases.put(executionLease.leaseId(), executionLease);
    }

    @Override
    public Optional<ExecutionLease> findLease(String leaseId) {
        return Optional.ofNullable(leases.get(leaseId));
    }
}
