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
public class SystemLoadTool extends AbstractBuiltinTool implements OpsTool {

    public static final String NAME = "get_system_load";

    public SystemLoadTool(CommandRunner commandRunner) {
        super(commandRunner);
    }

    @Override
    public ToolDefinition definition() {
        return readOnlyDefinition(NAME, "Get system load and basic resource snapshot.", Map.of());
    }

    @Override
    public ToolExecutionResult execute(ToolCall call) {
        if (isWindows()) {
            return ToolExecutionResult.skipped(NAME, "get_system_load requires a Linux/Kylin runtime in Phase 1");
        }
        ToolDefinition definition = definition();
        return commandRunner.run(NAME, CommandTemplateId.GET_SYSTEM_LOAD, Map.of(), options(definition));
    }
}
