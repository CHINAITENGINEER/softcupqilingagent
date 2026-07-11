package com.cup.opsagent.approval;

import com.cup.opsagent.tool.core.RiskLevel;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

abstract class ApprovalRepositoryContractTest {

    protected abstract ApprovalRepository repository();

    @Test
    void shouldSaveAndFindApproval() {
        ApprovalRepository repository = repository();
        ApprovalRecord approval = pendingApproval("approval-1", "nginx");

        repository.saveApproval(approval);

        assertThat(repository.findApproval("approval-1")).contains(approval);
    }

    @Test
    void shouldReturnEmptyWhenApprovalDoesNotExist() {
        assertThat(repository().findApproval("missing-approval")).isEmpty();
    }

    @Test
    void shouldOverwriteApprovalWithSameId() {
        ApprovalRepository repository = repository();
        ApprovalRecord pending = pendingApproval("approval-1", "nginx");
        ApprovalRecord rejected = pending.withStatus(ApprovalStatus.REJECTED, "admin", Instant.parse("2026-06-29T10:05:00Z"));

        repository.saveApproval(pending);
        repository.saveApproval(rejected);

        assertThat(repository.findApproval("approval-1")).contains(rejected);
    }

    @Test
    void shouldSaveAndFindExecutionLease() {
        ApprovalRepository repository = repository();
        ApprovalRecord approval = pendingApproval("approval-1", "nginx");
        ExecutionLease lease = lease("lease-1", approval);

        repository.saveApproval(approval);
        repository.saveLease(lease);

        assertThat(repository.findLease("lease-1")).contains(lease);
    }

    @Test
    void shouldReturnEmptyWhenLeaseDoesNotExist() {
        assertThat(repository().findLease("missing-lease")).isEmpty();
    }

    @Test
    void shouldOverwriteLeaseWithSameId() {
        ApprovalRepository repository = repository();
        ApprovalRecord approval = pendingApproval("approval-1", "nginx");
        ExecutionLease issued = lease("lease-1", approval);
        ExecutionLease consumed = issued.consume(Instant.parse("2026-06-29T10:03:00Z"));

        repository.saveApproval(approval);
        repository.saveLease(issued);
        repository.saveLease(consumed);

        assertThat(repository.findLease("lease-1")).contains(consumed);
    }

    protected ApprovalRecord pendingApproval(String approvalId, String serviceName) {
        Instant now = Instant.parse("2026-06-29T10:00:00Z");
        Map<String, Object> arguments = Map.of("serviceName", serviceName);
        return new ApprovalRecord(
                approvalId,
                "trace-1",
                "step-1",
                "requester-1",
                "restart_service",
                arguments,
                RiskLevel.MEDIUM,
                ActionHasher.hash("restart_service", arguments, RiskLevel.MEDIUM),
                ApprovalStatus.PENDING,
                "requires approval",
                now,
                now.plusSeconds(900),
                null,
                ""
        );
    }

    protected ExecutionLease lease(String leaseId, ApprovalRecord approval) {
        Instant now = Instant.parse("2026-06-29T10:00:00Z");
        return new ExecutionLease(
                leaseId,
                approval.approvalId(),
                approval.actionHash(),
                approval.toolName(),
                approval.canonicalArguments(),
                now,
                now.plusSeconds(300),
                null
        );
    }
}
