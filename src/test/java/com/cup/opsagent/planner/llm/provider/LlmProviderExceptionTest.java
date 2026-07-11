package com.cup.opsagent.planner.llm.provider;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LlmProviderExceptionTest {

    @Test
    void shouldSanitizeExceptionMessage() {
        LlmProviderException exception = new LlmProviderException(
                LlmProviderErrorCode.LLM_PROVIDER_UNAUTHORIZED,
                "provider rejected Authorization: Bearer sk-secret123456789 apiKey=sk-anothersecret123456789"
        );

        assertThat(exception.code()).isEqualTo(LlmProviderErrorCode.LLM_PROVIDER_UNAUTHORIZED);
        assertThat(exception.getMessage()).contains("[REDACTED]");
        assertThat(exception.getMessage()).doesNotContain("sk-secret123456789");
        assertThat(exception.getMessage()).doesNotContain("sk-anothersecret123456789");
    }

    @Test
    void shouldRetainCauseButSanitizeMessage() {
        RuntimeException cause = new RuntimeException("raw provider body sk-causesecret123456789");
        LlmProviderException exception = new LlmProviderException(
                LlmProviderErrorCode.LLM_PROVIDER_SERVER_ERROR,
                "upstream error token=sk-messagesecret123456789",
                cause
        );

        assertThat(exception.getCause()).isSameAs(cause);
        assertThat(exception.getMessage()).contains("token=[REDACTED]");
        assertThat(exception.getMessage()).doesNotContain("sk-messagesecret123456789");
    }
}
