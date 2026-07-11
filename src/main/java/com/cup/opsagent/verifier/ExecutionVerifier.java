package com.cup.opsagent.verifier;

import com.cup.opsagent.tool.core.ToolCall;
import com.cup.opsagent.tool.core.ToolDefinition;

public interface ExecutionVerifier {

    String name();

    boolean supports(ToolCall toolCall, ToolDefinition toolDefinition);

    VerificationResult verify(VerificationContext context);
}
