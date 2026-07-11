package com.cup.opsagent.tool.builtin;

import com.cup.opsagent.executor.CommandExecutionOptions;
import com.cup.opsagent.executor.CommandRunner;
import com.cup.opsagent.tool.core.PermissionRequirement;
import com.cup.opsagent.tool.core.RiskLevel;
import com.cup.opsagent.tool.core.ToolDefinition;

import java.util.Map;
import java.util.Objects;

abstract class AbstractBuiltinTool {

    protected static final long DEFAULT_TIMEOUT_MS = 5000;
    protected static final int DEFAULT_OUTPUT_LIMIT_BYTES = 20_000;

    protected final CommandRunner commandRunner;

    protected AbstractBuiltinTool(CommandRunner commandRunner) {
        this.commandRunner = Objects.requireNonNull(commandRunner, "commandRunner must not be null");
    }

    protected ToolDefinition readOnlyDefinition(String name, String description, Map<String, String> inputSchema) {
        return new ToolDefinition(
                name,
                description,
                inputSchema,
                RiskLevel.LOW,
                true,
                false,
                PermissionRequirement.READ_ONLY_OS,
                DEFAULT_TIMEOUT_MS,
                DEFAULT_OUTPUT_LIMIT_BYTES,
                true
        );
    }

    protected CommandExecutionOptions options(ToolDefinition definition) {
        return new CommandExecutionOptions(definition.timeoutMs(), definition.outputLimitBytes());
    }

    protected boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase().contains("win");
    }
}
