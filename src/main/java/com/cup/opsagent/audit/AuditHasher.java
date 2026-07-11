package com.cup.opsagent.audit;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

public final class AuditHasher {

    public static final String GENESIS_HASH = "GENESIS";

    private AuditHasher() {
    }

    public static String hashEvent(AuditEvent event, String previousHash) {
        if (event == null) {
            throw new IllegalArgumentException("event must not be null");
        }
        String canonical = "previousHash=" + canonicalValue(previousHash == null ? GENESIS_HASH : previousHash)
                + "|traceId=" + canonicalValue(event.traceId())
                + "|eventType=" + canonicalValue(event.eventType() == null ? null : event.eventType().name())
                + "|eventTime=" + canonicalValue(canonicalInstant(event.eventTime()))
                + "|success=" + canonicalValue(event.success())
                + "|payload=" + canonicalValue(event.payload() == null ? Map.of() : event.payload())
                + "|errorMessage=" + canonicalValue(event.errorMessage());
        return sha256(canonical);
    }

    private static String canonicalInstant(Instant instant) {
        return instant == null ? null : instant.truncatedTo(ChronoUnit.MILLIS).toString();
    }

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
                    .map(AuditHasher::canonicalValue)
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
