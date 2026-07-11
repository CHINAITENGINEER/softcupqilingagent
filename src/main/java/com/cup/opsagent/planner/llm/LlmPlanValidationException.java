package com.cup.opsagent.planner.llm;

public class LlmPlanValidationException extends IllegalArgumentException {

    private final LlmPlanValidationErrorCode code;

    public LlmPlanValidationException(LlmPlanValidationErrorCode code, String message) {
        super(message);
        this.code = code;
    }

    public LlmPlanValidationErrorCode code() {
        return code;
    }
}
