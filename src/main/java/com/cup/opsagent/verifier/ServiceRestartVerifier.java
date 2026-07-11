package com.cup.opsagent.verifier;

import com.cup.opsagent.executor.CommandExecutionOptions;
import com.cup.opsagent.executor.CommandRunner;
import com.cup.opsagent.executor.CommandTemplateId;
import com.cup.opsagent.safety.validation.ServiceNameValidator;
import com.cup.opsagent.tool.builtin.RestartServiceTool;
import com.cup.opsagent.tool.core.ToolCall;
import com.cup.opsagent.tool.core.ToolDefinition;
import com.cup.opsagent.tool.core.ToolExecutionResult;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

@Component
@Order(50)
public class ServiceRestartVerifier implements ExecutionVerifier {

    private static final long VERIFY_TIMEOUT_MS = 3000;
    private static final int VERIFY_OUTPUT_LIMIT_BYTES = 4096;

    private final CommandRunner commandRunner;

    public ServiceRestartVerifier(CommandRunner commandRunner) {
        this.commandRunner = Objects.requireNonNull(commandRunner, "commandRunner must not be null");
    }

    @Override
    public String name() {
        return "ServiceRestartVerifier";
    }

    @Override
    public boolean supports(ToolCall toolCall, ToolDefinition toolDefinition) {
        return toolCall != null
                && toolDefinition != null
                && RestartServiceTool.NAME.equals(toolCall.toolName())
                && RestartServiceTool.NAME.equals(toolDefinition.name())
                && toolDefinition.requiresApproval()
                && !toolDefinition.readOnly();
    }

    @Override
    public VerificationResult verify(VerificationContext context) {
        if (context == null || context.toolCall() == null) {
            return VerificationResult.failed(
                    name(),
                    "toolCall is required for restart verification",
                    Map.of(),
                    List.of("重新生成包含 restart_service ToolCall 的验证上下文")
            );
        }

        Optional<String> validationError = validateServiceName(context.toolCall());
        if (validationError.isPresent()) {
            return VerificationResult.failed(
                    name(),
                    validationError.get(),
                    Map.of("toolName", context.toolCall().toolName()),
                    List.of("拒绝验证非法 serviceName", "重新生成符合 systemd service 名称规则的操作")
            );
        }

        if (context.executionResult() == null || !context.executionResult().success()) {
            return VerificationResult.failed(
                    name(),
                    "restart execution did not succeed; skip service active verification",
                    Map.of(
                            "toolName", context.toolCall().toolName(),
                            "serviceName", context.toolCall().arguments().get("serviceName"),
                            "restartSuccess", context.executionResult() != null && context.executionResult().success(),
                            "restartExitCode", context.executionResult() == null || context.executionResult().exitCode() == null ? "none" : context.executionResult().exitCode()
                    ),
                    List.of("人工排查建议：先确认重启操作本身是否被审批并执行成功")
            );
        }

        String serviceName = (String) context.toolCall().arguments().get("serviceName");
        ToolExecutionResult statusResult = commandRunner.run(
                name(),
                CommandTemplateId.VERIFY_SERVICE_ACTIVE,
                Map.of("serviceName", serviceName),
                new CommandExecutionOptions(VERIFY_TIMEOUT_MS, VERIFY_OUTPUT_LIMIT_BYTES)
        );
        ServiceStatus serviceStatus = classifyStatus(statusResult.stdout());
        Map<String, Object> evidence = Map.of(
                "toolName", context.toolCall().toolName(),
                "serviceName", serviceName,
                "statusTemplateId", CommandTemplateId.VERIFY_SERVICE_ACTIVE.name(),
                "statusSuccess", statusResult.success(),
                "statusExitCode", statusResult.exitCode() == null ? "none" : statusResult.exitCode(),
                "statusFailureCode", statusResult.failureCode() == null ? "none" : statusResult.failureCode().name(),
                "serviceStatus", serviceStatus.name(),
                "stdoutLength", statusResult.stdout() == null ? 0 : statusResult.stdout().length(),
                "stderrLength", statusResult.stderr() == null ? 0 : statusResult.stderr().length(),
                "statusDurationMs", statusResult.durationMs()
        );

        if (statusResult.success() && serviceStatus == ServiceStatus.ACTIVE) {
            return VerificationResult.passed(name(), "service is active after restart", evidence);
        }

        return VerificationResult.failed(
                name(),
                "service is not active after restart",
                evidence,
                List.of(
                        "人工排查建议：检查服务当前状态，不要自动执行诊断命令",
                        "人工排查建议：查看该服务最近错误日志，不要自动执行日志读取命令",
                        "人工排查建议：确认服务配置文件和依赖端口是否正常"
                )
        );
    }

    private Optional<String> validateServiceName(ToolCall toolCall) {
        if (toolCall == null || toolCall.arguments() == null) {
            return Optional.of("serviceName is required for restart verification");
        }
        return ServiceNameValidator.validate(toolCall.arguments().get("serviceName"))
                .map(reason -> reason.replace("serviceName is required", "serviceName is required for restart verification"));
    }

    private ServiceStatus classifyStatus(String stdout) {
        String status = stdout == null ? "" : stdout.trim();
        if ("active".equals(status)) {
            return ServiceStatus.ACTIVE;
        }
        if ("inactive".equals(status) || "failed".equals(status) || "deactivating".equals(status) || "activating".equals(status)) {
            return ServiceStatus.INACTIVE;
        }
        return ServiceStatus.UNKNOWN;
    }

    private enum ServiceStatus {
        ACTIVE,
        INACTIVE,
        UNKNOWN
    }
}
