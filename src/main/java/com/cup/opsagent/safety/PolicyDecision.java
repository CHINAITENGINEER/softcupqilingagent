package com.cup.opsagent.safety;

import com.cup.opsagent.tool.core.RiskLevel;

import java.util.ArrayList;
import java.util.List;

public record PolicyDecision(
        PolicyDecisionType type,
        boolean allowed,
        boolean blocked,
        boolean needApproval,
        RiskLevel riskLevel,
        String reason,
        List<PolicyHit> violatedPolicies,
        List<String> suggestions
) {
    public static PolicyDecision allow(RiskLevel riskLevel, String reason) {
        return new PolicyDecision(PolicyDecisionType.ALLOW, true, false, false, riskLevel, reason, List.of(), List.of());
    }

    public static PolicyDecision block(RiskLevel riskLevel, String reason, List<PolicyHit> hits, List<String> suggestions) {
        return new PolicyDecision(PolicyDecisionType.BLOCK, false, true, false, riskLevel, reason, copy(hits), copy(suggestions));
    }

    public static PolicyDecision waitingApproval(RiskLevel riskLevel, String reason, List<String> suggestions) {
        return new PolicyDecision(PolicyDecisionType.WAITING_APPROVAL, false, false, true, riskLevel, reason, List.of(), copy(suggestions));
    }

    private static <T> List<T> copy(List<T> values) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        return List.copyOf(new ArrayList<>(values));
    }
}
