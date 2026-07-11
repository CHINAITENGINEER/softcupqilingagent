package com.cup.opsagent.tool.core;

import java.time.Instant;

public record ToolExecutionResult(
        String toolName,
        boolean success,
        String stdout,
        String stderr,
        Integer exitCode,
        long durationMs,
        Instant executedAt,
        ToolExecutionFailureCode failureCode
) {
    public ToolExecutionResult {
        if (toolName == null || toolName.isBlank()) {
            throw new IllegalArgumentException("toolName must not be blank");
        }
        if (durationMs < 0) {
            throw new IllegalArgumentException("durationMs must not be negative");
        }
        if (success && failureCode != null) {
            throw new IllegalArgumentException("failureCode must be null when success is true");
        }
        stdout = stdout == null ? "" : stdout;
        stderr = stderr == null ? "" : stderr;
        executedAt = executedAt == null ? Instant.now() : executedAt;
    }

    public ToolExecutionResult(
            String toolName,
            boolean success,
            String stdout,
            String stderr,
            Integer exitCode,
            long durationMs,
            Instant executedAt
    ) {
        this(toolName, success, stdout, stderr, exitCode, durationMs, executedAt, null);
    }

    public static ToolExecutionResult skipped(String toolName, String reason) {
        return new ToolExecutionResult(toolName, false, "", reason, null, 0, Instant.now());
    }
}
