package com.cup.opsagent.planner.llm;

import java.util.Map;

public record LlmPlanStepResponse(
        String stepId,
        String actionName,
        String toolName,
        Map<String, Object> arguments,
        String reason
) {
}
