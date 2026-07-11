package com.cup.opsagent.tool.core;

import com.cup.opsagent.tool.builtin.OpenPortsTool;
import com.cup.opsagent.tool.builtin.PortUsageTool;
import com.cup.opsagent.tool.builtin.RestartServiceTool;
import com.cup.opsagent.tool.builtin.ServiceStatusTool;
import com.cup.opsagent.tool.builtin.SystemLoadTool;
import com.cup.opsagent.tool.builtin.TopProcessesTool;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@Component
public class ToolRegistry {

    private static final Set<String> MVP_TOOL_ALLOWLIST = Set.of(
            SystemLoadTool.NAME,
            TopProcessesTool.NAME,
            OpenPortsTool.NAME,
            PortUsageTool.NAME,
            ServiceStatusTool.NAME,
            RestartServiceTool.NAME
    );

    private final Map<String, OpsTool> tools = new LinkedHashMap<>();

    public ToolRegistry(List<OpsTool> discoveredTools) {
        List<String> unexpectedTools = discoveredTools.stream()
                .map(tool -> tool.definition().name())
                .filter(toolName -> !MVP_TOOL_ALLOWLIST.contains(toolName))
                .sorted()
                .toList();
        if (!unexpectedTools.isEmpty()) {
            throw new IllegalStateException("unexpected OpsTool bean outside MVP allowlist: " + unexpectedTools);
        }

        discoveredTools.stream()
                .sorted(Comparator.comparing(tool -> tool.definition().name()))
                .forEach(this::register);
        if (!tools.keySet().containsAll(MVP_TOOL_ALLOWLIST)) {
            throw new IllegalStateException("MVP tool allowlist is not fully registered. expected=" + MVP_TOOL_ALLOWLIST + ", actual=" + tools.keySet());
        }
    }

    public void register(OpsTool tool) {
        ToolDefinition definition = tool.definition();
        validateDefinition(definition);
        if (tools.containsKey(definition.name())) {
            throw new IllegalArgumentException("duplicate tool name: " + definition.name());
        }
        tools.put(definition.name(), tool);
    }

    public Optional<OpsTool> findTool(String toolName) {
        return Optional.ofNullable(tools.get(toolName));
    }

    public Optional<ToolDefinition> findDefinition(String toolName) {
        return findTool(toolName).map(OpsTool::definition);
    }

    public Collection<ToolDefinition> listDefinitions() {
        return tools.values().stream()
                .map(OpsTool::definition)
                .toList();
    }

    public boolean exists(String toolName) {
        return tools.containsKey(toolName);
    }

    private void validateDefinition(ToolDefinition definition) {
        if (definition == null || definition.name() == null || definition.name().isBlank()) {
            throw new IllegalArgumentException("tool definition name must not be blank");
        }
        if (!MVP_TOOL_ALLOWLIST.contains(definition.name())) {
            throw new IllegalArgumentException("tool is not in MVP allowlist: " + definition.name());
        }
        if (definition.riskLevel() == null) {
            throw new IllegalArgumentException("tool riskLevel must not be null: " + definition.name());
        }
        if (!definition.enabled()) {
            throw new IllegalArgumentException("disabled MVP tool must not be registered: " + definition.name());
        }
        if (definition.name().equals(RestartServiceTool.NAME)) {
            if (definition.riskLevel() != RiskLevel.MEDIUM || !definition.requiresApproval() || definition.readOnly()) {
                throw new IllegalArgumentException("restart_service must be MEDIUM, requiresApproval=true, readOnly=false");
            }
            return;
        }
        if (definition.riskLevel() != RiskLevel.LOW || !definition.readOnly() || definition.requiresApproval()) {
            throw new IllegalArgumentException("read-only MVP tools must be LOW, readOnly=true, requiresApproval=false: " + definition.name());
        }
    }
}
