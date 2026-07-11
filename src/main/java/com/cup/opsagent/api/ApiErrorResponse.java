package com.cup.opsagent.api;

import java.time.Instant;
import java.util.Map;

public record ApiErrorResponse(
        String code,
        String message,
        int status,
        String path,
        Instant timestamp,
        Map<String, Object> details
) {
    public ApiErrorResponse {
        if (code == null || code.isBlank()) {
            throw new IllegalArgumentException("code must not be blank");
        }
        message = message == null ? "" : message;
        if (status < 400 || status > 599) {
            throw new IllegalArgumentException("status must be an error HTTP status");
        }
        path = path == null ? "" : path;
        timestamp = timestamp == null ? Instant.now() : timestamp;
        details = details == null ? Map.of() : Map.copyOf(details);
    }

    static ApiErrorResponse of(String code, String message, int status, String path) {
        return new ApiErrorResponse(code, message, status, path, Instant.now(), Map.of());
    }

    static ApiErrorResponse of(String code, String message, int status, String path, Map<String, Object> details) {
        return new ApiErrorResponse(code, message, status, path, Instant.now(), details);
    }
}
