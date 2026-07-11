package com.cup.opsagent.verifier;

import com.cup.opsagent.tool.builtin.SystemLoadTool;
import com.cup.opsagent.tool.core.PermissionRequirement;
import com.cup.opsagent.tool.core.RiskLevel;
import com.cup.opsagent.tool.core.ToolCall;
import com.cup.opsagent.tool.core.ToolDefinition;
import com.cup.opsagent.tool.core.ToolExecutionFailureCode;
import com.cup.opsagent.tool.core.ToolExecutionResult;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ReadOnlyToolVerifierTest {

    private final ReadOnlyToolVerifier verifier = new ReadOnlyToolVerifier();

    @Test
    void shouldPassWhenReadOnlyToolSucceededWithOutput() {
        VerificationResult result = verifier.verify(new VerificationContext(
                "检查系统负载",
                "trace-1",
                new ToolCall(SystemLoadTool.NAME, Map.of(), "trace-1", "step-1"),
                readOnlyDefinition(SystemLoadTool.NAME),
                new ToolExecutionResult(SystemLoadTool.NAME, true, "load average: 0.10", "", 0, 12, Instant.now())
        ));

        assertThat(result.verified()).isTrue();
        assertThat(result.verifierName()).isEqualTo("ReadOnlyToolVerifier");
        assertThat(result.evidence()).containsEntry("toolName", SystemLoadTool.NAME);
        assertThat(result.evidence()).containsEntry("executionFailureCode", "none");
    }

    @Test
    void shouldFailWhenReadOnlyToolExecutionFailed() {
        VerificationResult result = verifier.verify(new VerificationContext(
                "检查系统负载",
                "trace-1",
                new ToolCall(SystemLoadTool.NAME, Map.of(), "trace-1", "step-1"),
                readOnlyDefinition(SystemLoadTool.NAME),
                new ToolExecutionResult(SystemLoadTool.NAME, false, "", "command not found", null, 5, Instant.now())
        ));

        assertThat(result.verified()).isFalse();
        assertThat(result.reason()).contains("did not succeed");
        assertThat(result.suggestedRecovery()).isNotEmpty();
    }

    @Test
    void shouldIncludeFailureCodeInEvidenceWhenPresent() {
        VerificationResult result = verifier.verify(new VerificationContext(
                "检查系统负载",
                "trace-1",
                new ToolCall(SystemLoadTool.NAME, Map.of(), "trace-1", "step-1"),
                readOnlyDefinition(SystemLoadTool.NAME),
                new ToolExecutionResult(
                        SystemLoadTool.NAME,
                        false,
                        "",
                        "process start failed",
                        null,
                        5,
                        Instant.now(),
                        ToolExecutionFailureCode.PROCESS_START_FAILED
                )
        ));

        assertThat(result.verified()).isFalse();
        assertThat(result.evidence()).containsEntry("executionFailureCode", ToolExecutionFailureCode.PROCESS_START_FAILED.name());
    }

    @Test
    void shouldFailWhenReadOnlyToolReturnedEmptyOutput() {
        VerificationResult result = verifier.verify(new VerificationContext(
                "检查系统负载",
                "trace-1",
                new ToolCall(SystemLoadTool.NAME, Map.of(), "trace-1", "step-1"),
                readOnlyDefinition(SystemLoadTool.NAME),
                new ToolExecutionResult(SystemLoadTool.NAME, true, "", "", 0, 5, Instant.now())
        ));

        assertThat(result.verified()).isFalse();
        assertThat(result.reason()).contains("empty output");
    }

    private ToolDefinition readOnlyDefinition(String name) {
        return new ToolDefinition(
                name,
                "test tool",
                Map.of(),
                RiskLevel.LOW,
                true,
                false,
                PermissionRequirement.READ_ONLY_OS,
                1000,
                1024,
                true
        );
    }
}
