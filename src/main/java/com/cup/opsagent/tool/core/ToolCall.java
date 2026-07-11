package com.cup.opsagent.tool.core;

import java.util.Map;

public record ToolCall(
        String toolName,
        Map<String, Object> arguments,
        String traceId,
        String stepId
) {
}
