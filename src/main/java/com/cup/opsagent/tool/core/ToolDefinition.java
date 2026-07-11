package com.cup.opsagent.tool.core;

import java.util.Map;

public record ToolDefinition(
        String name,
        String description,
        Map<String, String> inputSchema,
        RiskLevel riskLevel,
        boolean readOnly,
        boolean requiresApproval,
        PermissionRequirement permissionRequirement,
        long timeoutMs,
        int outputLimitBytes,
        boolean enabled
) {
}
