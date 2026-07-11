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
@Order(15)
public class ToolEnabledPolicy implements Policy {

    @Override
    public String name() {
        return "ToolEnabledPolicy";
    }

    @Override
    public Optional<PolicyDecision> evaluate(PolicyContext context) {
        if (context.toolDefinition().isPresent() && !context.toolDefinition().get().enabled()) {
            String reason = "Tool is disabled: " + context.toolDefinition().get().name();
            return Optional.of(PolicyDecision.block(
                    RiskLevel.HIGH,
                    reason,
                    List.of(new PolicyHit(name(), "HIGH", reason)),
                    List.of("请启用工具后再执行，或改用其他已启用工具")
            ));
        }
        return Optional.empty();
    }
}
