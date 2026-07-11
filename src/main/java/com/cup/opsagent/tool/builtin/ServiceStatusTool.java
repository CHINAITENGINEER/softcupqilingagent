package com.cup.opsagent.tool.builtin;

import com.cup.opsagent.executor.CommandRunner;
import com.cup.opsagent.executor.CommandTemplateId;
import com.cup.opsagent.safety.validation.ServiceNameValidator;
import com.cup.opsagent.tool.core.OpsTool;
import com.cup.opsagent.tool.core.ToolCall;
import com.cup.opsagent.tool.core.ToolDefinition;
import com.cup.opsagent.tool.core.ToolExecutionResult;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class ServiceStatusTool extends AbstractBuiltinTool implements OpsTool {

    public static final String NAME = "get_service_status";

    public ServiceStatusTool(CommandRunner commandRunner) {
        super(commandRunner);
    }

    @Override
    public ToolDefinition definition() {
        return readOnlyDefinition(NAME, "Get status of a systemd service.", Map.of("serviceName", "string: required, service name"));
    }

    @Override
    public ToolExecutionResult execute(ToolCall call) {
        String serviceName = extractServiceName(call);
        if (serviceName == null) {
            return ToolExecutionResult.skipped(NAME, "serviceName argument is required and must match service name pattern");
        }
        if (isWindows()) {
            return ToolExecutionResult.skipped(NAME, "service status is only available on systemd-based Linux targets in Phase 1");
        }
        ToolDefinition definition = definition();
        return commandRunner.run(NAME, CommandTemplateId.GET_SERVICE_STATUS, Map.of("serviceName", serviceName), options(definition));
    }

    private String extractServiceName(ToolCall call) {
        Object rawName = call.arguments() == null ? null : call.arguments().get("serviceName");
        return ServiceNameValidator.extract(rawName).orElse(null);
    }
}
