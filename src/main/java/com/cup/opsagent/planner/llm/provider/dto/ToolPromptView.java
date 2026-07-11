package com.cup.opsagent.planner.llm.provider.dto;

import java.util.Map;

public record ToolPromptView(
        String name,
        String description,
        Map<String, String> inputSchema,
        boolean readOnly,
        boolean requiresApproval
) {
}
