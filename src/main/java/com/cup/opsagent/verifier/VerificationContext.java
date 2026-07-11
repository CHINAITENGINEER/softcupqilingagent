package com.cup.opsagent.verifier;

import com.cup.opsagent.tool.core.ToolCall;
import com.cup.opsagent.tool.core.ToolDefinition;
import com.cup.opsagent.tool.core.ToolExecutionResult;

public record VerificationContext(
        String userInput,
        String traceId,
        ToolCall toolCall,
        ToolDefinition toolDefinition,
        ToolExecutionResult executionResult
) {
}
