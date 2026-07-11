package com.cup.opsagent.agent.model;

import com.cup.opsagent.tool.core.RiskLevel;

import java.util.List;

public record TaskPlan(
        String intentType,
        String summary,
        RiskLevel riskLevel,
        List<PlanStep> steps
) {
    public boolean isEmpty() {
        return steps == null || steps.isEmpty();
    }
}
