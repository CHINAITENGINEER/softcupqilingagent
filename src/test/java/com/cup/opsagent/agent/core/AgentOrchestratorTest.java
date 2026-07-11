package com.cup.opsagent.agent.core;

import com.cup.opsagent.agent.model.AgentRequest;
import com.cup.opsagent.agent.model.AgentResponse;
import com.cup.opsagent.agent.model.PlanStep;
import com.cup.opsagent.agent.model.TaskPlan;
import com.cup.opsagent.approval.ApprovalService;
import com.cup.opsagent.approval.InMemoryApprovalRepository;
import com.cup.opsagent.audit.AuditEventType;
import com.cup.opsagent.audit.AuditLogService;
import com.cup.opsagent.audit.AuditTrace;
import com.cup.opsagent.planner.TaskPlanner;
import com.cup.opsagent.planner.llm.LlmJsonTaskPlanner;
import com.cup.opsagent.planner.llm.LlmPlannerClient;
import com.cup.opsagent.planner.llm.LlmPlanValidationErrorCode;
import com.cup.opsagent.planner.llm.provider.LlmProviderErrorCode;
import com.cup.opsagent.planner.llm.provider.LlmProviderException;
import com.cup.opsagent.rag.AuditedRagAugmentor;
import com.cup.opsagent.rag.KnowledgeRetriever;
import com.cup.opsagent.rag.RagAugmentor;
import com.cup.opsagent.rag.RagContextFactory;
import com.cup.opsagent.rag.RagProperties;
import com.cup.opsagent.rag.RetrievedKnowledge;
import com.cup.opsagent.safety.PolicyEngine;
import com.cup.opsagent.safety.SafetyOrchestrator;
import com.cup.opsagent.safety.policy.ArgumentSchemaPolicy;
import com.cup.opsagent.safety.policy.DangerousIntentPolicy;
import com.cup.opsagent.safety.policy.RiskLevelPolicy;
import com.cup.opsagent.safety.policy.ToolEnabledPolicy;
import com.cup.opsagent.safety.policy.ToolExistencePolicy;
import com.cup.opsagent.tool.builtin.OpenPortsTool;
import com.cup.opsagent.tool.builtin.PortUsageTool;
import com.cup.opsagent.tool.builtin.RestartServiceTool;
import com.cup.opsagent.tool.builtin.ServiceStatusTool;
import com.cup.opsagent.tool.builtin.SystemLoadTool;
import com.cup.opsagent.tool.builtin.TopProcessesTool;
import com.cup.opsagent.tool.core.OpsTool;
import com.cup.opsagent.tool.core.PermissionRequirement;
import com.cup.opsagent.tool.core.RiskLevel;
import com.cup.opsagent.tool.core.ToolCall;
import com.cup.opsagent.tool.core.ToolDefinition;
import com.cup.opsagent.tool.core.ToolExecutionFailureCode;
import com.cup.opsagent.tool.core.ToolExecutionResult;
import com.cup.opsagent.tool.core.ToolRegistry;
import com.cup.opsagent.verifier.ExecutionVerifier;
import com.cup.opsagent.verifier.ReadOnlyToolVerifier;
import com.cup.opsagent.verifier.VerificationContext;
import com.cup.opsagent.verifier.VerificationOrchestrator;
import com.cup.opsagent.verifier.VerificationResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class AgentOrchestratorTest {

    @Test
    void shouldExecuteAllowedToolAndAttachVerificationResult() {
        AuditLogService auditLogService = new AuditLogService();
        AgentOrchestrator orchestrator = orchestrator(auditLogService, "load average: 0.10", systemLoadPlanner(), readOnlyVerificationOrchestrator());

        AgentResponse response = orchestrator.handle(new AgentRequest("check system load", "session-1", "user-1"));

        assertThat(response.status()).isEqualTo("SUCCESS");
        assertThat(response.steps()).hasSize(1);
        assertThat(response.steps().getFirst().executionResult().success()).isTrue();
        assertThat(response.steps().getFirst().verificationResult()).isNotNull();
        assertThat(response.steps().getFirst().verificationResult().verified()).isTrue();
        assertThat(response.answer()).contains("check system load");

        AuditTrace trace = auditLogService.findTrace(response.traceId()).orElseThrow();
        assertThat(trace.getStatus()).isEqualTo("SUCCESS");
        assertThat(trace.getEvents()).extracting(event -> event.eventType()).contains(
                AuditEventType.RECEIVE_REQUEST,
                AuditEventType.PLAN_GENERATED,
                AuditEventType.POLICY_CHECKED,
                AuditEventType.TOOL_EXECUTED,
                AuditEventType.VERIFICATION_STARTED,
                AuditEventType.VERIFICATION_PASSED,
                AuditEventType.FINAL_RESPONSE
        );
    }

    @Test
    void shouldAuditToolExecutionFailureCodeWhenPresent() {
        AuditLogService auditLogService = new AuditLogService();
        ToolExecutionResult failureResult = new ToolExecutionResult(
                SystemLoadTool.NAME,
                false,
                "",
                "denied shell wrapper command",
                null,
                5,
                Instant.now(),
                ToolExecutionFailureCode.SHELL_WRAPPER_DENIED
        );
        AgentOrchestrator orchestrator = orchestrator(auditLogService, failureResult, systemLoadPlanner(), readOnlyVerificationOrchestrator());

        AgentResponse response = orchestrator.handle(new AgentRequest("check system load", "session-1", "user-1"));

        AuditTrace trace = auditLogService.findTrace(response.traceId()).orElseThrow();
        AuditEventType toolExecuted = AuditEventType.TOOL_EXECUTED;
        Map<String, Object> payload = trace.getEvents().stream()
                .filter(event -> event.eventType() == toolExecuted)
                .findFirst()
                .orElseThrow()
                .payload();
        assertThat(payload).containsEntry("failureCode", ToolExecutionFailureCode.SHELL_WRAPPER_DENIED.name());
    }

    @Test
    void shouldAuditToolExecutionFailureCodeNoneWhenAbsent() {
        AuditLogService auditLogService = new AuditLogService();
        AgentOrchestrator orchestrator = orchestrator(auditLogService, "load average: 0.10", systemLoadPlanner(), readOnlyVerificationOrchestrator());

        AgentResponse response = orchestrator.handle(new AgentRequest("check system load", "session-1", "user-1"));

        AuditTrace trace = auditLogService.findTrace(response.traceId()).orElseThrow();
        Map<String, Object> payload = trace.getEvents().stream()
                .filter(event -> event.eventType() == AuditEventType.TOOL_EXECUTED)
                .findFirst()
                .orElseThrow()
                .payload();
        assertThat(payload).containsEntry("failureCode", "none");
    }

    @Test
    void shouldReturnVerificationFailedStatusWhenExecutionOutputIsEmpty() {
        AuditLogService auditLogService = new AuditLogService();
        AgentOrchestrator orchestrator = orchestrator(auditLogService, "", systemLoadPlanner(), readOnlyVerificationOrchestrator());

        AgentResponse response = orchestrator.handle(new AgentRequest("check system load", "session-1", "user-1"));

        assertThat(response.status()).isEqualTo("VERIFICATION_FAILED");
        assertThat(response.steps()).hasSize(1);
        assertThat(response.steps().getFirst().executionResult().success()).isTrue();
        assertThat(response.steps().getFirst().verificationResult()).isNotNull();
        assertThat(response.steps().getFirst().verificationResult().verified()).isFalse();
        assertThat(response.steps().getFirst().verificationResult().reason()).contains("empty output");
        assertThat(response.steps().getFirst().message()).isNotBlank();
        assertThat(response.answer()).contains("check system load");

        AuditTrace trace = auditLogService.findTrace(response.traceId()).orElseThrow();
        assertThat(trace.getStatus()).isEqualTo("VERIFICATION_FAILED");
        assertThat(trace.getEvents()).extracting(event -> event.eventType()).contains(
                AuditEventType.TOOL_EXECUTED,
                AuditEventType.VERIFICATION_STARTED,
                AuditEventType.VERIFICATION_FAILED,
                AuditEventType.FINAL_RESPONSE
        );
    }

    @Test
    void shouldConvertVerifierExceptionToVerificationFailedResult() {
        AuditLogService auditLogService = new AuditLogService();
        AgentOrchestrator orchestrator = orchestrator(auditLogService, "load average: 0.10", systemLoadPlanner(), throwingVerificationOrchestrator());

        AgentResponse response = orchestrator.handle(new AgentRequest("check system load", "session-1", "user-1"));

        assertThat(response.status()).isEqualTo("VERIFICATION_FAILED");
        assertThat(response.steps()).hasSize(1);
        VerificationResult verificationResult = response.steps().getFirst().verificationResult();
        assertThat(verificationResult.verified()).isFalse();
        assertThat(verificationResult.verifierName()).isEqualTo("VerificationException");
        assertThat(verificationResult.reason()).contains("verification failed with exception");

        AuditTrace trace = auditLogService.findTrace(response.traceId()).orElseThrow();
        assertThat(trace.getEvents()).extracting(event -> event.eventType()).contains(
                AuditEventType.TOOL_EXECUTED,
                AuditEventType.VERIFICATION_STARTED,
                AuditEventType.VERIFICATION_FAILED,
                AuditEventType.FINAL_RESPONSE
        );
    }
    @Test
    void shouldReturnPlanningFailedAndAuditSanitizedPlannerException() {
        AuditLogService auditLogService = new AuditLogService();
        TaskPlanner failingPlanner = userInput -> {
            throw new IllegalStateException("provider failed apiKey=sk-secret123456789 Authorization: Bearer sk-othersecret123456789");
        };
        AgentOrchestrator orchestrator = orchestrator(auditLogService, "load average: 0.10", failingPlanner, readOnlyVerificationOrchestrator());

        AgentResponse response = orchestrator.handle(new AgentRequest("check system load", "session-1", "user-1"));

        assertThat(response.status()).isEqualTo("PLANNING_FAILED");
        assertThat(response.steps()).isEmpty();
        assertThat(response.answer()).isNotBlank();
        assertThat(response.answer()).doesNotContain("sk-secret123456789");

        AuditTrace trace = auditLogService.findTrace(response.traceId()).orElseThrow();
        assertThat(trace.getStatus()).isEqualTo("PLANNING_FAILED");
        assertThat(trace.getEvents()).extracting(event -> event.eventType()).contains(
                AuditEventType.RECEIVE_REQUEST,
                AuditEventType.PLANNING_FAILED,
                AuditEventType.FINAL_RESPONSE
        );
        assertThat(trace.getEvents()).extracting(event -> event.eventType()).doesNotContain(AuditEventType.PLAN_GENERATED);
        Map<String, Object> payload = trace.getEvents().stream()
                .filter(event -> event.eventType() == AuditEventType.PLANNING_FAILED)
                .findFirst()
                .orElseThrow()
                .payload();
        assertThat(payload).containsEntry("errorType", "IllegalStateException");
        assertThat(payload.get("errorMessage")).asString().contains("[REDACTED]");
        assertThat(payload.get("errorMessage")).asString().doesNotContain("sk-secret123456789");
        assertThat(payload.get("errorMessage")).asString().doesNotContain("sk-othersecret123456789");
    }

    @Test
    void shouldAuditLlmPlanningFailedWithProviderCodeAndNoSensitivePayload() {
        AuditLogService auditLogService = new AuditLogService();
        TaskPlanner failingLlmPlanner = new LlmJsonTaskPlanner(
                userInput -> {
                    throw new LlmProviderException(
                            LlmProviderErrorCode.LLM_PROVIDER_UNAUTHORIZED,
                            "provider failed Authorization: Bearer sk-secret123456789 rawResponse={\"error\":\"denied\"}"
                    );
                },
                llmPlannerToolRegistry(),
                new ObjectMapper()
        );
        AgentOrchestrator orchestrator = orchestrator(auditLogService, "load average: 0.10", failingLlmPlanner, readOnlyVerificationOrchestrator());

        AgentResponse response = orchestrator.handle(new AgentRequest("check system load", "session-llm-fail", "user-1"));

        assertThat(response.status()).isEqualTo("PLANNING_FAILED");
        AuditTrace trace = auditLogService.findTrace(response.traceId()).orElseThrow();
        assertThat(trace.getEvents()).extracting(event -> event.eventType()).contains(
                AuditEventType.LLM_PLANNING_STARTED,
                AuditEventType.LLM_PLANNING_FAILED,
                AuditEventType.PLANNING_FAILED,
                AuditEventType.FINAL_RESPONSE
        );
        assertThat(trace.getEvents()).extracting(event -> event.eventType()).doesNotContain(AuditEventType.LLM_PLANNING_COMPLETED);
        Map<String, Object> payload = trace.getEvents().stream()
                .filter(event -> event.eventType() == AuditEventType.LLM_PLANNING_FAILED)
                .findFirst()
                .orElseThrow()
                .payload();
        assertThat(payload).containsEntry("providerErrorCode", LlmProviderErrorCode.LLM_PROVIDER_UNAUTHORIZED.name());
        assertThat(payload.get("errorMessage")).asString().contains("[REDACTED]");
        assertThat(payload.get("errorMessage")).asString().doesNotContain("sk-secret123456789");
        assertThat(payload).doesNotContainKeys("prompt", "rawResponse", "apiKey", "authorization");
    }
    @Test
    void shouldReturnPlanningFailedForLlmPlanValidationException() {
        AuditLogService auditLogService = new AuditLogService();
        TaskPlanner failingPlanner = new LlmJsonTaskPlanner(
                userInput -> "{\"intentType\":\"BAD\",\"summary\":\"unknown tool\",\"steps\":[{\"stepId\":\"step-1\",\"toolName\":\"rm_rf\",\"arguments\":{},\"reason\":\"bad\"}]}",
                llmPlannerToolRegistry(),
                new ObjectMapper()
        );
        AgentOrchestrator orchestrator = orchestrator(auditLogService, "load average: 0.10", failingPlanner, readOnlyVerificationOrchestrator());

        AgentResponse response = orchestrator.handle(new AgentRequest("unknown tool request", "session-1", "user-1"));

        assertThat(response.status()).isEqualTo("PLANNING_FAILED");
        AuditTrace trace = auditLogService.findTrace(response.traceId()).orElseThrow();
        assertThat(trace.getEvents()).extracting(event -> event.eventType()).contains(
                AuditEventType.LLM_PLANNING_STARTED,
                AuditEventType.LLM_PLAN_REJECTED,
                AuditEventType.PLANNING_FAILED,
                AuditEventType.FINAL_RESPONSE
        );
        assertThat(trace.getEvents()).extracting(event -> event.eventType()).doesNotContain(AuditEventType.LLM_PLANNING_COMPLETED);
        Map<String, Object> payload = trace.getEvents().stream()
                .filter(event -> event.eventType() == AuditEventType.LLM_PLAN_REJECTED)
                .findFirst()
                .orElseThrow()
                .payload();
        assertThat(payload).containsEntry("errorType", "LlmPlanValidationException");
        assertThat(payload).containsEntry("validationErrorCode", LlmPlanValidationErrorCode.UNKNOWN_TOOL.name());
        assertThat(payload.get("errorMessage")).asString().contains("unknown tool");
    }
    @Test
    void shouldWaitForApprovalAndSkipExecutionAndVerificationForRestartService() {
        AuditLogService auditLogService = new AuditLogService();
        AgentOrchestrator orchestrator = orchestrator(auditLogService, "load average: 0.10", restartServicePlanner(), readOnlyVerificationOrchestrator());

        AgentResponse response = orchestrator.handle(new AgentRequest("restart nginx", "session-1", "user-1"));

        assertThat(response.status()).isEqualTo("WAITING_APPROVAL");
        assertThat(response.steps()).hasSize(1);
        assertThat(response.steps().getFirst().executed()).isFalse();
        assertThat(response.steps().getFirst().verificationResult()).isNull();

        assertThat(response.answer()).contains("approvalId=");

        AuditTrace trace = auditLogService.findTrace(response.traceId()).orElseThrow();
        assertThat(trace.getStatus()).isEqualTo("WAITING_APPROVAL");
        assertThat(trace.getEvents()).extracting(event -> event.eventType()).contains(
                AuditEventType.POLICY_CHECKED,
                AuditEventType.APPROVAL_REQUIRED,
                AuditEventType.EXECUTION_SKIPPED,
                AuditEventType.FINAL_RESPONSE
        );
        Map<String, Object> approvalPayload = trace.getEvents().stream()
                .filter(event -> event.eventType() == AuditEventType.APPROVAL_REQUIRED)
                .findFirst()
                .orElseThrow()
                .payload();
        assertThat(approvalPayload).containsKeys("approvalId", "actionHash", "approvalExpiresAt");
        assertThat(approvalPayload).containsEntry("toolName", RestartServiceTool.NAME);
        assertThat(approvalPayload.get("approvalId")).asString().isNotBlank();
        assertThat(approvalPayload.get("actionHash")).asString().isNotBlank();

        Map<String, Object> skippedPayload = trace.getEvents().stream()
                .filter(event -> event.eventType() == AuditEventType.EXECUTION_SKIPPED)
                .findFirst()
                .orElseThrow()
                .payload();
        assertThat(skippedPayload).containsEntry("approvalId", approvalPayload.get("approvalId"));
        assertThat(trace.getEvents()).extracting(event -> event.eventType()).doesNotContain(
                AuditEventType.TOOL_EXECUTED,
                AuditEventType.VERIFICATION_STARTED,
                AuditEventType.VERIFICATION_PASSED,
                AuditEventType.VERIFICATION_FAILED
        );
    }

    @Test
    void shouldUseFakeLlmPlannerAndWaitForApprovalForRestartService() {
        AuditLogService auditLogService = new AuditLogService();
        TaskPlanner fakeLlmPlanner = new LlmJsonTaskPlanner(
                userInput -> "{\"intentType\":\"SERVICE_RESTART\",\"summary\":\"restart service\",\"suggestedRiskLevel\":\"LOW\",\"steps\":[{\"stepId\":\"step-1\",\"actionName\":\"restart-service\",\"toolName\":\"restart_service\",\"arguments\":{\"serviceName\":\"nginx\"},\"reason\":\"restart requested\"}]}",
                llmPlannerToolRegistry(),
                new ObjectMapper()
        );
        AgentOrchestrator orchestrator = orchestrator(auditLogService, "load average: 0.10", fakeLlmPlanner, readOnlyVerificationOrchestrator());

        AgentResponse response = orchestrator.handle(new AgentRequest("restart nginx", "session-llm-1", "user-llm-1"));

        assertThat(response.status()).isEqualTo("WAITING_APPROVAL");
        assertThat(response.steps()).hasSize(1);
        assertThat(response.steps().getFirst().toolName()).isEqualTo(RestartServiceTool.NAME);
        assertThat(response.steps().getFirst().executed()).isFalse();
        assertThat(response.answer()).contains("approvalId=");

        AuditTrace trace = auditLogService.findTrace(response.traceId()).orElseThrow();
        assertThat(trace.getStatus()).isEqualTo("WAITING_APPROVAL");
        Map<String, Object> planPayload = trace.getEvents().stream()
                .filter(event -> event.eventType() == AuditEventType.PLAN_GENERATED)
                .findFirst()
                .orElseThrow()
                .payload();
        assertThat(planPayload).containsEntry("intentType", "SERVICE_RESTART");
        assertThat(planPayload).containsEntry("riskLevel", RiskLevel.MEDIUM.name());

        Map<String, Object> approvalPayload = trace.getEvents().stream()
                .filter(event -> event.eventType() == AuditEventType.APPROVAL_REQUIRED)
                .findFirst()
                .orElseThrow()
                .payload();
        assertThat(approvalPayload).containsEntry("toolName", RestartServiceTool.NAME);
        assertThat(approvalPayload).containsEntry("requesterId", "user-llm-1");
        assertThat(approvalPayload.get("approvalId")).asString().isNotBlank();
        assertThat(approvalPayload.get("actionHash")).asString().isNotBlank();
    }

    @Test
    void shouldAuditRagRetrievalBeforeLlmPlanningWhenRagEnabled() {
        AuditLogService auditLogService = new AuditLogService();
        RagProperties ragProperties = new RagProperties();
        ragProperties.setEnabled(true);
        RecordingLlmPlannerClient llmClient = new RecordingLlmPlannerClient("""
                {
                  "intentType":"SYSTEM_HEALTH_CHECK",
                  "summary":"use retrieved runbook context",
                  "suggestedRiskLevel":"LOW",
                  "steps":[]
                }
                """);
        TaskPlanner ragEnabledLlmPlanner = new LlmJsonTaskPlanner(
                llmClient,
                llmPlannerToolRegistry(),
                new ObjectMapper(),
                ragProperties,
                auditedRagAugmentor(auditLogService, ragProperties)
        );
        AgentOrchestrator orchestrator = orchestrator(auditLogService, "load average: 0.10", ragEnabledLlmPlanner, readOnlyVerificationOrchestrator());

        AgentResponse response = orchestrator.handle(new AgentRequest("nginx 502", "session-rag-1", "user-rag-1"));

        assertThat(response.status()).isEqualTo("BLOCKED");
        assertThat(llmClient.ragContext())
                .contains("Retrieved Knowledge Context:")
                .contains("knowledgeId: runbook-nginx");
        AuditTrace trace = auditLogService.findTrace(response.traceId()).orElseThrow();
        assertThat(trace.getEvents()).extracting(event -> event.eventType()).containsSubsequence(
                AuditEventType.RECEIVE_REQUEST,
                AuditEventType.LLM_PLANNING_STARTED,
                AuditEventType.RAG_RETRIEVAL_STARTED,
                AuditEventType.RAG_RETRIEVAL_COMPLETED,
                AuditEventType.LLM_PLANNING_COMPLETED,
                AuditEventType.PLAN_GENERATED,
                AuditEventType.EXECUTION_BLOCKED,
                AuditEventType.FINAL_RESPONSE
        );
        Map<String, Object> startedPayload = trace.getEvents().stream()
                .filter(event -> event.eventType() == AuditEventType.RAG_RETRIEVAL_STARTED)
                .findFirst()
                .orElseThrow()
                .payload();
        assertThat(startedPayload).containsEntry("maxResults", ragProperties.getMaxResults());
        Map<String, Object> completedPayload = trace.getEvents().stream()
                .filter(event -> event.eventType() == AuditEventType.RAG_RETRIEVAL_COMPLETED)
                .findFirst()
                .orElseThrow()
                .payload();
        assertThat(completedPayload).containsEntry("resultCount", 1);
        assertThat(completedPayload.get("topKnowledgeIds")).isEqualTo(List.of("runbook-nginx"));
    }

    @Test
    void shouldFailClosedAndNotCallLlmWhenRagRetrievalFails() {
        AuditLogService auditLogService = new AuditLogService();
        RagProperties ragProperties = new RagProperties();
        ragProperties.setEnabled(true);
        RecordingLlmPlannerClient llmClient = new RecordingLlmPlannerClient("{}");
        TaskPlanner ragEnabledLlmPlanner = new LlmJsonTaskPlanner(
                llmClient,
                llmPlannerToolRegistry(),
                new ObjectMapper(),
                ragProperties,
                failingAuditedRagAugmentor(auditLogService, ragProperties)
        );
        AgentOrchestrator orchestrator = orchestrator(auditLogService, "load average: 0.10", ragEnabledLlmPlanner, readOnlyVerificationOrchestrator());

        AgentResponse response = orchestrator.handle(new AgentRequest("nginx 502", "session-rag-fail", "user-rag-1"));

        assertThat(response.status()).isEqualTo("PLANNING_FAILED");
        assertThat(response.steps()).isEmpty();
        assertThat(llmClient.calls()).isZero();
        assertThat(llmClient.ragContext()).isNull();
        AuditTrace trace = auditLogService.findTrace(response.traceId()).orElseThrow();
        assertThat(trace.getStatus()).isEqualTo("PLANNING_FAILED");
        assertThat(trace.getEvents()).extracting(event -> event.eventType()).containsSubsequence(
                AuditEventType.RECEIVE_REQUEST,
                AuditEventType.LLM_PLANNING_STARTED,
                AuditEventType.RAG_RETRIEVAL_STARTED,
                AuditEventType.RAG_RETRIEVAL_FAILED,
                AuditEventType.LLM_PLANNING_FAILED,
                AuditEventType.PLANNING_FAILED,
                AuditEventType.FINAL_RESPONSE
        );
        assertThat(trace.getEvents()).extracting(event -> event.eventType()).doesNotContain(
                AuditEventType.RAG_RETRIEVAL_COMPLETED,
                AuditEventType.LLM_PLANNING_COMPLETED,
                AuditEventType.PLAN_GENERATED
        );
        Map<String, Object> ragFailurePayload = trace.getEvents().stream()
                .filter(event -> event.eventType() == AuditEventType.RAG_RETRIEVAL_FAILED)
                .findFirst()
                .orElseThrow()
                .payload();
        assertThat(ragFailurePayload)
                .containsEntry("errorType", "IllegalStateException")
                .containsKey("queryHash")
                .containsKey("durationMs");
        Map<String, Object> planningFailurePayload = trace.getEvents().stream()
                .filter(event -> event.eventType() == AuditEventType.PLANNING_FAILED)
                .findFirst()
                .orElseThrow()
                .payload();
        assertThat(planningFailurePayload).containsEntry("errorType", "IllegalStateException");
    }

    private AgentOrchestrator orchestrator(AuditLogService auditLogService, String systemLoadStdout, TaskPlanner taskPlanner, VerificationOrchestrator verificationOrchestrator) {
        return orchestrator(
                auditLogService,
                new ToolExecutionResult(SystemLoadTool.NAME, true, systemLoadStdout, "", 0, 5, Instant.now()),
                taskPlanner,
                verificationOrchestrator
        );
    }

    private AgentOrchestrator orchestrator(AuditLogService auditLogService, ToolExecutionResult systemLoadResult, TaskPlanner taskPlanner, VerificationOrchestrator verificationOrchestrator) {
        ToolRegistry toolRegistry = new ToolRegistry(List.of(
                tool(SystemLoadTool.NAME, RiskLevel.LOW, true, false, systemLoadResult),
                tool(TopProcessesTool.NAME, RiskLevel.LOW, true, false, "PID COMMAND"),
                tool(OpenPortsTool.NAME, RiskLevel.LOW, true, false, "LISTEN 0.0.0.0:22"),
                tool(PortUsageTool.NAME, RiskLevel.LOW, true, false, "port 22 is used"),
                tool(ServiceStatusTool.NAME, RiskLevel.LOW, true, false, "Active: active"),
                tool(RestartServiceTool.NAME, RiskLevel.MEDIUM, false, true, "")
        ));
        PolicyEngine policyEngine = new PolicyEngine(List.of(
                new ToolExistencePolicy(),
                new ToolEnabledPolicy(),
                new DangerousIntentPolicy(),
                new ArgumentSchemaPolicy(),
                new RiskLevelPolicy()
        ));
        return new AgentOrchestrator(
                taskPlanner,
                new SafetyOrchestrator(toolRegistry, policyEngine),
                toolRegistry,
                auditLogService,
                verificationOrchestrator,
                new ApprovalService(new InMemoryApprovalRepository())
        );
    }

    private VerificationOrchestrator readOnlyVerificationOrchestrator() {
        return new VerificationOrchestrator(List.of(new ReadOnlyToolVerifier()));
    }

    private VerificationOrchestrator throwingVerificationOrchestrator() {
        return new VerificationOrchestrator(List.of(new ThrowingVerifier()));
    }

    private TaskPlanner systemLoadPlanner() {
        return userInput -> new TaskPlan(
                "SYSTEM_LOAD_CHECK",
                "check system load",
                RiskLevel.LOW,
                List.of(new PlanStep("step-1", "check_system_load", SystemLoadTool.NAME, Map.of(), "user requested system load"))
        );
    }

    private TaskPlanner restartServicePlanner() {
        return userInput -> new TaskPlan(
                "SERVICE_RESTART",
                "restart service",
                RiskLevel.MEDIUM,
                List.of(new PlanStep("step-1", "restart_service", RestartServiceTool.NAME, Map.of("serviceName", "nginx"), "user requested service restart"))
        );
    }

    private AuditedRagAugmentor auditedRagAugmentor(AuditLogService auditLogService, RagProperties ragProperties) {
        KnowledgeRetriever retriever = query -> List.of(new RetrievedKnowledge(
                "runbook-nginx",
                "runbook",
                "Nginx 502 troubleshooting",
                "Check nginx status and upstream health before restarting nginx.",
                0.91,
                Instant.parse("2026-07-01T00:00:00Z"),
                Map.of()
        ));
        RagAugmentor ragAugmentor = new RagAugmentor(retriever, new RagContextFactory(), ragProperties);
        return new AuditedRagAugmentor(ragAugmentor, auditLogService, ragProperties);
    }

    private AuditedRagAugmentor failingAuditedRagAugmentor(AuditLogService auditLogService, RagProperties ragProperties) {
        KnowledgeRetriever retriever = query -> {
            throw new IllegalStateException("retriever unavailable");
        };
        RagAugmentor ragAugmentor = new RagAugmentor(retriever, new RagContextFactory(), ragProperties);
        return new AuditedRagAugmentor(ragAugmentor, auditLogService, ragProperties);
    }

    private ToolRegistry llmPlannerToolRegistry() {
        return new ToolRegistry(List.of(
                tool(SystemLoadTool.NAME, Map.of(), RiskLevel.LOW, true, false, "load average: 0.10"),
                tool(TopProcessesTool.NAME, Map.of("limit", "integer: optional, 1-50"), RiskLevel.LOW, true, false, "PID COMMAND"),
                tool(OpenPortsTool.NAME, Map.of(), RiskLevel.LOW, true, false, "LISTEN 0.0.0.0:22"),
                tool(PortUsageTool.NAME, Map.of("port", "integer: required, 1-65535"), RiskLevel.LOW, true, false, "port 22 is used"),
                tool(ServiceStatusTool.NAME, Map.of("serviceName", "string: required, service name"), RiskLevel.LOW, true, false, "Active: active"),
                tool(RestartServiceTool.NAME, Map.of("serviceName", "string: required, whitelisted service name"), RiskLevel.MEDIUM, false, true, "")
        ));
    }

    private OpsTool tool(String name, Map<String, String> inputSchema, RiskLevel riskLevel, boolean readOnly, boolean requiresApproval, String stdout) {
        ToolDefinition definition = new ToolDefinition(
                name,
                name + " test tool",
                inputSchema,
                riskLevel,
                readOnly,
                requiresApproval,
                readOnly ? PermissionRequirement.READ_ONLY_OS : PermissionRequirement.SERVICE_CONTROL,
                1000,
                1024,
                true
        );
        return new StubOpsTool(definition, new ToolExecutionResult(name, true, stdout, "", 0, 5, Instant.now()));
    }

    private OpsTool tool(String name, RiskLevel riskLevel, boolean readOnly, boolean requiresApproval, String stdout) {
        return tool(name, riskLevel, readOnly, requiresApproval, new ToolExecutionResult(name, true, stdout, "", 0, 5, Instant.now()));
    }

    private OpsTool tool(String name, RiskLevel riskLevel, boolean readOnly, boolean requiresApproval, ToolExecutionResult result) {
        ToolDefinition definition = new ToolDefinition(
                name,
                name + " test tool",
                Map.of(),
                riskLevel,
                readOnly,
                requiresApproval,
                readOnly ? PermissionRequirement.READ_ONLY_OS : PermissionRequirement.SERVICE_CONTROL,
                1000,
                1024,
                true
        );
        return new StubOpsTool(definition, result);
    }

    private record StubOpsTool(ToolDefinition definition, ToolExecutionResult result) implements OpsTool {
        @Override
        public ToolExecutionResult execute(ToolCall call) {
            return result;
        }
    }

    private static class RecordingLlmPlannerClient implements LlmPlannerClient {
        private final String responseJson;
        private String ragContext;
        private int calls;

        RecordingLlmPlannerClient(String responseJson) {
            this.responseJson = responseJson;
        }

        @Override
        public String createPlanJson(String userInput) {
            this.calls++;
            this.ragContext = null;
            return responseJson;
        }

        @Override
        public String createPlanJson(String userInput, String ragContext) {
            this.calls++;
            this.ragContext = ragContext;
            return responseJson;
        }

        String ragContext() {
            return ragContext;
        }

        int calls() {
            return calls;
        }
    }

    private static class ThrowingVerifier implements ExecutionVerifier {
        @Override
        public String name() {
            return "ThrowingVerifier";
        }

        @Override
        public boolean supports(ToolCall toolCall, ToolDefinition toolDefinition) {
            return true;
        }

        @Override
        public VerificationResult verify(VerificationContext context) {
            throw new IllegalStateException("boom");
        }
    }
}

