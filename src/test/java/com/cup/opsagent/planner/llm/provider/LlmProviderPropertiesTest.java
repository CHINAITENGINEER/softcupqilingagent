package com.cup.opsagent.planner.llm.provider;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LlmProviderPropertiesTest {

    @Test
    void shouldValidateCompleteHttpsConfiguration() {
        LlmProviderProperties properties = validProperties();

        properties.validateForLlmMode();

        assertThat(properties.safeBaseUrlHost()).isEqualTo("api.example.com");
    }

    @Test
    void shouldAllowLocalhostHttpForTests() {
        LlmProviderProperties properties = validProperties();
        properties.setBaseUrl("http://localhost:11434/v1");

        properties.validateForLlmMode();

        assertThat(properties.safeBaseUrlHost()).isEqualTo("localhost");
    }

    @Test
    void shouldFailClosedWhenRequiredConfigIsMissing() {
        LlmProviderProperties properties = validProperties();
        properties.setApiKey(" ");

        assertThatThrownBy(properties::validateForLlmMode)
                .isInstanceOfSatisfying(LlmProviderException.class, exception ->
                        assertThat(exception.code()).isEqualTo(LlmProviderErrorCode.LLM_PROVIDER_MISCONFIGURED)
                )
                .hasMessageContaining("apiKey");
    }

    @Test
    void shouldRejectNonHttpsNonLocalBaseUrl() {
        LlmProviderProperties properties = validProperties();
        properties.setBaseUrl("http://api.example.com/v1");

        assertThatThrownBy(properties::validateForLlmMode)
                .isInstanceOfSatisfying(LlmProviderException.class, exception ->
                        assertThat(exception.code()).isEqualTo(LlmProviderErrorCode.LLM_PROVIDER_MISCONFIGURED)
                )
                .hasMessageContaining("https");
    }

    @Test
    void shouldRejectBaseUrlWithUserInfoOrQuery() {
        LlmProviderProperties properties = validProperties();
        properties.setBaseUrl("https://user:pass@api.example.com/v1?token=secret");

        assertThatThrownBy(properties::validateForLlmMode)
                .isInstanceOf(LlmProviderException.class)
                .hasMessageContaining("userinfo or query");
    }

    @Test
    void shouldRejectUnsupportedThinkingMode() {
        LlmProviderProperties properties = validProperties();
        properties.setThinkingMode("automatic");

        assertThatThrownBy(properties::validateForLlmMode)
                .isInstanceOf(LlmProviderException.class)
                .hasMessageContaining("thinkingMode");
    }

    @Test
    void shouldRejectInvalidNumericConfig() {
        LlmProviderProperties properties = validProperties();
        properties.setReadTimeoutMs(0);

        assertThatThrownBy(properties::validateForLlmMode)
                .isInstanceOf(LlmProviderException.class)
                .hasMessageContaining("readTimeoutMs");
    }

    private LlmProviderProperties validProperties() {
        LlmProviderProperties properties = new LlmProviderProperties();
        properties.setProvider("openai-compatible");
        properties.setBaseUrl("https://api.example.com/v1");
        properties.setApiKey("sk-validsecret123456789");
        properties.setModel("test-model");
        return properties;
    }
}
