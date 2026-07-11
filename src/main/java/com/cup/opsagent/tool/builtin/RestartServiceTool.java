package com.cup.opsagent.tool.builtin;

import com.cup.opsagent.executor.CommandRunner;
import com.cup.opsagent.executor.CommandTemplateId;
import com.cup.opsagent.safety.validation.ServiceNameValidator;
import com.cup.opsagent.tool.core.OpsTool;
import com.cup.opsagent.tool.core.PermissionRequirement;
import com.cup.opsagent.tool.core.RiskLevel;
import com.cup.opsagent.tool.core.ToolCall;
import com.cup.opsagent.tool.core.ToolDefinition;
import com.cup.opsagent.tool.core.ToolExecutionResult;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class RestartServiceTool extends AbstractBuiltinTool implements OpsTool {

    public static final String NAME = "restart_service";

    public RestartServiceTool(CommandRunner commandRunner) {
        super(commandRunner);
    }

    @Override
    public ToolDefinition definition() {
        return new ToolDefinition(
                NAME,
                "Restart an approved systemd service.",
                Map.of("serviceName", "string: required, whitelisted service name"),
                RiskLevel.MEDIUM,
                false,
                true,
                PermissionRequirement.SERVICE_CONTROL,
                DEFAULT_TIMEOUT_MS,
                DEFAULT_OUTPUT_LIMIT_BYTES,
                true
        );
    }

    @Override
    public ToolExecutionResult execute(ToolCall call) {
        String serviceName = extractServiceName(call);
        if (serviceName == null) {
            return ToolExecutionResult.skipped(NAME, "serviceName argument is required and must match service name pattern");
        }
        if (isWindows()) {
            return ToolExecutionResult.skipped(NAME, "service restart is only available on systemd-based Linux targets in Phase 1");
        }
        ToolDefinition definition = definition();
        return commandRunner.run(NAME, CommandTemplateId.RESTART_SERVICE, Map.of("serviceName", serviceName), options(definition));
    }

    private String extractServiceName(ToolCall call) {
        Object rawName = call.arguments() == null ? null : call.arguments().get("serviceName");
        return ServiceNameValidator.extract(rawName).orElse(null);
    }
}
