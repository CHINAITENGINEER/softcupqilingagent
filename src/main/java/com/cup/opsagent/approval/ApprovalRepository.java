package com.cup.opsagent.approval;

import java.util.List;
import java.util.Optional;

public interface ApprovalRepository {

    void saveApproval(ApprovalRecord approvalRecord);

    Optional<ApprovalRecord> findApproval(String approvalId);

    List<ApprovalRecord> listApprovals();

    void saveLease(ExecutionLease executionLease);

    Optional<ExecutionLease> findLease(String leaseId);
}
