package com.cup.opsagent.safety;

public record PolicyHit(
        String policyName,
        String severity,
        String reason
) {
}
