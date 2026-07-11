package com.cup.opsagent.verifier;

import com.cup.opsagent.tool.core.ToolCall;
import com.cup.opsagent.tool.core.ToolDefinition;
import com.cup.opsagent.tool.core.ToolExecutionResult;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
@Order(100)
public class ReadOnlyToolVerifier implements ExecutionVerifier {

    @Override
    public String name() {
        return "ReadOnlyToolVerifier";
    }

    @Override
    public boolean supports(ToolCall toolCall, ToolDefinition toolDefinition) {
        return toolDefinition != null && toolDefinition.readOnly();
    }

    @Override
    public VerificationResult verify(VerificationContext context) {
        ToolExecutionResult result = context.executionResult();
        Map<String, Object> evidence = Map.of(
                "toolName", context.toolCall().toolName(),
                "success", result.success(),
                "exitCode", result.exitCode() == null ? "none" : result.exitCode(),
                "executionFailureCode", result.failureCode() == null ? "none" : result.failureCode().name(),
                "durationMs", result.durationMs(),
                "stdoutLength", result.stdout() == null ? 0 : result.stdout().length(),
                "stderrLength", result.stderr() == null ? 0 : result.stderr().length()
        );

        if (!result.success()) {
            return VerificationResult.failed(
                    name(),
                    "read-only tool execution did not succeed",
                    evidence,
                    List.of("检查目标系统是否安装对应命令", "确认当前运行环境是否为 Linux/Kylin")
            );
        }

        if (result.stdout() == null || result.stdout().isBlank()) {
            return VerificationResult.failed(
                    name(),
                    "read-only tool returned empty output",
                    evidence,
                    List.of("确认目标状态是否确实为空", "检查命令输出是否被权限或环境限制")
            );
        }

        return VerificationResult.passed(name(), "read-only tool returned successful and non-empty output", evidence);
    }
}
