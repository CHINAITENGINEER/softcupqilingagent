package com.cup.opsagent.executor;

import java.util.List;

record CommandSpec(
        CommandTemplateId templateId,
        List<String> command,
        long timeoutMs,
        int outputLimitBytes
) {
    public CommandSpec {
        if (templateId == null) {
            throw new IllegalArgumentException("templateId must not be null");
        }
        if (command == null || command.isEmpty()) {
            throw new IllegalArgumentException("command must not be empty");
        }
        if (command.stream().anyMatch(part -> part == null || part.isBlank())) {
            throw new IllegalArgumentException("command elements must not be null or blank");
        }
        command = List.copyOf(command);
        if (timeoutMs <= 0) {
            throw new IllegalArgumentException("timeoutMs must be positive");
        }
        if (outputLimitBytes <= 0) {
            throw new IllegalArgumentException("outputLimitBytes must be positive");
        }
    }
}
