package com.cup.opsagent.tool.builtin;

import com.cup.opsagent.executor.CommandRunner;
import com.cup.opsagent.executor.CommandTemplateId;
import com.cup.opsagent.tool.core.OpsTool;
import com.cup.opsagent.tool.core.ToolCall;
import com.cup.opsagent.tool.core.ToolDefinition;
import com.cup.opsagent.tool.core.ToolExecutionResult;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class OpenPortsTool extends AbstractBuiltinTool implements OpsTool {

    public static final String NAME = "get_open_ports";

    public OpenPortsTool(CommandRunner commandRunner) {
        super(commandRunner);
    }

    @Override
    public ToolDefinition definition() {
        return readOnlyDefinition(NAME, "List open listening ports.", Map.of());
    }

    @Override
    public ToolExecutionResult execute(ToolCall call) {
        if (isWindows()) {
            return ToolExecutionResult.skipped(NAME, "get_open_ports requires a Linux/Kylin runtime in Phase 1");
        }
        ToolDefinition definition = definition();
        return commandRunner.run(NAME, CommandTemplateId.GET_OPEN_PORTS, Map.of(), options(definition));
    }
}
