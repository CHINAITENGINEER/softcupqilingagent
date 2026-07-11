package com.cup.opsagent.verifier;

import com.cup.opsagent.executor.CommandExecutionOptions;
import com.cup.opsagent.executor.CommandRunner;
import com.cup.opsagent.executor.CommandTemplateId;
import com.cup.opsagent.executor.CommandTemplateRegistry;
import com.cup.opsagent.tool.builtin.RestartServiceTool;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ServiceRestartVerifierTest {

    @Test
    void shouldPassWhenServiceIsActiveAfterRestart() {
        StubCommandRunner commandRunner = new StubCommandRunner(new ToolExecutionResult(
                "ServiceRestartVerifier",
                true,
                "active\n",
                "",
                0,
                10,
                Instant.now()
        ));
        ServiceRestartVerifier verifier = new ServiceRestartVerifier(commandRunner);

        VerificationResult result = verifier.verify(context("nginx"));

        assertThat(result.verified()).isTrue();
        assertThat(result.reason()).contains("active after restart");
        assertThat(commandRunner.lastTemplateId).isEqualTo(CommandTemplateId.VERIFY_SERVICE_ACTIVE);
        assertThat(commandRunner.lastArgs).containsEntry("serviceName", "nginx");
        assertThat(result.evidence()).containsEntry("serviceName", "nginx");
        assertThat(result.evidence()).containsEntry("statusTemplateId", CommandTemplateId.VERIFY_SERVICE_ACTIVE.name());
        assertThat(result.evidence()).containsEntry("statusFailureCode", "none");
    }

    @Test
    void shouldFailWhenServiceIsNotActiveAfterRestart() {
        StubCommandRunner commandRunner = new StubCommandRunner(new ToolExecutionResult(
                "ServiceRestartVerifier",
                false,
                "failed\n",
                "",
                3,
                10,
                Instant.now()
        ));
        ServiceRestartVerifier verifier = new ServiceRestartVerifier(commandRunner);

        VerificationResult result = verifier.verify(context("nginx"));

        assertThat(result.verified()).isFalse();
        assertThat(result.reason()).contains("not active");
        assertThat(result.suggestedRecovery()).isNotEmpty();
        assertThat(result.evidence()).containsEntry("serviceStatus", "INACTIVE");
        assertThat(result.evidence()).containsEntry("statusTemplateId", CommandTemplateId.VERIFY_SERVICE_ACTIVE.name());
        assertThat(result.evidence()).containsEntry("statusFailureCode", "none");
        assertThat(result.evidence()).doesNotContainKeys("normalizedStatus", "systemctlStdout", "systemctlStderr");
    }

    @Test
    void shouldRejectInjectedServiceNameBeforeRunningCommand() {
        StubCommandRunner commandRunner = new StubCommandRunner(new ToolExecutionResult(
                "ServiceRestartVerifier",
                true,
                "active\n",
                "",
                0,
                10,
                Instant.now()
        ));
        ServiceRestartVerifier verifier = new ServiceRestartVerifier(commandRunner);

        VerificationResult result = verifier.verify(context("nginx; whoami"));

        assertThat(result.verified()).isFalse();
        assertThat(result.reason()).contains("shell injection");
        assertThat(commandRunner.lastTemplateId).isNull();
    }

    @Test
    void shouldRejectServiceNameStartingWithDashBeforeRunningCommand() {
        StubCommandRunner commandRunner = new StubCommandRunner(new ToolExecutionResult(
                "ServiceRestartVerifier",
                true,
                "active\n",
                "",
                0,
                10,
                Instant.now()
        ));
        ServiceRestartVerifier verifier = new ServiceRestartVerifier(commandRunner);

        VerificationResult result = verifier.verify(context("-Htcp"));

        assertThat(result.verified()).isFalse();
        assertThat(result.reason()).contains("must not start");
        assertThat(commandRunner.lastTemplateId).isNull();
    }

    @Test
    void shouldReturnFalseWhenDefinitionDoesNotMatchRestartService() {
        ServiceRestartVerifier verifier = new ServiceRestartVerifier(new StubCommandRunner(new ToolExecutionResult(
                "ServiceRestartVerifier",
                true,
                "active\n",
                "",
                0,
                10,
                Instant.now()
        )));
        ToolDefinition wrongDefinition = new ToolDefinition(
                RestartServiceTool.NAME,
                "wrong metadata",
                Map.of("serviceName", "string"),
                RiskLevel.LOW,
                true,
                false,
                PermissionRequirement.READ_ONLY_OS,
                1000,
                1024,
                true
        );

        assertThat(verifier.supports(new ToolCall(RestartServiceTool.NAME, Map.of("serviceName", "nginx"), "trace-1", "step-1"), wrongDefinition)).isFalse();
    }

    @Test
    void shouldRejectMissingArgumentsBeforeRunningCommand() {
        StubCommandRunner commandRunner = new StubCommandRunner(new ToolExecutionResult(
                "ServiceRestartVerifier",
                true,
                "active\n",
                "",
                0,
                10,
                Instant.now()
        ));
        ServiceRestartVerifier verifier = new ServiceRestartVerifier(commandRunner);
        VerificationContext context = new VerificationContext(
                "重启 nginx",
                "trace-1",
                new ToolCall(RestartServiceTool.NAME, null, "trace-1", "step-1"),
                definition(),
                new ToolExecutionResult(RestartServiceTool.NAME, true, "restart completed", "", 0, 100, Instant.now())
        );

        VerificationResult result = verifier.verify(context);

        assertThat(result.verified()).isFalse();
        assertThat(result.reason()).contains("serviceName is required");
        assertThat(commandRunner.lastTemplateId).isNull();
    }

    @Test
    void shouldSkipIsActiveCheckWhenRestartExecutionFailed() {
        StubCommandRunner commandRunner = new StubCommandRunner(new ToolExecutionResult(
                "ServiceRestartVerifier",
                true,
                "active\n",
                "",
                0,
                10,
                Instant.now()
        ));
        ServiceRestartVerifier verifier = new ServiceRestartVerifier(commandRunner);
        VerificationContext context = new VerificationContext(
                "重启 nginx",
                "trace-1",
                new ToolCall(RestartServiceTool.NAME, Map.of("serviceName", "nginx"), "trace-1", "step-1"),
                definition(),
                new ToolExecutionResult(RestartServiceTool.NAME, false, "", "restart failed", 1, 100, Instant.now())
        );

        VerificationResult result = verifier.verify(context);

        assertThat(result.verified()).isFalse();
        assertThat(result.reason()).contains("restart execution did not succeed");
        assertThat(commandRunner.lastTemplateId).isNull();
    }

    @Test
    void shouldNotExposeUnexpectedStatusStdoutAsEvidence() {
        StubCommandRunner commandRunner = new StubCommandRunner(new ToolExecutionResult(
                "ServiceRestartVerifier",
                true,
                "unexpected /etc/systemd/system/nginx.service details\n",
                "",
                0,
                10,
                Instant.now()
        ));
        ServiceRestartVerifier verifier = new ServiceRestartVerifier(commandRunner);

        VerificationResult result = verifier.verify(context("nginx"));

        assertThat(result.verified()).isFalse();
        assertThat(result.evidence()).containsEntry("serviceStatus", "UNKNOWN");
        assertThat(result.evidence()).doesNotContainValue("unexpected /etc/systemd/system/nginx.service details");
    }

    @Test
    void shouldIncludeStatusFailureCodeWhenStatusCommandFailsAtRunnerLayer() {
        StubCommandRunner commandRunner = new StubCommandRunner(new ToolExecutionResult(
                "ServiceRestartVerifier",
                false,
                "",
                "process start failed",
                null,
                10,
                Instant.now(),
                ToolExecutionFailureCode.PROCESS_START_FAILED
        ));
        ServiceRestartVerifier verifier = new ServiceRestartVerifier(commandRunner);

        VerificationResult result = verifier.verify(context("nginx"));

        assertThat(result.verified()).isFalse();
        assertThat(result.evidence()).containsEntry("statusFailureCode", ToolExecutionFailureCode.PROCESS_START_FAILED.name());
    }

    @Test
    void shouldRejectNullCommandRunnerInConstructor() {
        assertThatThrownBy(() -> new ServiceRestartVerifier(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("commandRunner");
    }

    private VerificationContext context(String serviceName) {
        return new VerificationContext(
                "重启 " + serviceName,
                "trace-1",
                new ToolCall(RestartServiceTool.NAME, Map.of("serviceName", serviceName), "trace-1", "step-1"),
                definition(),
                new ToolExecutionResult(RestartServiceTool.NAME, true, "restart completed", "", 0, 100, Instant.now())
        );
    }

    private ToolDefinition definition() {
        return new ToolDefinition(
                RestartServiceTool.NAME,
                "restart service",
                Map.of("serviceName", "string"),
                RiskLevel.MEDIUM,
                false,
                true,
                PermissionRequirement.SERVICE_CONTROL,
                1000,
                1024,
                true
        );
    }

    private static class StubCommandRunner extends CommandRunner {
        private final ToolExecutionResult result;
        private CommandTemplateId lastTemplateId;
        private Map<String, Object> lastArgs;

        private StubCommandRunner(ToolExecutionResult result) {
            super(new CommandTemplateRegistry());
            this.result = result;
        }

        @Override
        public ToolExecutionResult run(String toolName, CommandTemplateId templateId, Map<String, Object> args, CommandExecutionOptions options) {
            this.lastTemplateId = templateId;
            this.lastArgs = args;
            return result;
        }
    }
}
