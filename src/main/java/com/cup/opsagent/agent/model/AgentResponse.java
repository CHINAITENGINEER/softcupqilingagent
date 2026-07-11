package com.cup.opsagent.agent.model;

import java.time.Instant;
import java.util.List;

public record AgentResponse(
        String traceId,
        String status,
        String answer,
        List<StepResult> steps,
        Instant startedAt,
        Instant endedAt
) {
}
