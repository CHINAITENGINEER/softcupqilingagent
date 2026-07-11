package com.cup.opsagent.planner.llm;

import com.cup.opsagent.tool.core.ToolDefinition;
import com.cup.opsagent.tool.core.ToolRegistry;

import java.util.Map;

public class LlmPlanValidator {

    public static final int MAX_STEPS = 5;
    public static final int MAX_INTENT_TYPE_LENGTH = 80;
    public static final int MAX_SUMMARY_LENGTH = 500;
    public static final int MAX_STEP_ID_LENGTH = 80;
    public static final int MAX_ACTION_NAME_LENGTH = 120;
    public static final int MAX_REASON_LENGTH = 500;
    public static final int MAX_ARGUMENT_STRING_LENGTH = 500;

    private final ToolRegistry toolRegistry;

    public LlmPlanValidator(ToolRegistry toolRegistry) {
        this.toolRegistry = toolRegistry;
    }

    public void validate(LlmPlanResponse response) {
        if (response == null) {
            throw failure(LlmPlanValidationErrorCode.NULL_RESPONSE, "LLM planner response must not be null");
        }
        requireText(response.intentType(), "intentType");
        validateTextLength(response.intentType(), "intentType", MAX_INTENT_TYPE_LENGTH);
        requireText(response.summary(), "summary");
        validateTextLength(response.summary(), "summary", MAX_SUMMARY_LENGTH);
        validateOptionalTextLength(response.suggestedRiskLevel(), "suggestedRiskLevel", MAX_INTENT_TYPE_LENGTH);
        if (response.steps() != null && response.steps().size() > MAX_STEPS) {
            throw failure(LlmPlanValidationErrorCode.TOO_MANY_STEPS, "LLM planner returned too many steps: " + response.steps().size());
        }
        if (response.steps() != null) {
            response.steps().forEach(this::validateStep);
        }
    }

    public ToolDefinition validateStep(LlmPlanStepResponse step) {
        if (step == null) {
            throw failure(LlmPlanValidationErrorCode.NULL_STEP, "LLM planner step must not be null");
        }
        requireText(step.stepId(), "stepId");
        validateTextLength(step.stepId(), "stepId", MAX_STEP_ID_LENGTH);
        validateOptionalTextLength(step.actionName(), "actionName", MAX_ACTION_NAME_LENGTH);
        String toolName = requireText(step.toolName(), "toolName");
        validateTextLength(toolName, "toolName", MAX_ACTION_NAME_LENGTH);
        validateOptionalTextLength(step.reason(), "reason", MAX_REASON_LENGTH);
        ToolDefinition definition = toolRegistry.findDefinition(toolName)
                .orElseThrow(() -> failure(LlmPlanValidationErrorCode.UNKNOWN_TOOL, "LLM planner referenced unknown tool: " + toolName));
        validateArguments(definition, step.arguments() == null ? Map.of() : step.arguments());
        return definition;
    }

    public String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw failure(LlmPlanValidationErrorCode.REQUIRED_FIELD_MISSING, "LLM planner field is required: " + fieldName);
        }
        return value.trim();
    }

    private void validateArguments(ToolDefinition definition, Map<String, Object> arguments) {
        Map<String, String> schema = definition.inputSchema() == null ? Map.of() : definition.inputSchema();
        for (Map.Entry<String, Object> argument : arguments.entrySet()) {
            if (!schema.containsKey(argument.getKey())) {
                throw failure(
                        LlmPlanValidationErrorCode.UNSUPPORTED_ARGUMENT,
                        "LLM planner returned unsupported argument '" + argument.getKey() + "' for tool: " + definition.name()
                );
            }
            validateArgumentValueLength(argument.getKey(), argument.getValue());
        }
        schema.forEach((argumentName, rule) -> {
            if (rule != null && rule.toLowerCase().contains("required") && !arguments.containsKey(argumentName)) {
                throw failure(
                        LlmPlanValidationErrorCode.REQUIRED_ARGUMENT_MISSING,
                        "LLM planner missed required argument '" + argumentName + "' for tool: " + definition.name()
                );
            }
        });
    }

    private void validateArgumentValueLength(String argumentName, Object value) {
        if (value instanceof String text && text.length() > MAX_ARGUMENT_STRING_LENGTH) {
            throw failure(
                    LlmPlanValidationErrorCode.ARGUMENT_VALUE_TOO_LONG,
                    "LLM planner argument value is too long: " + argumentName
            );
        }
    }

    private void validateOptionalTextLength(String value, String fieldName, int maxLength) {
        if (value != null && !value.isBlank()) {
            validateTextLength(value, fieldName, maxLength);
        }
    }

    private void validateTextLength(String value, String fieldName, int maxLength) {
        if (value != null && value.length() > maxLength) {
            throw failure(
                    LlmPlanValidationErrorCode.FIELD_TOO_LONG,
                    "LLM planner field is too long: " + fieldName
            );
        }
    }

    private LlmPlanValidationException failure(LlmPlanValidationErrorCode code, String message) {
        return new LlmPlanValidationException(code, message);
    }
}
