package com.cup.opsagent.agent.model;

import java.util.Map;

public record PlanStep(
        String stepId,
        String actionName,
        String toolName,
        Map<String, Object> arguments,
        String reason
) {
}
