package com.cup.opsagent.approval;

import java.util.Optional;

public interface ApprovalRepository {

    void saveApproval(ApprovalRecord approvalRecord);

    Optional<ApprovalRecord> findApproval(String approvalId);

    void saveLease(ExecutionLease executionLease);

    Optional<ExecutionLease> findLease(String leaseId);
}
