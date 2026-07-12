package com.cup.opsagent.api;

import com.cup.opsagent.approval.ApprovalRecord;
import com.cup.opsagent.approval.ApprovalService;
import com.cup.opsagent.audit.AuditLogService;
import com.cup.opsagent.audit.AuditEventType;
import com.cup.opsagent.audit.AuditTrace;
import com.cup.opsagent.approval.ApprovalStatus;
import com.cup.opsagent.tool.core.RiskLevel;
import com.cup.opsagent.rag.RagProperties;
import com.cup.opsagent.tool.core.ToolRegistry;
import org.springframework.boot.info.BuildProperties;
import org.springframework.core.env.Environment;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import java.util.LinkedHashMap;
import java.time.ZoneOffset;
import java.util.List;

@RestController
@RequestMapping("/api/system")
public class SystemStatusController {

    private final Environment environment;
    private final Optional<BuildProperties> buildProperties;
    private final ApprovalService approvalService;
    private final AuditLogService auditLogService;
    private final ToolRegistry toolRegistry;
    private final RagProperties ragProperties;

    public SystemStatusController(
            Environment environment,
            Optional<BuildProperties> buildProperties,
            ApprovalService approvalService,
            AuditLogService auditLogService,
            ToolRegistry toolRegistry,
            RagProperties ragProperties
    ) {
        this.environment = environment;
        this.buildProperties = buildProperties;
        this.approvalService = approvalService;
        this.auditLogService = auditLogService;
        this.toolRegistry = toolRegistry;
        this.ragProperties = ragProperties;
    }

    @GetMapping("/status")
    public SystemStatusResponse status() {
        long pendingApprovals = approvalService.listApprovals().stream()
                .filter(approval -> approval.status().name().equals("PENDING"))
                .count();
        long highRiskPendingApprovals = approvalService.listApprovals().stream()
                .filter(approval -> approval.status().name().equals("PENDING"))
                .filter(this::isHighRisk)
                .count();
        return new SystemStatusResponse(
                "UP",
                "QilingOS SafeOps Agent",
                buildProperties.map(BuildProperties::getVersion).orElse("0.1.0-SNAPSHOT"),
                Arrays.asList(environment.getActiveProfiles()),
                Instant.now(),
                new SystemStatusResponse.AgentStatus("ONLINE", toolRegistry.listDefinitions().size()),
                new SystemStatusResponse.ApprovalStatus(approvalService.listApprovals().size(), pendingApprovals, highRiskPendingApprovals),
                new SystemStatusResponse.AuditStatus(auditLogService.listTraces().size(), "ENABLED"),
                new SystemStatusResponse.RagStatus(ragProperties.isEnabled(), safeValue(ragProperties.getRetrieverMode()), safeValue(ragProperties.getVectorStoreProvider())),
                Map.of(
                        "javaVersion", System.getProperty("java.version", ""),
                        "osName", System.getProperty("os.name", ""),
                        "availableProcessors", Runtime.getRuntime().availableProcessors()
                )
        );
    }

    /** Aggregated, payload-free metrics for the console dashboard. */
    @GetMapping("/metrics")
    public SystemMetricsResponse metrics() {
        Instant generatedAt = Instant.now();
        List<ApprovalRecord> approvals = approvalService.listApprovals();
        List<AuditTrace> traces = auditLogService.listTraces().stream().toList();
        Map<String, Long> risks = enumCounts(RiskLevel.values(), approvals.stream()
                .collect(java.util.stream.Collectors.groupingBy(item -> item.riskLevel().name(), java.util.stream.Collectors.counting())));
        Map<String, Long> statuses = enumCounts(ApprovalStatus.values(), approvals.stream()
                .collect(java.util.stream.Collectors.groupingBy(item -> item.status().name(), java.util.stream.Collectors.counting())));
        long successful = traces.stream().flatMap(trace -> trace.getEvents().stream())
                .filter(event -> event.eventType() == AuditEventType.VERIFICATION_PASSED).count();
        long failed = traces.stream().flatMap(trace -> trace.getEvents().stream())
                .filter(event -> event.eventType() == AuditEventType.VERIFICATION_FAILED).count();
        long total = successful + failed;
        List<OperationTrendPoint> trend = java.util.stream.IntStream.rangeClosed(0, 23)
                .mapToObj(offset -> generatedAt.minus(java.time.Duration.ofHours(23L - offset)).atZone(ZoneOffset.UTC))
                .map(hour -> new OperationTrendPoint(String.format("%02d", hour.getHour()), traces.stream()
                        .filter(trace -> trace.getStartedAt().atZone(ZoneOffset.UTC).getYear() == hour.getYear()
                                && trace.getStartedAt().atZone(ZoneOffset.UTC).getDayOfYear() == hour.getDayOfYear()
                                && trace.getStartedAt().atZone(ZoneOffset.UTC).getHour() == hour.getHour()).count()))
                .toList();
        long retrievals = traces.stream().flatMap(trace -> trace.getEvents().stream())
                .filter(event -> event.eventType() == AuditEventType.RAG_RETRIEVAL_COMPLETED).count();
        return new SystemMetricsResponse(generatedAt, 24, trend, risks, statuses,
                new VerificationMetrics(total, successful, failed, total == 0 ? 0.0 : (double) successful / total),
                traces.size(), retrievals);
    }

    private <E extends Enum<E>> Map<String, Long> enumCounts(E[] values, Map<String, Long> source) {
        Map<String, Long> result = new LinkedHashMap<>();
        for (E value : values) result.put(value.name(), source.getOrDefault(value.name(), 0L));
        return result;
    }

    private boolean isHighRisk(ApprovalRecord approval) {
        String risk = approval.riskLevel().name();
        return risk.equals("HIGH") || risk.equals("CRITICAL");
    }

    private String safeValue(String value) {
        return value == null ? "" : value;
    }

    public record SystemStatusResponse(
            String status,
            String serviceName,
            String version,
            java.util.List<String> activeProfiles,
            Instant serverTime,
            AgentStatus agent,
            ApprovalStatus approvals,
            AuditStatus audit,
            RagStatus rag,
            Map<String, Object> runtime
    ) {
        public record AgentStatus(String status, int registeredToolCount) {
        }

        public record ApprovalStatus(long total, long pending, long highRiskPending) {
        }

        public record AuditStatus(long traceCount, String integrityMode) {
        }

        public record RagStatus(boolean enabled, String retrieverMode, String vectorStoreProvider) {
        }
    }

    public record SystemMetricsResponse(Instant generatedAt, int windowHours, List<OperationTrendPoint> operationTrend,
                                        Map<String, Long> riskDistribution, Map<String, Long> approvalStatusDistribution,
                                        VerificationMetrics verification, long auditTraceCount, long ragRetrievalCount) { }
    public record OperationTrendPoint(String hour, long count) { }
    public record VerificationMetrics(long total, long successful, long failed, double successRate) { }
}
