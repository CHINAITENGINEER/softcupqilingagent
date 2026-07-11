package com.cup.opsagent.planner.llm;

import com.cup.opsagent.agent.model.TaskPlan;
import com.cup.opsagent.audit.AuditLogService;
import com.cup.opsagent.rag.AuditedRagAugmentor;
import com.cup.opsagent.rag.KnowledgeQuery;
import com.cup.opsagent.rag.KnowledgeRetriever;
import com.cup.opsagent.rag.RagAugmentor;
import com.cup.opsagent.rag.RagContextFactory;
import com.cup.opsagent.rag.RagProperties;
import com.cup.opsagent.rag.RetrievedKnowledge;
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
import com.cup.opsagent.tool.core.ToolExecutionResult;
import com.cup.opsagent.tool.core.ToolRegistry;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LlmJsonTaskPlannerTest {

    @Test
    void shouldConvertFakeLlmRestartPlanToTaskPlanAndUseToolRisk() {
        LlmJsonTaskPlanner planner = new LlmJsonTaskPlanner(
                new FakeLlmPlannerClient(new ObjectMapper()),
                toolRegistry(),
                new ObjectMapper()
        );

        TaskPlan plan = planner.plan("帮我重启 nginx");

        assertThat(plan.intentType()).isEqualTo("SERVICE_RESTART");
        assertThat(plan.riskLevel()).isEqualTo(RiskLevel.MEDIUM);
        assertThat(plan.steps()).hasSize(1);
        assertThat(plan.steps().getFirst().toolName()).isEqualTo(RestartServiceTool.NAME);
        assertThat(plan.steps().getFirst().arguments()).containsEntry("serviceName", "nginx");
    }

    @Test
    void shouldRejectUnknownToolFromLlmOutput() {
        LlmJsonTaskPlanner planner = new LlmJsonTaskPlanner(
                ignored -> "{\"intentType\":\"BAD\",\"summary\":\"bad tool\",\"steps\":[{\"stepId\":\"step-1\",\"toolName\":\"delete_all_logs\",\"arguments\":{},\"reason\":\"bad\"}]}",
                toolRegistry(),
                new ObjectMapper()
        );

        assertThatThrownBy(() -> planner.plan("清理日志"))
                .isInstanceOfSatisfying(LlmPlanValidationException.class, exception ->
                        assertThat(exception.code()).isEqualTo(LlmPlanValidationErrorCode.UNKNOWN_TOOL)
                )
                .hasMessageContaining("unknown tool");
    }

    @Test
    void shouldRejectUnknownJsonFieldFromLlmOutput() {
        LlmJsonTaskPlanner planner = new LlmJsonTaskPlanner(
                ignored -> "{\"intentType\":\"BAD\",\"summary\":\"shell\",\"shellCommand\":\"rm -rf /\",\"steps\":[]}",
                toolRegistry(),
                new ObjectMapper()
        );

        assertThatThrownBy(() -> planner.plan("直接执行 shell"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("invalid plan JSON");
    }

    @Test
    void shouldRejectUnsupportedToolArgument() {
        LlmJsonTaskPlanner planner = new LlmJsonTaskPlanner(
                ignored -> "{\"intentType\":\"SERVICE_RESTART\",\"summary\":\"restart\",\"steps\":[{\"stepId\":\"step-1\",\"toolName\":\"restart_service\",\"arguments\":{\"serviceName\":\"nginx\",\"shellCommand\":\"rm -rf /\"},\"reason\":\"restart\"}]}",
                toolRegistry(),
                new ObjectMapper()
        );

        assertThatThrownBy(() -> planner.plan("重启 nginx"))
                .isInstanceOfSatisfying(LlmPlanValidationException.class, exception ->
                        assertThat(exception.code()).isEqualTo(LlmPlanValidationErrorCode.UNSUPPORTED_ARGUMENT)
                )
                .hasMessageContaining("unsupported argument");
    }

    @Test
    void shouldRejectMissingRequiredArgument() {
        LlmJsonTaskPlanner planner = new LlmJsonTaskPlanner(
                ignored -> "{\"intentType\":\"SERVICE_RESTART\",\"summary\":\"restart\",\"steps\":[{\"stepId\":\"step-1\",\"toolName\":\"restart_service\",\"arguments\":{},\"reason\":\"restart\"}]}",
                toolRegistry(),
                new ObjectMapper()
        );

        assertThatThrownBy(() -> planner.plan("重启 nginx"))
                .isInstanceOfSatisfying(LlmPlanValidationException.class, exception ->
                        assertThat(exception.code()).isEqualTo(LlmPlanValidationErrorCode.REQUIRED_ARGUMENT_MISSING)
                )
                .hasMessageContaining("missed required argument");
    }

    @Test
    void shouldRejectOversizedRawJsonResponse() {
        LlmJsonTaskPlanner planner = new LlmJsonTaskPlanner(
                ignored -> "{\"intentType\":\"" + "A".repeat(LlmJsonTaskPlanner.MAX_RAW_JSON_LENGTH) + "\",\"summary\":\"too large\",\"steps\":[]}",
                toolRegistry(),
                new ObjectMapper()
        );

        assertThatThrownBy(() -> planner.plan("超大响应"))
                .isInstanceOfSatisfying(LlmPlanValidationException.class, exception ->
                        assertThat(exception.code()).isEqualTo(LlmPlanValidationErrorCode.RESPONSE_TOO_LARGE)
                )
                .hasMessageContaining("too large JSON response");
    }

    @Test
    void shouldRejectTooLongSummary() {
        LlmJsonTaskPlanner planner = new LlmJsonTaskPlanner(
                ignored -> "{\"intentType\":\"SYSTEM_HEALTH_CHECK\",\"summary\":\"" + "A".repeat(LlmPlanValidator.MAX_SUMMARY_LENGTH + 1) + "\",\"steps\":[]}",
                toolRegistry(),
                new ObjectMapper()
        );

        assertThatThrownBy(() -> planner.plan("超长摘要"))
                .isInstanceOfSatisfying(LlmPlanValidationException.class, exception ->
                        assertThat(exception.code()).isEqualTo(LlmPlanValidationErrorCode.FIELD_TOO_LONG)
                )
                .hasMessageContaining("field is too long: summary");
    }

    @Test
    void shouldRejectTooLongArgumentStringValue() {
        LlmJsonTaskPlanner planner = new LlmJsonTaskPlanner(
                ignored -> "{\"intentType\":\"SERVICE_RESTART\",\"summary\":\"restart\",\"steps\":[{\"stepId\":\"step-1\",\"toolName\":\"restart_service\",\"arguments\":{\"serviceName\":\"" + "n".repeat(LlmPlanValidator.MAX_ARGUMENT_STRING_LENGTH + 1) + "\"},\"reason\":\"restart\"}]}",
                toolRegistry(),
                new ObjectMapper()
        );

        assertThatThrownBy(() -> planner.plan("重启超长服务名"))
                .isInstanceOfSatisfying(LlmPlanValidationException.class, exception ->
                        assertThat(exception.code()).isEqualTo(LlmPlanValidationErrorCode.ARGUMENT_VALUE_TOO_LONG)
                )
                .hasMessageContaining("argument value is too long: serviceName");
    }

    @Test
    void shouldRejectTooManySteps() {
        LlmJsonTaskPlanner planner = new LlmJsonTaskPlanner(
                ignored -> "{\"intentType\":\"TOO_MANY\",\"summary\":\"too many\",\"steps\":["
                        + "{\"stepId\":\"1\",\"toolName\":\"get_system_load\",\"arguments\":{}},"
                        + "{\"stepId\":\"2\",\"toolName\":\"get_system_load\",\"arguments\":{}},"
                        + "{\"stepId\":\"3\",\"toolName\":\"get_system_load\",\"arguments\":{}},"
                        + "{\"stepId\":\"4\",\"toolName\":\"get_system_load\",\"arguments\":{}},"
                        + "{\"stepId\":\"5\",\"toolName\":\"get_system_load\",\"arguments\":{}},"
                        + "{\"stepId\":\"6\",\"toolName\":\"get_system_load\",\"arguments\":{}}]}",
                toolRegistry(),
                new ObjectMapper()
        );

        assertThatThrownBy(() -> planner.plan("巡检"))
                .isInstanceOfSatisfying(LlmPlanValidationException.class, exception ->
                        assertThat(exception.code()).isEqualTo(LlmPlanValidationErrorCode.TOO_MANY_STEPS)
                )
                .hasMessageContaining("too many steps");
    }

    @Test
    void shouldNotUseRagWhenDisabledEvenWithTraceId() {
        RecordingLlmPlannerClient client = new RecordingLlmPlannerClient(emptyPlanJson());
        RagProperties properties = new RagProperties();
        properties.setEnabled(false);
        LlmJsonTaskPlanner planner = new LlmJsonTaskPlanner(
                client,
                toolRegistry(),
                new ObjectMapper(),
                properties,
                auditedRagAugmentor(properties)
        );

        TaskPlan plan = planner.plan("trace-1", "检查 nginx");

        assertThat(plan.steps()).isEmpty();
        assertThat(client.ragContext()).isNull();
    }

    @Test
    void shouldUseRagContextWhenEnabledAndTraceIdIsProvided() {
        RecordingLlmPlannerClient client = new RecordingLlmPlannerClient(emptyPlanJson());
        RagProperties properties = new RagProperties();
        properties.setEnabled(true);
        LlmJsonTaskPlanner planner = new LlmJsonTaskPlanner(
                client,
                toolRegistry(),
                new ObjectMapper(),
                properties,
                auditedRagAugmentor(properties)
        );

        TaskPlan plan = planner.plan("trace-1", "nginx 502");

        assertThat(plan.steps()).isEmpty();
        assertThat(client.ragContext())
                .contains("Retrieved Knowledge Context:")
                .contains("knowledgeId: runbook-nginx")
                .contains("Check nginx status before restart.");
    }

    private String emptyPlanJson() {
        return "{\"intentType\":\"DIAGNOSTIC\",\"summary\":\"no executable action\",\"suggestedRiskLevel\":\"LOW\",\"steps\":[]}";
    }

    private AuditedRagAugmentor auditedRagAugmentor(RagProperties properties) {
        KnowledgeRetriever retriever = query -> List.of(new RetrievedKnowledge(
                "runbook-nginx",
                "runbook",
                "Nginx troubleshooting",
                "Check nginx status before restart.",
                0.91,
                Instant.parse("2026-07-01T00:00:00Z"),
                Map.of()
        ));
        RagAugmentor ragAugmentor = new RagAugmentor(retriever, new RagContextFactory(), properties);
        AuditLogService auditLogService = new AuditLogService();
        auditLogService.startTrace("test");
        return new AuditedRagAugmentor(ragAugmentor, auditLogService, properties);
    }

    private static class RecordingLlmPlannerClient implements LlmPlannerClient {
        private final String responseJson;
        private String ragContext;

        RecordingLlmPlannerClient(String responseJson) {
            this.responseJson = responseJson;
        }

        @Override
        public String createPlanJson(String userInput) {
            this.ragContext = null;
            return responseJson;
        }

        @Override
        public String createPlanJson(String userInput, String ragContext) {
            this.ragContext = ragContext;
            return responseJson;
        }

        String ragContext() {
            return ragContext;
        }
    }

    private ToolRegistry toolRegistry() {
        return new ToolRegistry(List.of(
                tool(SystemLoadTool.NAME, Map.of(), RiskLevel.LOW, true, false),
                tool(TopProcessesTool.NAME, Map.of("limit", "integer: optional, 1-50"), RiskLevel.LOW, true, false),
                tool(OpenPortsTool.NAME, Map.of(), RiskLevel.LOW, true, false),
                tool(PortUsageTool.NAME, Map.of("port", "integer: required, 1-65535"), RiskLevel.LOW, true, false),
                tool(ServiceStatusTool.NAME, Map.of("serviceName", "string: required, service name"), RiskLevel.LOW, true, false),
                tool(RestartServiceTool.NAME, Map.of("serviceName", "string: required, whitelisted service name"), RiskLevel.MEDIUM, false, true)
        ));
    }

    private OpsTool tool(String name, Map<String, String> inputSchema, RiskLevel riskLevel, boolean readOnly, boolean requiresApproval) {
        ToolDefinition definition = new ToolDefinition(
                name,
                name,
                inputSchema,
                riskLevel,
                readOnly,
                requiresApproval,
                PermissionRequirement.NONE,
                1000,
                1024,
                true
        );
        return new OpsTool() {
            @Override
            public ToolDefinition definition() {
                return definition;
            }

            @Override
            public ToolExecutionResult execute(ToolCall call) {
                return ToolExecutionResult.skipped(name, "test tool");
            }
        };
    }
}
