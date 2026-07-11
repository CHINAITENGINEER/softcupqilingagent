package com.cup.opsagent.safety.policy;

import com.cup.opsagent.safety.Policy;
import com.cup.opsagent.safety.PolicyContext;
import com.cup.opsagent.safety.PolicyDecision;
import com.cup.opsagent.safety.PolicyHit;
import com.cup.opsagent.tool.core.RiskLevel;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
@Order(10)
public class ToolExistencePolicy implements Policy {

    @Override
    public String name() {
        return "ToolExistencePolicy";
    }

    @Override
    public Optional<PolicyDecision> evaluate(PolicyContext context) {
        if (context.toolCall() == null || context.toolCall().toolName() == null || context.toolCall().toolName().isBlank()) {
            return Optional.of(block("Tool name is missing"));
        }
        if (context.toolDefinition().isEmpty()) {
            return Optional.of(block("Tool is not registered: " + context.toolCall().toolName()));
        }
        return Optional.empty();
    }

    private PolicyDecision block(String reason) {
        return PolicyDecision.block(
                RiskLevel.HIGH,
                reason,
                List.of(new PolicyHit(name(), "HIGH", reason)),
                List.of("只能调用 ToolRegistry 中注册过的工具")
        );
    }
}
