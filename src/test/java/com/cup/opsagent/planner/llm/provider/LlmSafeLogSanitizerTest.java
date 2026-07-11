package com.cup.opsagent.planner.llm.provider;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LlmSafeLogSanitizerTest {

    @Test
    void shouldRedactAuthorizationBearerHeader() {
        String sanitized = LlmSafeLogSanitizer.redactSecrets("Authorization: Bearer sk-testsecret123456789 status=401");

        assertThat(sanitized).contains("Authorization: Bearer [REDACTED]");
        assertThat(sanitized).doesNotContain("sk-testsecret123456789");
    }

    @Test
    void shouldRedactOpenAiStyleKeyAnywhereInText() {
        String sanitized = LlmSafeLogSanitizer.redactSecrets("provider error for key sk-prod_abcdefghijklmnopqrstuvwxyz");

        assertThat(sanitized).contains("[REDACTED]");
        assertThat(sanitized).doesNotContain("sk-prod_abcdefghijklmnopqrstuvwxyz");
    }

    @Test
    void shouldRedactNamedSecretFields() {
        String sanitized = LlmSafeLogSanitizer.redactSecrets("apiKey=abc123456789 token: xyz987654321 secret = hidden-value");

        assertThat(sanitized).contains("apiKey=[REDACTED]");
        assertThat(sanitized).contains("token: [REDACTED]");
        assertThat(sanitized).contains("secret = [REDACTED]");
        assertThat(sanitized).doesNotContain("abc123456789");
        assertThat(sanitized).doesNotContain("xyz987654321");
        assertThat(sanitized).doesNotContain("hidden-value");
    }

    @Test
    void shouldTruncateExceptionTextAfterRedaction() {
        String sensitiveText = "Authorization: Bearer sk-sensitive123456789 " + "x".repeat(LlmSafeLogSanitizer.MAX_EXCEPTION_TEXT_LENGTH + 50);

        String sanitized = LlmSafeLogSanitizer.sanitizeForException(sensitiveText);

        assertThat(sanitized).hasSize(LlmSafeLogSanitizer.MAX_EXCEPTION_TEXT_LENGTH);
        assertThat(sanitized).endsWith("...");
        assertThat(sanitized).doesNotContain("sk-sensitive123456789");
    }

    @Test
    void shouldUseLongerLimitForRawResponseAudit() {
        String rawResponse = "x".repeat(LlmSafeLogSanitizer.MAX_AUDIT_TEXT_LENGTH + 100);

        String audit = LlmSafeLogSanitizer.sanitizeForAudit(rawResponse);
        String rawAudit = LlmSafeLogSanitizer.sanitizeRawResponseForAudit(rawResponse);

        assertThat(audit).hasSize(LlmSafeLogSanitizer.MAX_AUDIT_TEXT_LENGTH);
        assertThat(rawAudit).hasSize(LlmSafeLogSanitizer.MAX_AUDIT_TEXT_LENGTH + 100);
    }

    @Test
    void shouldReturnEmptyTextForNullOrBlankInput() {
        assertThat(LlmSafeLogSanitizer.sanitizeForException(null)).isEmpty();
        assertThat(LlmSafeLogSanitizer.sanitizeForAudit("   ")).isEmpty();
    }

    @Test
    void shouldRejectNegativeTruncateLength() {
        assertThatThrownBy(() -> LlmSafeLogSanitizer.truncate("value", -1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("maxLength");
    }
}
