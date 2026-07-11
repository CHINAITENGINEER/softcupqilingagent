package com.cup.opsagent.planner.llm.provider;

import java.util.regex.Pattern;

public final class LlmSafeLogSanitizer {

    public static final int MAX_EXCEPTION_TEXT_LENGTH = 240;
    public static final int MAX_AUDIT_TEXT_LENGTH = 512;
    public static final int MAX_RAW_RESPONSE_AUDIT_LENGTH = 2_048;

    private static final String REDACTED = "[REDACTED]";
    private static final Pattern AUTHORIZATION_HEADER = Pattern.compile("(?i)(authorization\\s*[:=]\\s*bearer\\s+)[^\\s,;]+");
    private static final Pattern BEARER_TOKEN = Pattern.compile("(?i)(bearer\\s+)[A-Za-z0-9._~+/=-]{12,}");
    private static final Pattern OPENAI_STYLE_KEY = Pattern.compile("\\bsk-[A-Za-z0-9_-]{8,}\\b");
    private static final Pattern NAMED_API_KEY = Pattern.compile("(?i)((api[-_ ]?key|token|secret)\\s*[:=]\\s*)[^\\s,;]+");

    private LlmSafeLogSanitizer() {
    }

    public static String sanitizeForException(String value) {
        return truncate(redactSecrets(value), MAX_EXCEPTION_TEXT_LENGTH);
    }

    public static String sanitizeForAudit(String value) {
        return truncate(redactSecrets(value), MAX_AUDIT_TEXT_LENGTH);
    }

    public static String sanitizeRawResponseForAudit(String value) {
        return truncate(redactSecrets(value), MAX_RAW_RESPONSE_AUDIT_LENGTH);
    }

    public static String redactSecrets(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        String sanitized = AUTHORIZATION_HEADER.matcher(value).replaceAll("$1" + REDACTED);
        sanitized = BEARER_TOKEN.matcher(sanitized).replaceAll("$1" + REDACTED);
        sanitized = OPENAI_STYLE_KEY.matcher(sanitized).replaceAll(REDACTED);
        return NAMED_API_KEY.matcher(sanitized).replaceAll("$1" + REDACTED);
    }

    public static String truncate(String value, int maxLength) {
        if (value == null || value.isBlank()) {
            return "";
        }
        if (maxLength < 0) {
            throw new IllegalArgumentException("maxLength must be non-negative");
        }
        if (value.length() <= maxLength) {
            return value;
        }
        if (maxLength <= 3) {
            return ".".repeat(maxLength);
        }
        return value.substring(0, maxLength - 3) + "...";
    }
}
