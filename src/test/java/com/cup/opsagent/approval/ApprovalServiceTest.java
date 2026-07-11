package com.cup.opsagent.approval;

import com.cup.opsagent.tool.core.RiskLevel;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ApprovalServiceTest {

    private static final Instant NOW = Instant.parse("2026-06-29T10:00:00Z");

    @Test
    void shouldCreatePendingApprovalWithStableActionHash() {
        ApprovalService service = serviceAt(NOW);

        ApprovalRecord record = service.requestApproval(
                "trace-1",
                "step-1",
                "requester-1",
                "restart_service",
                Map.of("serviceName", "nginx"),
                RiskLevel.MEDIUM,
                "requires approval"
        );

        assertThat(record.status()).isEqualTo(ApprovalStatus.PENDING);
        assertThat(record.actionHash()).isEqualTo(ActionHasher.hash("restart_service", Map.of("serviceName", "nginx"), RiskLevel.MEDIUM));
        assertThat(record.expiresAt()).isAfter(record.createdAt());
        assertThat(service.findApproval(record.approvalId())).contains(record);
    }

    @Test
    void shouldKeepActionHashStableWhenArgumentOrderChanges() {
        String first = ActionHasher.hash("restart_service", Map.of("serviceName", "nginx", "mode", "safe"), RiskLevel.MEDIUM);
        String second = ActionHasher.hash("restart_service", Map.of("mode", "safe", "serviceName", "nginx"), RiskLevel.MEDIUM);

        assertThat(first).isEqualTo(second);
    }

    @Test
    void shouldChangeActionHashWhenArgumentsChange() {
        String original = ActionHasher.hash("restart_service", Map.of("serviceName", "nginx"), RiskLevel.MEDIUM);
        String tampered = ActionHasher.hash("restart_service", Map.of("serviceName", "mysql"), RiskLevel.MEDIUM);

        assertThat(original).isNotEqualTo(tampered);
    }

    @Test
    void shouldApprovePendingApprovalAndIssueLease() {
        ApprovalService service = serviceAt(NOW);
        ApprovalRecord record = requestRestartApproval(service);

        ExecutionLease lease = service.approve(record.approvalId(), "admin");

        ApprovalRecord approved = service.findApproval(record.approvalId()).orElseThrow();
        assertThat(approved.status()).isEqualTo(ApprovalStatus.APPROVED);
        assertThat(approved.decidedBy()).isEqualTo("admin");
        assertThat(lease.approvalId()).isEqualTo(record.approvalId());
        assertThat(lease.actionHash()).isEqualTo(record.actionHash());
        assertThat(lease.consumed()).isFalse();
    }

    @Test
    void shouldRejectPendingApproval() {
        ApprovalService service = serviceAt(NOW);
        ApprovalRecord record = requestRestartApproval(service);

        ApprovalRecord rejected = service.reject(record.approvalId(), "admin");

        assertThat(rejected.status()).isEqualTo(ApprovalStatus.REJECTED);
        assertThatThrownBy(() -> service.approve(record.approvalId(), "admin"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("approval is not pending");
    }

    @Test
    void shouldConsumeLeaseOnlyOnce() {
        ApprovalService service = serviceAt(NOW);
        ApprovalRecord record = requestRestartApproval(service);
        ExecutionLease lease = service.approve(record.approvalId(), "admin");

        ExecutionLease consumed = service.consumeLease(lease.leaseId(), "restart_service", Map.of("serviceName", "nginx"), RiskLevel.MEDIUM);

        assertThat(consumed.consumed()).isTrue();
        assertThat(service.findApproval(record.approvalId()).orElseThrow().status()).isEqualTo(ApprovalStatus.CONSUMED);
        assertThatThrownBy(() -> service.consumeLease(lease.leaseId(), "restart_service", Map.of("serviceName", "nginx"), RiskLevel.MEDIUM))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("already been consumed");
    }

    @Test
    void shouldRejectLeaseConsumptionWhenActionIsTampered() {
        ApprovalService service = serviceAt(NOW);
        ApprovalRecord record = requestRestartApproval(service);
        ExecutionLease lease = service.approve(record.approvalId(), "admin");

        assertThatThrownBy(() -> service.consumeLease(lease.leaseId(), "restart_service", Map.of("serviceName", "mysql"), RiskLevel.MEDIUM))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("action hash mismatch");
        assertThat(service.findApproval(record.approvalId()).orElseThrow().status()).isEqualTo(ApprovalStatus.APPROVED);
    }

    @Test
    void shouldRejectExpiredApprovalWhenApproving() {
        MutableClock clock = new MutableClock(NOW);
        ApprovalService service = new ApprovalService(clock);
        ApprovalRecord record = requestRestartApproval(service);
        clock.setInstant(NOW.plusSeconds(16 * 60));

        assertThatThrownBy(() -> service.approve(record.approvalId(), "admin"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("approval has expired");
        assertThat(service.findApproval(record.approvalId()).orElseThrow().status()).isEqualTo(ApprovalStatus.EXPIRED);
    }

    @Test
    void shouldRejectExpiredLeaseConsumption() {
        MutableClock clock = new MutableClock(NOW);
        ApprovalService service = new ApprovalService(clock);
        ApprovalRecord record = requestRestartApproval(service);
        ExecutionLease lease = service.approve(record.approvalId(), "admin");
        clock.setInstant(NOW.plusSeconds(6 * 60));

        assertThatThrownBy(() -> service.consumeLease(lease.leaseId(), "restart_service", Map.of("serviceName", "nginx"), RiskLevel.MEDIUM))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("execution lease has expired");
    }

    private ApprovalRecord requestRestartApproval(ApprovalService service) {
        return service.requestApproval(
                "trace-1",
                "step-1",
                "requester-1",
                "restart_service",
                Map.of("serviceName", "nginx"),
                RiskLevel.MEDIUM,
                "requires approval"
        );
    }

    private ApprovalService serviceAt(Instant instant) {
        return new ApprovalService(Clock.fixed(instant, ZoneOffset.UTC));
    }

    private static final class MutableClock extends Clock {
        private Instant instant;

        private MutableClock(Instant instant) {
            this.instant = instant;
        }

        private void setInstant(Instant instant) {
            this.instant = instant;
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }
}
