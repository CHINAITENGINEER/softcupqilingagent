package com.cup.opsagent.api;

import com.cup.opsagent.approval.ApprovalRecord;
import com.cup.opsagent.approval.ApprovalService;
import com.cup.opsagent.audit.AuditLogService;
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
}
