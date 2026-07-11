package com.cup.opsagent.planner.llm;

import java.util.List;

public record LlmPlanResponse(
        String intentType,
        String summary,
        String suggestedRiskLevel,
        List<LlmPlanStepResponse> steps
) {
}
