package com.cup.opsagent.verifier;

import com.cup.opsagent.tool.core.PermissionRequirement;
import com.cup.opsagent.tool.core.RiskLevel;
import com.cup.opsagent.tool.core.ToolCall;
import com.cup.opsagent.tool.core.ToolDefinition;
import com.cup.opsagent.tool.core.ToolExecutionResult;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class VerificationOrchestratorTest {

    @Test
    void shouldFailWhenNoVerifierSupportsTool() {
        VerificationOrchestrator orchestrator = new VerificationOrchestrator(List.of(new ReadOnlyToolVerifier()));
        ToolDefinition writeTool = new ToolDefinition(
                "restart_service",
                "test write tool",
                Map.of(),
                RiskLevel.MEDIUM,
                false,
                true,
                PermissionRequirement.SERVICE_CONTROL,
                1000,
                1024,
                true
        );

        VerificationResult result = orchestrator.verify(new VerificationContext(
                "重启 nginx",
                "trace-1",
                new ToolCall("restart_service", Map.of("serviceName", "nginx"), "trace-1", "step-1"),
                writeTool,
                new ToolExecutionResult("restart_service", true, "", "", 0, 10, Instant.now())
        ));

        assertThat(result.verified()).isFalse();
        assertThat(result.verifierName()).isEqualTo("NoMatchingVerifier");
        assertThat(result.suggestedRecovery()).contains("为该工具实现专用 ExecutionVerifier");
    }

    @Test
    void shouldNormalizeNullVerificationResultFields() {
        VerificationResult result = new VerificationResult(null, false, null, null, null, null);

        assertThat(result.verifierName()).isEqualTo("UnknownVerifier");
        assertThat(result.reason()).isEmpty();
        assertThat(result.evidence()).isEmpty();
        assertThat(result.suggestedRecovery()).isEmpty();
        assertThat(result.checkedAt()).isNotNull();
    }

    @Test
    void shouldNormalizeNestedNullVerificationResultFields() {
        java.util.Map<String, Object> evidence = new java.util.LinkedHashMap<>();
        evidence.put("k", null);
        evidence.put(null, "dropped");
        java.util.List<String> recovery = new java.util.ArrayList<>();
        recovery.add("manual check");
        recovery.add(null);

        VerificationResult result = new VerificationResult("Verifier", false, null, evidence, recovery, null);

        assertThat(result.evidence()).containsEntry("k", "");
        assertThat(result.evidence()).doesNotContainKey(null);
        assertThat(result.suggestedRecovery()).containsExactly("manual check");
    }
}
