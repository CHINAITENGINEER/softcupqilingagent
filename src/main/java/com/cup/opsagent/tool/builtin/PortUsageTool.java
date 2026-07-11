package com.cup.opsagent.tool.builtin;

import com.cup.opsagent.executor.CommandRunner;
import com.cup.opsagent.executor.CommandTemplateId;
import com.cup.opsagent.safety.validation.NumericArgumentValidator;
import com.cup.opsagent.tool.core.OpsTool;
import com.cup.opsagent.tool.core.ToolCall;
import com.cup.opsagent.tool.core.ToolDefinition;
import com.cup.opsagent.tool.core.ToolExecutionResult;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class PortUsageTool extends AbstractBuiltinTool implements OpsTool {

    public static final String NAME = "check_port_usage";

    public PortUsageTool(CommandRunner commandRunner) {
        super(commandRunner);
    }

    @Override
    public ToolDefinition definition() {
        return readOnlyDefinition(NAME, "Check which process is using a specific port.", Map.of("port", "integer: required, 1-65535"));
    }

    @Override
    public ToolExecutionResult execute(ToolCall call) {
        Integer port = extractPort(call);
        if (port == null) {
            return ToolExecutionResult.skipped(NAME, "port argument is required and must be an integer");
        }
        if (isWindows()) {
            return ToolExecutionResult.skipped(NAME, "check_port_usage requires a Linux/Kylin runtime in Phase 1");
        }
        ToolDefinition definition = definition();
        return commandRunner.run(NAME, CommandTemplateId.CHECK_PORT_USAGE, Map.of("port", port), options(definition));
    }

    private Integer extractPort(ToolCall call) {
        Object rawPort = call.arguments() == null ? null : call.arguments().get("port");
        return NumericArgumentValidator.integerInRange(rawPort, 1, 65535).orElse(null);
    }
}
