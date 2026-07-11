package com.cup.opsagent.safety;

import java.util.Optional;

public interface Policy {

    String name();

    Optional<PolicyDecision> evaluate(PolicyContext context);
}
