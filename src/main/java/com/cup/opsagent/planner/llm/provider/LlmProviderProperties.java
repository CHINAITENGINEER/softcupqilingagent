package com.cup.opsagent.planner.llm.provider;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;

@Component
@ConfigurationProperties(prefix = "ops-agent.llm")
public class LlmProviderProperties {

    private String provider = "fake";
    private String baseUrl = "";
    private String apiKey = "";
    private String model = "";
    private int connectTimeoutMs = 15_000;
    private int readTimeoutMs = 15_000;
    private double temperature = 0.0;
    private int maxOutputTokens = 1_000;
    private boolean auditRawResponse = false;
    private boolean responseFormatJsonObject = true;
    private String thinkingMode = "";

    public void validateForLlmMode() {
        requireText(baseUrl, "baseUrl");
        requireText(apiKey, "apiKey");
        requireText(model, "model");
        validateBaseUrl();
        validatePositive(connectTimeoutMs, "connectTimeoutMs");
        validatePositive(readTimeoutMs, "readTimeoutMs");
        validatePositive(maxOutputTokens, "maxOutputTokens");
        if (temperature < 0 || temperature > 2) {
            throw misconfigured("temperature must be between 0 and 2");
        }
        validateThinkingMode();
    }

    public String normalizedThinkingMode() {
        return thinkingMode == null ? "" : thinkingMode.trim().toLowerCase(Locale.ROOT);
    }

    private void validateThinkingMode() {
        String normalized = normalizedThinkingMode();
        if (!normalized.isEmpty() && !normalized.equals("enabled") && !normalized.equals("disabled")) {
            throw misconfigured("thinkingMode must be enabled, disabled, or empty");
        }
    }

    public String safeBaseUrlHost() {
        if (baseUrl == null || baseUrl.isBlank()) {
            return "";
        }
        try {
            URI uri = new URI(baseUrl.trim());
            return uri.getHost() == null ? "" : uri.getHost();
        } catch (URISyntaxException exception) {
            return "";
        }
    }

    private void requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw misconfigured("missing required LLM provider config: " + fieldName);
        }
    }

    private void validateBaseUrl() {
        URI uri;
        try {
            uri = new URI(baseUrl.trim());
        } catch (URISyntaxException exception) {
            throw misconfigured("invalid LLM provider baseUrl");
        }
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
        String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT);
        if (!scheme.equals("https") && !(scheme.equals("http") && isLocalhost(host))) {
            throw misconfigured("LLM provider baseUrl must use https, except localhost test endpoints");
        }
        if (uri.getRawQuery() != null || uri.getRawUserInfo() != null) {
            throw misconfigured("LLM provider baseUrl must not contain userinfo or query");
        }
    }

    private boolean isLocalhost(String host) {
        return host.equals("localhost") || host.equals("127.0.0.1") || host.equals("::1");
    }

    private void validatePositive(int value, String fieldName) {
        if (value <= 0) {
            throw misconfigured(fieldName + " must be positive");
        }
    }

    private LlmProviderException misconfigured(String message) {
        return new LlmProviderException(LlmProviderErrorCode.LLM_PROVIDER_MISCONFIGURED, message);
    }

    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public int getConnectTimeoutMs() {
        return connectTimeoutMs;
    }

    public void setConnectTimeoutMs(int connectTimeoutMs) {
        this.connectTimeoutMs = connectTimeoutMs;
    }

    public int getReadTimeoutMs() {
        return readTimeoutMs;
    }

    public void setReadTimeoutMs(int readTimeoutMs) {
        this.readTimeoutMs = readTimeoutMs;
    }

    public double getTemperature() {
        return temperature;
    }

    public void setTemperature(double temperature) {
        this.temperature = temperature;
    }

    public int getMaxOutputTokens() {
        return maxOutputTokens;
    }

    public void setMaxOutputTokens(int maxOutputTokens) {
        this.maxOutputTokens = maxOutputTokens;
    }

    public boolean isAuditRawResponse() {
        return auditRawResponse;
    }

    public void setAuditRawResponse(boolean auditRawResponse) {
        this.auditRawResponse = auditRawResponse;
    }

    public boolean isResponseFormatJsonObject() {
        return responseFormatJsonObject;
    }

    public void setResponseFormatJsonObject(boolean responseFormatJsonObject) {
        this.responseFormatJsonObject = responseFormatJsonObject;
    }

    public String getThinkingMode() {
        return thinkingMode;
    }

    public void setThinkingMode(String thinkingMode) {
        this.thinkingMode = thinkingMode;
    }
}
