package com.cup.opsagent.approval;

import com.cup.opsagent.tool.core.ToolExecutionResult;
import com.cup.opsagent.verifier.VerificationResult;

public record ApprovedActionResult(
        ExecutionLease lease,
        ToolExecutionResult executionResult,
        VerificationResult verificationResult
) {
}
