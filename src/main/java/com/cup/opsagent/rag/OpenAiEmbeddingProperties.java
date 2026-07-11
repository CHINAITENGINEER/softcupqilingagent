package com.cup.opsagent.rag;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;

@Component
@ConfigurationProperties(prefix = "ops-agent.rag.embedding.openai")
public class OpenAiEmbeddingProperties {

    private String baseUrl = "";
    private String apiKey = "";
    private String model = "";

    public void validate() {
        requireText(baseUrl, "baseUrl");
        requireText(apiKey, "apiKey");
        requireText(model, "model");
        validateBaseUrl();
    }

    private void requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("missing required OpenAI embedding config: " + fieldName);
        }
    }

    private void validateBaseUrl() {
        URI uri;
        try {
            uri = new URI(baseUrl.trim());
        } catch (URISyntaxException exception) {
            throw new IllegalArgumentException("invalid OpenAI embedding baseUrl", exception);
        }
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
        String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT);
        if (!scheme.equals("https") && !(scheme.equals("http") && isLocalhost(host))) {
            throw new IllegalArgumentException("OpenAI embedding baseUrl must use https, except localhost test endpoints");
        }
        if (uri.getRawQuery() != null || uri.getRawUserInfo() != null) {
            throw new IllegalArgumentException("OpenAI embedding baseUrl must not contain userinfo or query");
        }
    }

    private boolean isLocalhost(String host) {
        return host.equals("localhost") || host.equals("127.0.0.1") || host.equals("::1");
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
}
