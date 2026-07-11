package com.cup.opsagent.executor;

public record CommandExecutionOptions(
        long timeoutMs,
        int outputLimitBytes
) {
    public CommandExecutionOptions {
        if (timeoutMs <= 0) {
            throw new IllegalArgumentException("timeoutMs must be positive");
        }
        if (outputLimitBytes <= 0) {
            throw new IllegalArgumentException("outputLimitBytes must be positive");
        }
    }
}
