package com.cup.opsagent.agent.core;

import com.cup.opsagent.agent.model.AgentRequest;
import com.cup.opsagent.agent.model.AgentResponse;
import com.cup.opsagent.agent.model.PlanStep;
import com.cup.opsagent.agent.model.StepResult;
import com.cup.opsagent.agent.model.TaskPlan;
import com.cup.opsagent.approval.ApprovalRecord;
import com.cup.opsagent.approval.ApprovalService;
import com.cup.opsagent.audit.AuditEvent;
import com.cup.opsagent.audit.AuditEventType;
import com.cup.opsagent.audit.AuditLogService;
import com.cup.opsagent.audit.AuditTrace;
import com.cup.opsagent.planner.TaskPlanner;
import com.cup.opsagent.planner.TraceAwareTaskPlanner;
import com.cup.opsagent.planner.llm.LlmJsonTaskPlanner;
import com.cup.opsagent.planner.llm.LlmPlanValidationException;
import com.cup.opsagent.planner.llm.provider.LlmProviderException;
import com.cup.opsagent.planner.llm.provider.LlmSafeLogSanitizer;
import com.cup.opsagent.safety.PolicyDecision;
import com.cup.opsagent.safety.PolicyDecisionType;
import com.cup.opsagent.safety.SafetyOrchestrator;
import com.cup.opsagent.tool.core.OpsTool;
import com.cup.opsagent.tool.core.ToolCall;
import com.cup.opsagent.tool.core.ToolExecutionResult;
import com.cup.opsagent.tool.core.ToolRegistry;
import com.cup.opsagent.verifier.VerificationContext;
import com.cup.opsagent.verifier.VerificationOrchestrator;
import com.cup.opsagent.verifier.VerificationResult;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class AgentOrchestrator {

    private final TaskPlanner taskPlanner;
    private final SafetyOrchestrator safetyOrchestrator;
    private final ToolRegistry toolRegistry;
    private final AuditLogService auditLogService;
    private final VerificationOrchestrator verificationOrchestrator;
    private final ApprovalService approvalService;

    public AgentOrchestrator(
            TaskPlanner taskPlanner,
            SafetyOrchestrator safetyOrchestrator,
            ToolRegistry toolRegistry,
            AuditLogService auditLogService,
            VerificationOrchestrator verificationOrchestrator,
            ApprovalService approvalService
    ) {
        this.taskPlanner = taskPlanner;
        this.safetyOrchestrator = safetyOrchestrator;
        this.toolRegistry = toolRegistry;
        this.auditLogService = auditLogService;
        this.verificationOrchestrator = verificationOrchestrator;
        this.approvalService = approvalService;
    }

    public AgentResponse handle(AgentRequest request) {
        Instant startedAt = Instant.now();
        AuditTrace trace = auditLogService.startTrace(request.message());
        String traceId = trace.getTraceId();

        TaskPlan plan;
        boolean llmPlanning = taskPlanner instanceof LlmJsonTaskPlanner;
        if (llmPlanning) {
            auditLogService.append(traceId, AuditEvent.success(traceId, AuditEventType.LLM_PLANNING_STARTED, payload(
                    "planner", taskPlanner.getClass().getSimpleName()
            )));
        }
        try {
            plan = taskPlanner instanceof TraceAwareTaskPlanner traceAwareTaskPlanner
                    ? traceAwareTaskPlanner.plan(traceId, request.message())
                    : taskPlanner.plan(request.message());
        } catch (RuntimeException exception) {
            String sanitizedError = LlmSafeLogSanitizer.sanitizeForException(exception.getMessage());
            String answer = "任务规划失败，请检查请求或稍后重试。";
            if (llmPlanning) {
                auditLogService.append(traceId, AuditEvent.failure(traceId, llmFailureEventType(exception), llmFailurePayload(exception, sanitizedError), sanitizedError));
            }
            auditLogService.append(traceId, AuditEvent.failure(traceId, AuditEventType.PLANNING_FAILED, payload(
                    "errorType", exception.getClass().getSimpleName(),
                    "errorMessage", sanitizedError
            ), sanitizedError));
            auditLogService.finish(traceId, "PLANNING_FAILED", answer);
            return new AgentResponse(traceId, "PLANNING_FAILED", answer, List.of(), startedAt, Instant.now());
        }
        if (llmPlanning) {
            auditLogService.append(traceId, AuditEvent.success(traceId, AuditEventType.LLM_PLANNING_COMPLETED, payload(
                    "intentType", plan.intentType(),
                    "riskLevel", planRiskLevelName(plan),
                    "stepCount", plan.steps() == null ? 0 : plan.steps().size()
            )));
        }
        auditLogService.append(traceId, AuditEvent.success(traceId, AuditEventType.PLAN_GENERATED, payload(
                "intentType", plan.intentType(),
                "summary", plan.summary(),
                "riskLevel", planRiskLevelName(plan),
                "stepCount", plan.steps() == null ? 0 : plan.steps().size()
        )));

        List<StepResult> stepResults = new ArrayList<>();
        if (plan.isEmpty()) {
            String answer = "请求未生成可执行工具调用：" + plan.summary();
            auditLogService.append(traceId, AuditEvent.failure(traceId, AuditEventType.EXECUTION_BLOCKED, payload(
                    "intentType", plan.intentType(),
                    "riskLevel", planRiskLevelName(plan)
            ), answer));
            auditLogService.finish(traceId, "BLOCKED", answer);
            return new AgentResponse(traceId, "BLOCKED", answer, stepResults, startedAt, Instant.now());
        }

        for (PlanStep step : plan.steps()) {
            ToolCall toolCall = new ToolCall(step.toolName(), step.arguments(), traceId, step.stepId());
            auditLogService.append(traceId, AuditEvent.success(traceId, AuditEventType.TOOLCALL_PARSED, payload(
                    "stepId", step.stepId(),
                    "toolName", step.toolName(),
                    "arguments", step.arguments(),
                    "reason", step.reason()
            )));

            PolicyDecision decision = safetyOrchestrator.check(request.message(), toolCall);
            auditLogService.append(traceId, AuditEvent.success(traceId, AuditEventType.POLICY_CHECKED, payload(
                    "stepId", step.stepId(),
                    "toolName", step.toolName(),
                    "decision", decision.type().name(),
                    "riskLevel", decision.riskLevel().name(),
                    "reason", decision.reason(),
                    "violatedPolicies", decision.violatedPolicies(),
                    "suggestions", decision.suggestions()
            )));

            if (decision.type() == PolicyDecisionType.BLOCK) {
                ToolExecutionResult skipped = ToolExecutionResult.skipped(step.toolName(), decision.reason());
                stepResults.add(new StepResult(step.stepId(), step.toolName(), decision.type().name(), false, skipped, null, decision.reason()));
                auditLogService.append(traceId, AuditEvent.failure(traceId, AuditEventType.EXECUTION_BLOCKED, payload(
                        "stepId", step.stepId(),
                        "toolName", step.toolName(),
                        "decision", decision.type().name()
                ), decision.reason()));
                String answer = "安全策略已阻断请求：" + decision.reason();
                auditLogService.finish(traceId, "BLOCKED", answer);
                return new AgentResponse(traceId, "BLOCKED", answer, stepResults, startedAt, Instant.now());
            }

            if (decision.type() == PolicyDecisionType.WAITING_APPROVAL) {
                ApprovalRecord approvalRecord = approvalService.requestApproval(
                        traceId,
                        step.stepId(),
                        request.userId(),
                        step.toolName(),
                        step.arguments(),
                        decision.riskLevel(),
                        decision.reason()
                );
                ToolExecutionResult skipped = ToolExecutionResult.skipped(step.toolName(), decision.reason());
                stepResults.add(new StepResult(step.stepId(), step.toolName(), decision.type().name(), false, skipped, null, decision.reason()));
                auditLogService.append(traceId, AuditEvent.success(traceId, AuditEventType.APPROVAL_REQUIRED, payload(
                        "stepId", step.stepId(),
                        "toolName", step.toolName(),
                        "decision", decision.type().name(),
                        "riskLevel", decision.riskLevel().name(),
                        "reason", decision.reason(),
                        "requesterId", approvalRecord.requesterId(),
                        "approvalId", approvalRecord.approvalId(),
                        "actionHash", approvalRecord.actionHash(),
                        "approvalExpiresAt", approvalRecord.expiresAt().toString(),
                        "shouldExecute", false
                )));
                auditLogService.append(traceId, AuditEvent.success(traceId, AuditEventType.EXECUTION_SKIPPED, payload(
                        "stepId", step.stepId(),
                        "toolName", step.toolName(),
                        "approvalId", approvalRecord.approvalId(),
                        "reason", "waiting for human approval"
                )));
                String answer = "该操作需要人工确认，approvalId=" + approvalRecord.approvalId() + "：" + decision.reason();
                auditLogService.finish(traceId, "WAITING_APPROVAL", answer);
                return new AgentResponse(traceId, "WAITING_APPROVAL", answer, stepResults, startedAt, Instant.now());
            }

            auditLogService.append(traceId, AuditEvent.success(traceId, AuditEventType.TOOL_VALIDATED, payload(
                    "stepId", step.stepId(),
                    "toolName", step.toolName()
            )));
            OpsTool tool = toolRegistry.findTool(step.toolName()).orElseThrow();
            auditLogService.append(traceId, AuditEvent.success(traceId, AuditEventType.EXECUTION_STARTED, payload(
                    "stepId", step.stepId(),
                    "toolName", step.toolName()
            )));
            ToolExecutionResult executionResult;
            try {
                executionResult = tool.execute(toolCall);
            } catch (RuntimeException exception) {
                executionResult = new ToolExecutionResult(
                        step.toolName(),
                        false,
                        "",
                        "tool execution failed: " + exception.getMessage(),
                        null,
                        0,
                        Instant.now()
                );
            }
            auditLogService.append(traceId, AuditEvent.success(traceId, AuditEventType.TOOL_EXECUTED, payload(
                    "stepId", step.stepId(),
                    "toolName", step.toolName(),
                    "success", executionResult.success(),
                    "exitCode", executionResult.exitCode() == null ? "none" : executionResult.exitCode(),
                    "failureCode", executionResult.failureCode() == null ? "none" : executionResult.failureCode().name(),
                    "durationMs", executionResult.durationMs()
            )));

            auditLogService.append(traceId, AuditEvent.success(traceId, AuditEventType.VERIFICATION_STARTED, payload(
                    "stepId", step.stepId(),
                    "toolName", step.toolName()
            )));
            VerificationResult verificationResult;
            try {
                verificationResult = verificationOrchestrator.verify(new VerificationContext(
                        request.message(),
                        traceId,
                        toolCall,
                        tool.definition(),
                        executionResult
                ));
            } catch (RuntimeException exception) {
                verificationResult = VerificationResult.failed(
                        "VerificationException",
                        "verification failed with exception: " + exception.getMessage(),
                        payload("toolName", step.toolName()),
                        List.of("检查 verifier 实现是否处理了该工具的执行结果")
                );
            }
            auditLogService.append(traceId, AuditEvent.success(
                    traceId,
                    verificationResult.verified() ? AuditEventType.VERIFICATION_PASSED : AuditEventType.VERIFICATION_FAILED,
                    payload(
                            "stepId", step.stepId(),
                            "toolName", step.toolName(),
                            "verifierName", verificationResult.verifierName(),
                            "verified", verificationResult.verified(),
                            "reason", verificationResult.reason(),
                            "evidence", verificationResult.evidence(),
                            "suggestedRecovery", verificationResult.suggestedRecovery()
                    )
            ));
            stepResults.add(new StepResult(
                    step.stepId(),
                    step.toolName(),
                    decision.type().name(),
                    true,
                    executionResult,
                    verificationResult,
                    verificationResult.verified() ? "执行并验证成功" : "执行完成但验证失败"
            ));
        }

        String answer = summarize(plan, stepResults);
        String finalStatus = hasVerificationFailure(stepResults) ? "VERIFICATION_FAILED" : "SUCCESS";
        auditLogService.finish(traceId, finalStatus, answer);
        return new AgentResponse(traceId, finalStatus, answer, stepResults, startedAt, Instant.now());
    }

    private boolean hasVerificationFailure(List<StepResult> stepResults) {
        return stepResults.stream()
                .anyMatch(result -> result.verificationResult() != null && !result.verificationResult().verified());
    }

    private Map<String, Object> payload(Object... entries) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (int index = 0; index + 1 < entries.length; index += 2) {
            Object key = entries[index];
            if (key != null) {
                result.put(String.valueOf(key), entries[index + 1] == null ? "" : entries[index + 1]);
            }
        }
        return Map.copyOf(result);
    }

    private AuditEventType llmFailureEventType(RuntimeException exception) {
        return exception instanceof LlmPlanValidationException ? AuditEventType.LLM_PLAN_REJECTED : AuditEventType.LLM_PLANNING_FAILED;
    }

    private Map<String, Object> llmFailurePayload(RuntimeException exception, String sanitizedError) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("errorType", exception.getClass().getSimpleName());
        result.put("errorMessage", sanitizedError);
        if (exception instanceof LlmProviderException providerException) {
            result.put("providerErrorCode", providerException.code().name());
        }
        if (exception instanceof LlmPlanValidationException validationException) {
            result.put("validationErrorCode", validationException.code().name());
        }
        return Map.copyOf(result);
    }

    private String planRiskLevelName(TaskPlan plan) {
        return plan.riskLevel() == null ? "UNKNOWN" : plan.riskLevel().name();
    }

    private String summarize(TaskPlan plan, List<StepResult> stepResults) {
        long successCount = stepResults.stream().filter(result -> result.executionResult() != null && result.executionResult().success()).count();
        long verifiedCount = stepResults.stream().filter(result -> result.verificationResult() != null && result.verificationResult().verified()).count();
        return plan.summary() + "。已执行 " + stepResults.size() + " 个步骤，其中 " + successCount + " 个执行成功，" + verifiedCount + " 个验证通过。";
    }
}
