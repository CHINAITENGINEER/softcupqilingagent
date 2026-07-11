package com.cup.opsagent.approval;

import com.cup.opsagent.tool.core.RiskLevel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
public class ApprovalService {

    private static final Duration DEFAULT_APPROVAL_TTL = Duration.ofMinutes(15);
    private static final Duration DEFAULT_LEASE_TTL = Duration.ofMinutes(5);

    private final ApprovalRepository approvalRepository;
    private final Clock clock;

    @Autowired
    public ApprovalService(ApprovalRepository approvalRepository) {
        this(approvalRepository, Clock.systemUTC());
    }

    ApprovalService(Clock clock) {
        this(new InMemoryApprovalRepository(), clock);
    }

    ApprovalService(ApprovalRepository approvalRepository, Clock clock) {
        this.approvalRepository = approvalRepository == null ? new InMemoryApprovalRepository() : approvalRepository;
        this.clock = clock == null ? Clock.systemUTC() : clock;
    }

    public ApprovalRecord requestApproval(
            String traceId,
            String stepId,
            String requesterId,
            String toolName,
            Map<String, Object> arguments,
            RiskLevel riskLevel,
            String reason
    ) {
        Instant now = clock.instant();
        Map<String, Object> canonicalArguments = arguments == null ? Map.of() : Map.copyOf(arguments);
        String actionHash = ActionHasher.hash(toolName, canonicalArguments, riskLevel);
        ApprovalRecord record = new ApprovalRecord(
                UUID.randomUUID().toString(),
                traceId,
                stepId,
                requesterId,
                toolName,
                canonicalArguments,
                riskLevel,
                actionHash,
                ApprovalStatus.PENDING,
                reason,
                now,
                now.plus(DEFAULT_APPROVAL_TTL),
                null,
                ""
        );
        approvalRepository.saveApproval(record);
        return record;
    }

    public ExecutionLease approve(String approvalId, String approver) {
        ApprovalRecord record = currentApproval(approvalId);
        Instant now = clock.instant();
        if (record.status() != ApprovalStatus.PENDING) {
            throw new IllegalStateException("approval is not pending: " + record.status());
        }
        if (!now.isBefore(record.expiresAt())) {
            ApprovalRecord expired = record.withStatus(ApprovalStatus.EXPIRED, approver, now);
            approvalRepository.saveApproval(expired);
            throw new IllegalStateException("approval has expired");
        }
        ApprovalRecord approved = record.withStatus(ApprovalStatus.APPROVED, approver, now);
        approvalRepository.saveApproval(approved);

        ExecutionLease lease = new ExecutionLease(
                UUID.randomUUID().toString(),
                approvalId,
                approved.actionHash(),
                approved.toolName(),
                approved.canonicalArguments(),
                now,
                now.plus(DEFAULT_LEASE_TTL),
                null
        );
        approvalRepository.saveLease(lease);
        return lease;
    }

    public ApprovalRecord reject(String approvalId, String approver) {
        ApprovalRecord record = currentApproval(approvalId);
        Instant now = clock.instant();
        if (record.status() != ApprovalStatus.PENDING) {
            throw new IllegalStateException("approval is not pending: " + record.status());
        }
        ApprovalRecord rejected = record.withStatus(ApprovalStatus.REJECTED, approver, now);
        approvalRepository.saveApproval(rejected);
        return rejected;
    }

    public ExecutionLease consumeLease(String leaseId, String toolName, Map<String, Object> arguments, RiskLevel riskLevel) {
        ExecutionLease lease = currentLease(leaseId);
        Instant now = clock.instant();
        if (lease.consumed()) {
            throw new IllegalStateException("execution lease has already been consumed");
        }
        if (lease.expiredAt(now)) {
            throw new IllegalStateException("execution lease has expired");
        }
        String actionHash = ActionHasher.hash(toolName, arguments, riskLevel);
        if (!lease.actionHash().equals(actionHash)) {
            throw new IllegalStateException("execution lease action hash mismatch");
        }
        ApprovalRecord approval = currentApproval(lease.approvalId());
        if (approval.status() != ApprovalStatus.APPROVED) {
            throw new IllegalStateException("approval is not approved: " + approval.status());
        }
        ExecutionLease consumed = lease.consume(now);
        approvalRepository.saveLease(consumed);
        approvalRepository.saveApproval(approval.withStatus(ApprovalStatus.CONSUMED, approval.decidedBy(), now));
        return consumed;
    }

    public Optional<ApprovalRecord> findApproval(String approvalId) {
        return approvalRepository.findApproval(approvalId);
    }

    public Optional<ExecutionLease> findLease(String leaseId) {
        return approvalRepository.findLease(leaseId);
    }

    private ApprovalRecord currentApproval(String approvalId) {
        if (approvalId == null || approvalId.isBlank()) {
            throw new IllegalArgumentException("approvalId must not be blank");
        }
        return approvalRepository.findApproval(approvalId)
                .orElseThrow(() -> new IllegalArgumentException("approval not found: " + approvalId));
    }

    private ExecutionLease currentLease(String leaseId) {
        if (leaseId == null || leaseId.isBlank()) {
            throw new IllegalArgumentException("leaseId must not be blank");
        }
        return approvalRepository.findLease(leaseId)
                .orElseThrow(() -> new IllegalArgumentException("execution lease not found: " + leaseId));
    }
}
