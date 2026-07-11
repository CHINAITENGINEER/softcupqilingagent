package com.cup.opsagent.safety;

import com.cup.opsagent.tool.core.ToolCall;
import com.cup.opsagent.tool.core.ToolRegistry;
import org.springframework.stereotype.Component;

@Component
public class SafetyOrchestrator {

    private final ToolRegistry toolRegistry;
    private final PolicyEngine policyEngine;

    public SafetyOrchestrator(ToolRegistry toolRegistry, PolicyEngine policyEngine) {
        this.toolRegistry = toolRegistry;
        this.policyEngine = policyEngine;
    }

    public PolicyDecision check(String userInput, ToolCall toolCall) {
        PolicyContext context = PolicyContext.of(
                userInput,
                toolCall,
                toolCall == null ? java.util.Optional.empty() : toolRegistry.findDefinition(toolCall.toolName())
        );
        return policyEngine.evaluate(context);
    }
}
