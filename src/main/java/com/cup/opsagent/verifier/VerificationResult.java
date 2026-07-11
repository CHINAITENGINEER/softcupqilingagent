package com.cup.opsagent.verifier;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public record VerificationResult(
        String verifierName,
        boolean verified,
        String reason,
        Map<String, Object> evidence,
        List<String> suggestedRecovery,
        Instant checkedAt
) {
    public VerificationResult {
        verifierName = verifierName == null || verifierName.isBlank() ? "UnknownVerifier" : verifierName;
        reason = reason == null ? "" : reason;
        evidence = sanitizeEvidence(evidence);
        suggestedRecovery = sanitizeRecovery(suggestedRecovery);
        checkedAt = checkedAt == null ? Instant.now() : checkedAt;
    }

    public static VerificationResult passed(String verifierName, String reason, Map<String, Object> evidence) {
        return new VerificationResult(verifierName, true, reason, evidence, List.of(), Instant.now());
    }

    public static VerificationResult failed(String verifierName, String reason, Map<String, Object> evidence, List<String> suggestedRecovery) {
        return new VerificationResult(verifierName, false, reason, evidence, suggestedRecovery, Instant.now());
    }

    private static Map<String, Object> sanitizeEvidence(Map<String, Object> evidence) {
        if (evidence == null || evidence.isEmpty()) {
            return Map.of();
        }
        Map<String, Object> sanitized = new LinkedHashMap<>();
        evidence.forEach((key, value) -> {
            if (key != null) {
                sanitized.put(key, value == null ? "" : value);
            }
        });
        return Map.copyOf(sanitized);
    }

    private static List<String> sanitizeRecovery(List<String> suggestedRecovery) {
        if (suggestedRecovery == null || suggestedRecovery.isEmpty()) {
            return List.of();
        }
        return suggestedRecovery.stream()
                .filter(Objects::nonNull)
                .toList();
    }
}
