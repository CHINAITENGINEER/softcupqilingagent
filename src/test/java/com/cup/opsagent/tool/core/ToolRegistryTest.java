package com.cup.opsagent.tool.core;

import com.cup.opsagent.tool.builtin.OpenPortsTool;
import com.cup.opsagent.tool.builtin.PortUsageTool;
import com.cup.opsagent.tool.builtin.RestartServiceTool;
import com.cup.opsagent.tool.builtin.ServiceStatusTool;
import com.cup.opsagent.tool.builtin.SystemLoadTool;
import com.cup.opsagent.tool.builtin.TopProcessesTool;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ToolRegistryTest {

    @Test
    void shouldRegisterOnlyExplicitMvpAllowlistTools() {
        ToolRegistry registry = new ToolRegistry(List.of(
                tool(SystemLoadTool.NAME, RiskLevel.LOW, true, false),
                tool(TopProcessesTool.NAME, RiskLevel.LOW, true, false),
                tool(OpenPortsTool.NAME, RiskLevel.LOW, true, false),
                tool(PortUsageTool.NAME, RiskLevel.LOW, true, false),
                tool(ServiceStatusTool.NAME, RiskLevel.LOW, true, false),
                tool(RestartServiceTool.NAME, RiskLevel.MEDIUM, false, true)
        ));

        assertThat(registry.exists(SystemLoadTool.NAME)).isTrue();
        assertThat(registry.exists(RestartServiceTool.NAME)).isTrue();
        assertThat(registry.listDefinitions()).hasSize(6);
    }

    @Test
    void shouldFailStartupWhenUnexpectedOpsToolExists() {
        List<OpsTool> tools = List.of(
                tool(SystemLoadTool.NAME, RiskLevel.LOW, true, false),
                tool(TopProcessesTool.NAME, RiskLevel.LOW, true, false),
                tool(OpenPortsTool.NAME, RiskLevel.LOW, true, false),
                tool(PortUsageTool.NAME, RiskLevel.LOW, true, false),
                tool(ServiceStatusTool.NAME, RiskLevel.LOW, true, false),
                tool(RestartServiceTool.NAME, RiskLevel.MEDIUM, false, true),
                tool("shell_command", RiskLevel.CRITICAL, false, false)
        );

        assertThatThrownBy(() -> new ToolRegistry(tools))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("unexpected OpsTool bean outside MVP allowlist")
                .hasMessageContaining("shell_command");
    }

    @Test
    void shouldRejectMisconfiguredRestartService() {
        List<OpsTool> tools = List.of(
                tool(SystemLoadTool.NAME, RiskLevel.LOW, true, false),
                tool(TopProcessesTool.NAME, RiskLevel.LOW, true, false),
                tool(OpenPortsTool.NAME, RiskLevel.LOW, true, false),
                tool(PortUsageTool.NAME, RiskLevel.LOW, true, false),
                tool(ServiceStatusTool.NAME, RiskLevel.LOW, true, false),
                tool(RestartServiceTool.NAME, RiskLevel.LOW, true, false)
        );

        assertThatThrownBy(() -> new ToolRegistry(tools))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("restart_service must be MEDIUM");
    }

    private OpsTool tool(String name, RiskLevel riskLevel, boolean readOnly, boolean requiresApproval) {
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
        return new StubOpsTool(definition);
    }

    private record StubOpsTool(ToolDefinition definition) implements OpsTool {
        @Override
        public ToolExecutionResult execute(ToolCall call) {
            return new ToolExecutionResult(definition.name(), true, "", "", 0, 0, Instant.now());
        }
    }
}
