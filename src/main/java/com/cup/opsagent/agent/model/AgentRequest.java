package com.cup.opsagent.agent.model;

import jakarta.validation.constraints.NotBlank;

public record AgentRequest(
        @NotBlank(message = "message must not be blank")
        String message,
        String sessionId,
        String userId
) {
}
