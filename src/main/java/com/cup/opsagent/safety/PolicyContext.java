package com.cup.opsagent.safety;

import com.cup.opsagent.tool.core.ToolCall;
import com.cup.opsagent.tool.core.ToolDefinition;

import java.util.Optional;

public record PolicyContext(
        String userInput,
        ToolCall toolCall,
        Optional<ToolDefinition> toolDefinition
) {
    public static PolicyContext of(String userInput, ToolCall toolCall, Optional<ToolDefinition> toolDefinition) {
        return new PolicyContext(userInput == null ? "" : userInput, toolCall, toolDefinition == null ? Optional.empty() : toolDefinition);
    }
}
