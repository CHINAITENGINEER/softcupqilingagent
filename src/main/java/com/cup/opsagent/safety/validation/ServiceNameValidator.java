package com.cup.opsagent.safety.validation;

import java.util.Optional;
import java.util.regex.Pattern;

public final class ServiceNameValidator {

    private static final Pattern SERVICE_NAME_PATTERN = Pattern.compile("^(?!-)[a-zA-Z0-9_.@-]{1,80}$");
    private static final Pattern INJECTION_PATTERN = Pattern.compile("(;|&&|\\|\\||\\||`|\\$\\(|>|<|\\n|\\r)");

    private ServiceNameValidator() {
    }

    public static Optional<String> validate(Object rawServiceName) {
        if (!(rawServiceName instanceof String serviceName) || serviceName.isBlank()) {
            return Optional.of("serviceName is required");
        }
        if (INJECTION_PATTERN.matcher(serviceName).find()) {
            return Optional.of("serviceName contains shell injection characters");
        }
        if (serviceName.startsWith("-")) {
            return Optional.of("serviceName must not start with '-'");
        }
        if (serviceName.contains("/") || serviceName.contains("..")) {
            return Optional.of("serviceName must not contain path traversal characters");
        }
        if (!SERVICE_NAME_PATTERN.matcher(serviceName).matches()) {
            return Optional.of("serviceName contains illegal characters");
        }
        return Optional.empty();
    }

    public static Optional<String> extract(Object rawServiceName) {
        return validate(rawServiceName).isEmpty() ? Optional.of((String) rawServiceName) : Optional.empty();
    }
}
