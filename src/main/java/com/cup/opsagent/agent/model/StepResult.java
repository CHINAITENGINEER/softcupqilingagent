package com.cup.opsagent.agent.model;

import com.cup.opsagent.tool.core.ToolExecutionResult;
import com.cup.opsagent.verifier.VerificationResult;

public record StepResult(
        String stepId,
        String toolName,
        String decision,
        boolean executed,
        ToolExecutionResult executionResult,
        VerificationResult verificationResult,
        String message
) {
}
