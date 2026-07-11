package com.cup.opsagent.rag;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;

@Component
@ConfigurationProperties(prefix = "ops-agent.rag.embedding")
public class RagEmbeddingProperties {

    private String baseUrl = "";
    private String apiKey = "";
    private String model = "";
    private int connectTimeoutMs = 15_000;
    private int readTimeoutMs = 15_000;

    public void validateForOpenAi() {
        requireText(baseUrl, "baseUrl");
        requireText(apiKey, "apiKey");
        requireText(model, "model");
        validateBaseUrl();
        validatePositive(connectTimeoutMs, "connectTimeoutMs");
        validatePositive(readTimeoutMs, "readTimeoutMs");
    }

    private void requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw misconfigured("missing required RAG embedding provider config: " + fieldName);
        }
    }

    private void validateBaseUrl() {
        URI uri;
        try {
            uri = new URI(baseUrl.trim());
        } catch (URISyntaxException exception) {
            throw misconfigured("invalid RAG embedding provider baseUrl");
        }
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
        String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT);
        if (!scheme.equals("https") && !(scheme.equals("http") && isLocalhost(host))) {
            throw misconfigured("RAG embedding provider baseUrl must use https, except localhost test endpoints");
        }
        if (uri.getRawQuery() != null || uri.getRawUserInfo() != null) {
            throw misconfigured("RAG embedding provider baseUrl must not contain userinfo or query");
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

    private RagProviderException misconfigured(String message) {
        return new RagProviderException(RagProviderErrorCode.RAG_PROVIDER_MISCONFIGURED, message);
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
}
