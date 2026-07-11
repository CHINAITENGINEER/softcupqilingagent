package com.cup.opsagent.planner.llm;

public enum PlannerMode {
    RULE,
    FAKE_LLM,
    LLM;

    public static PlannerMode from(String value) {
        if (value == null || value.isBlank()) {
            return RULE;
        }
        return PlannerMode.valueOf(value.trim().replace('-', '_').toUpperCase());
    }
}
