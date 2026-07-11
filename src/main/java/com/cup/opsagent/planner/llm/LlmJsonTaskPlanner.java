package com.cup.opsagent.planner.llm;

import com.cup.opsagent.agent.model.PlanStep;
import com.cup.opsagent.agent.model.TaskPlan;
import com.cup.opsagent.planner.TraceAwareTaskPlanner;
import com.cup.opsagent.rag.AuditedRagAugmentor;
import com.cup.opsagent.rag.RagAugmentationResult;
import com.cup.opsagent.rag.RagProperties;
import com.cup.opsagent.tool.core.RiskLevel;
import com.cup.opsagent.tool.core.ToolRegistry;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Map;

public class LlmJsonTaskPlanner implements TraceAwareTaskPlanner {

    public static final int MAX_RAW_JSON_LENGTH = 16_384;

    private final LlmPlannerClient llmPlannerClient;
    private final ToolRegistry toolRegistry;
    private final LlmPlanValidator validator;
    private final ObjectMapper strictObjectMapper;
    private final RagProperties ragProperties;
    private final AuditedRagAugmentor auditedRagAugmentor;

    public LlmJsonTaskPlanner(LlmPlannerClient llmPlannerClient, ToolRegistry toolRegistry, ObjectMapper objectMapper) {
        this(llmPlannerClient, toolRegistry, objectMapper, new RagProperties(), null);
    }

    public LlmJsonTaskPlanner(
            LlmPlannerClient llmPlannerClient,
            ToolRegistry toolRegistry,
            ObjectMapper objectMapper,
            RagProperties ragProperties,
            AuditedRagAugmentor auditedRagAugmentor
    ) {
        this.llmPlannerClient = llmPlannerClient;
        this.toolRegistry = toolRegistry;
        this.validator = new LlmPlanValidator(toolRegistry);
        this.strictObjectMapper = objectMapper.copy()
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, true);
        this.ragProperties = ragProperties == null ? new RagProperties() : ragProperties;
        this.auditedRagAugmentor = auditedRagAugmentor;
    }

    @Override
    public TaskPlan plan(String userInput) {
        return planInternal(userInput, null);
    }

    @Override
    public TaskPlan plan(String traceId, String userInput) {
        return planInternal(userInput, traceId);
    }

    private TaskPlan planInternal(String userInput, String traceId) {
        LlmPlanResponse response = parse(createPlanJson(userInput, traceId));
        validator.validate(response);
        if (response.steps() == null || response.steps().isEmpty()) {
            return new TaskPlan(
                    validator.requireText(response.intentType(), "intentType"),
                    validator.requireText(response.summary(), "summary"),
                    parseSuggestedRisk(response.suggestedRiskLevel()),
                    List.of()
            );
        }
        List<PlanStep> steps = response.steps().stream()
                .map(this::toPlanStep)
                .toList();
        return new TaskPlan(
                validator.requireText(response.intentType(), "intentType"),
                validator.requireText(response.summary(), "summary"),
                highestToolRisk(steps),
                steps
        );
    }

    private String createPlanJson(String userInput, String traceId) {
        if (!shouldUseRag(traceId)) {
            return llmPlannerClient.createPlanJson(userInput);
        }
        RagAugmentationResult ragResult = auditedRagAugmentor.augment(traceId, userInput);
        return llmPlannerClient.createPlanJson(userInput, ragResult.context());
    }

    private boolean shouldUseRag(String traceId) {
        return ragProperties.isEnabled() && auditedRagAugmentor != null && traceId != null && !traceId.isBlank();
    }

    private LlmPlanResponse parse(String json) {
        if (json == null || json.isBlank()) {
            throw new IllegalArgumentException("LLM planner returned blank JSON");
        }
        if (json.length() > MAX_RAW_JSON_LENGTH) {
            throw new LlmPlanValidationException(
                    LlmPlanValidationErrorCode.RESPONSE_TOO_LARGE,
                    "LLM planner returned too large JSON response"
            );
        }
        try {
            return strictObjectMapper.readValue(json, LlmPlanResponse.class);
        } catch (Exception exception) {
            throw new IllegalArgumentException("LLM planner returned invalid plan JSON", exception);
        }
    }

    private PlanStep toPlanStep(LlmPlanStepResponse step) {
        validator.validateStep(step);
        String toolName = validator.requireText(step.toolName(), "toolName");
        return new PlanStep(
                validator.requireText(step.stepId(), "stepId"),
                blankToDefault(step.actionName(), toolName),
                toolName,
                step.arguments() == null ? Map.of() : Map.copyOf(step.arguments()),
                blankToDefault(step.reason(), "LLM selected tool " + toolName)
        );
    }

    private RiskLevel highestToolRisk(List<PlanStep> steps) {
        return steps.stream()
                .map(step -> toolRegistry.findDefinition(step.toolName()).orElseThrow().riskLevel())
                .max(this::compareRisk)
                .orElse(RiskLevel.HIGH);
    }

    private int compareRisk(RiskLevel left, RiskLevel right) {
        return Integer.compare(left.ordinal(), right.ordinal());
    }

    private RiskLevel parseSuggestedRisk(String riskLevel) {
        if (riskLevel == null || riskLevel.isBlank()) {
            return RiskLevel.HIGH;
        }
        try {
            return RiskLevel.valueOf(riskLevel.trim().toUpperCase());
        } catch (IllegalArgumentException exception) {
            return RiskLevel.HIGH;
        }
    }

    private String blankToDefault(String value, String defaultValue) {
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        return value.trim();
    }
}
