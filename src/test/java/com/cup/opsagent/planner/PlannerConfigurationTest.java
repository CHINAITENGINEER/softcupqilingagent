package com.cup.opsagent.planner;

import com.cup.opsagent.agent.model.TaskPlan;
import com.cup.opsagent.planner.llm.FakeLlmPlannerClient;
import com.cup.opsagent.planner.llm.LlmJsonTaskPlanner;
import com.cup.opsagent.planner.llm.PlannerMode;
import com.cup.opsagent.planner.llm.provider.OpenAiCompatibleLlmPlannerClient;
import com.cup.opsagent.rag.AuditedRagAugmentor;
import com.cup.opsagent.rag.RagProperties;
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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PlannerConfigurationTest {

    @Test
    void shouldUseRulePlannerByDefault() {
        RuleBasedTaskPlanner ruleBasedTaskPlanner = new RuleBasedTaskPlanner();

        TaskPlanner planner = configuration().taskPlanner(
                ruleBasedTaskPlanner,
                fakeLlmPlannerClient(),
                realLlmPlannerClient(),
                toolRegistry(),
                new ObjectMapper(),
                new RagProperties(),
                mock(AuditedRagAugmentor.class),
                ""
        );

        assertThat(planner).isSameAs(ruleBasedTaskPlanner);
    }

    @Test
    void shouldUseExplicitFakeLlmClientForFakeLlmMode() {
        TaskPlanner planner = configuration().taskPlanner(
                new RuleBasedTaskPlanner(),
                fakeLlmPlannerClient(),
                realLlmPlannerClient(),
                toolRegistry(),
                new ObjectMapper(),
                new RagProperties(),
                mock(AuditedRagAugmentor.class),
                "fake-llm"
        );

        assertThat(planner).isInstanceOf(LlmJsonTaskPlanner.class);
        TaskPlan plan = planner.plan("帮我重启 nginx");
        assertThat(plan.intentType()).isEqualTo("SERVICE_RESTART");
        assertThat(plan.steps().getFirst().toolName()).isEqualTo(RestartServiceTool.NAME);
    }

    @Test
    void shouldUseRealLlmClientForLlmMode() {
        OpenAiCompatibleLlmPlannerClient realLlmPlannerClient = realLlmPlannerClient();
        when(realLlmPlannerClient.createPlanJson("检查系统负载")).thenReturn("""
                {
                  "intentType":"SYSTEM_HEALTH_CHECK",
                  "summary":"用户希望执行系统健康巡检",
                  "suggestedRiskLevel":"LOW",
                  "steps":[
                    {
                      "stepId":"step-1",
                      "actionName":"inspect-system-load",
                      "toolName":"get_system_load",
                      "arguments":{},
                      "reason":"查看系统负载"
                    }
                  ]
                }
                """);

        TaskPlanner planner = configuration().taskPlanner(
                new RuleBasedTaskPlanner(),
                fakeLlmPlannerClient(),
                realLlmPlannerClient,
                toolRegistry(),
                new ObjectMapper(),
                new RagProperties(),
                mock(AuditedRagAugmentor.class),
                "llm"
        );

        assertThat(planner).isInstanceOf(LlmJsonTaskPlanner.class);
        TaskPlan plan = planner.plan("检查系统负载");
        assertThat(plan.intentType()).isEqualTo("SYSTEM_HEALTH_CHECK");
        assertThat(plan.steps().getFirst().toolName()).isEqualTo(SystemLoadTool.NAME);
    }

    @Test
    void shouldParsePlannerModeAliases() {
        assertThat(PlannerMode.from(null)).isEqualTo(PlannerMode.RULE);
        assertThat(PlannerMode.from("fake-llm")).isEqualTo(PlannerMode.FAKE_LLM);
        assertThat(PlannerMode.from("LLM")).isEqualTo(PlannerMode.LLM);
    }

    private PlannerConfiguration configuration() {
        return new PlannerConfiguration();
    }

    private FakeLlmPlannerClient fakeLlmPlannerClient() {
        return new FakeLlmPlannerClient(new ObjectMapper());
    }

    private OpenAiCompatibleLlmPlannerClient realLlmPlannerClient() {
        return mock(OpenAiCompatibleLlmPlannerClient.class);
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
                name + " test tool",
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
                return new ToolExecutionResult(name, true, "", "", 0, 1, Instant.now());
            }
        };
    }
}
