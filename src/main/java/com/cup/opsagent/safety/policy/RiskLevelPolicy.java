package com.cup.opsagent.safety.policy;

import com.cup.opsagent.safety.Policy;
import com.cup.opsagent.safety.PolicyContext;
import com.cup.opsagent.safety.PolicyDecision;
import com.cup.opsagent.safety.PolicyHit;
import com.cup.opsagent.tool.core.RiskLevel;
import com.cup.opsagent.tool.core.ToolDefinition;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
@Order(40)
public class RiskLevelPolicy implements Policy {

    @Override
    public String name() {
        return "RiskLevelPolicy";
    }

    @Override
    public Optional<PolicyDecision> evaluate(PolicyContext context) {
        if (context.toolDefinition().isEmpty()) {
            return Optional.empty();
        }
        ToolDefinition definition = context.toolDefinition().get();
        RiskLevel riskLevel = definition.riskLevel();

        if (riskLevel == RiskLevel.CRITICAL || riskLevel == RiskLevel.HIGH) {
            String reason = "Tool risk level is blocked: " + riskLevel;
            return Optional.of(PolicyDecision.block(
                    riskLevel,
                    reason,
                    List.of(new PolicyHit(name(), riskLevel.name(), reason)),
                    List.of("高危工具默认禁止自动执行")
            ));
        }

        if (riskLevel == RiskLevel.MEDIUM || definition.requiresApproval()) {
            return Optional.of(PolicyDecision.waitingApproval(
                    riskLevel,
                    "Tool requires human approval: " + definition.name(),
                    List.of("请在审批页面确认该中风险操作")
            ));
        }

        return Optional.empty();
    }
}
