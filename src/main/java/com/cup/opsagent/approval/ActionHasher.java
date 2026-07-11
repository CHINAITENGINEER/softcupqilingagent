package com.cup.opsagent.approval;

import com.cup.opsagent.tool.core.RiskLevel;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

public final class ActionHasher {

    private ActionHasher() {
    }

    public static String hash(String toolName, Map<String, Object> arguments, RiskLevel riskLevel) {
        return sha256(canonicalAction(toolName, arguments, riskLevel));
    }

    static String canonicalAction(String toolName, Map<String, Object> arguments, RiskLevel riskLevel) {
        if (toolName == null || toolName.isBlank()) {
            throw new IllegalArgumentException("toolName must not be blank");
        }
        if (riskLevel == null) {
            throw new IllegalArgumentException("riskLevel must not be null");
        }
        return "toolName=" + escape(toolName.trim())
                + "|riskLevel=" + riskLevel.name()
                + "|arguments=" + canonicalValue(arguments == null ? Map.of() : arguments);
    }

    @SuppressWarnings("unchecked")
    private static String canonicalValue(Object value) {
        if (value == null) {
            return "null";
        }
        if (value instanceof Map<?, ?> map) {
            return map.entrySet().stream()
                    .sorted(Comparator.comparing(entry -> String.valueOf(entry.getKey())))
                    .map(entry -> escape(String.valueOf(entry.getKey())) + ":" + canonicalValue(entry.getValue()))
                    .reduce("{", (left, right) -> left.equals("{") ? left + right : left + "," + right) + "}";
        }
        if (value instanceof List<?> list) {
            return list.stream()
                    .map(ActionHasher::canonicalValue)
                    .reduce("[", (left, right) -> left.equals("[") ? left + right : left + "," + right) + "]";
        }
        if (value instanceof String string) {
            return '"' + escape(string) + '"';
        }
        if (value instanceof Number || value instanceof Boolean) {
            return String.valueOf(value);
        }
        if (value instanceof Enum<?> enumValue) {
            return '"' + escape(enumValue.name()) + '"';
        }
        if (value instanceof Map) {
            return canonicalValue((Map<String, Object>) value);
        }
        return '"' + escape(String.valueOf(value)) + '"';
    }

    private static String escape(String value) {
        return value.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("|", "\\|")
                .replace(":", "\\:")
                .replace(",", "\\,");
    }

    private static String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder(bytes.length * 2);
            for (byte current : bytes) {
                builder.append(String.format("%02x", current));
            }
            return builder.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }
}
