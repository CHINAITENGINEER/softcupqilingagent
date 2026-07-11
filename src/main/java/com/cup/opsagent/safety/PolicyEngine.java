package com.cup.opsagent.safety;

import com.cup.opsagent.tool.core.RiskLevel;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

@Component
public class PolicyEngine {

    private final List<Policy> policies;

    public PolicyEngine(List<Policy> policies) {
        this.policies = policies.stream()
                .sorted(Comparator.comparingInt(policy -> {
                    org.springframework.core.annotation.Order order = policy.getClass().getAnnotation(org.springframework.core.annotation.Order.class);
                    return order == null ? Integer.MAX_VALUE : order.value();
                }))
                .toList();
    }

    public PolicyDecision evaluate(PolicyContext context) {
        for (Policy policy : policies) {
            Optional<PolicyDecision> decision = policy.evaluate(context);
            if (decision.isPresent()) {
                return decision.get();
            }
        }
        RiskLevel riskLevel = context.toolDefinition()
                .map(definition -> definition.riskLevel() == null ? RiskLevel.LOW : definition.riskLevel())
                .orElse(RiskLevel.HIGH);
        return PolicyDecision.allow(riskLevel, "Tool allowed");
    }
}
