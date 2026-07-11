package com.cup.opsagent.tool.core;

public interface OpsTool {

    ToolDefinition definition();

    ToolExecutionResult execute(ToolCall call);
}
