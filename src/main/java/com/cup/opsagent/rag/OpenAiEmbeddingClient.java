package com.cup.opsagent.rag;

import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.util.List;

public class OpenAiEmbeddingClient implements EmbeddingClient {

    private static final String EMBEDDINGS_PATH = "/embeddings";

    private final OpenAiEmbeddingProperties properties;
    private final RestClient restClient;

    public OpenAiEmbeddingClient(OpenAiEmbeddingProperties properties, RestClient.Builder restClientBuilder) {
        if (properties == null) {
            throw new IllegalArgumentException("openAiEmbeddingProperties must not be null");
        }
        if (restClientBuilder == null) {
            throw new IllegalArgumentException("restClientBuilder must not be null");
        }
        properties.validate();
        this.properties = properties;
        this.restClient = restClientBuilder
                .baseUrl(trimTrailingSlash(properties.getBaseUrl()))
                .defaultHeader("Authorization", "Bearer " + properties.getApiKey())
                .build();
    }

    OpenAiEmbeddingClient(OpenAiEmbeddingProperties properties, RestClient restClient) {
        if (properties == null) {
            throw new IllegalArgumentException("openAiEmbeddingProperties must not be null");
        }
        if (restClient == null) {
            throw new IllegalArgumentException("restClient must not be null");
        }
        properties.validate();
        this.properties = properties;
        this.restClient = restClient;
    }

    @Override
    public EmbeddingVector embed(String text) {
        String input = text == null ? "" : text;
        try {
            OpenAiEmbeddingResponse response = restClient.post()
                    .uri(EMBEDDINGS_PATH)
                    .body(new OpenAiEmbeddingRequest(properties.getModel(), input))
                    .retrieve()
                    .body(OpenAiEmbeddingResponse.class);
            return toEmbeddingVector(response);
        } catch (RestClientResponseException exception) {
            throw new IllegalStateException("OpenAI embedding provider HTTP error status=" + exception.getStatusCode().value());
        } catch (ResourceAccessException exception) {
            throw new IllegalStateException("OpenAI embedding provider request failed or timed out", exception);
        } catch (RestClientException exception) {
            throw new IllegalStateException("OpenAI embedding provider request failed", exception);
        }
    }

    private EmbeddingVector toEmbeddingVector(OpenAiEmbeddingResponse response) {
        if (response == null || response.data() == null || response.data().isEmpty()) {
            throw new IllegalStateException("OpenAI embedding provider returned empty response");
        }
        List<Float> embedding = response.data().getFirst().embedding();
        if (embedding == null || embedding.isEmpty()) {
            throw new IllegalStateException("OpenAI embedding provider returned empty embedding");
        }
        float[] values = new float[embedding.size()];
        for (int index = 0; index < embedding.size(); index++) {
            Float value = embedding.get(index);
            values[index] = value == null ? 0.0f : value;
        }
        return new EmbeddingVector(values);
    }

    private static String trimTrailingSlash(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        String trimmed = value.trim();
        while (trimmed.endsWith("/")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed;
    }
}
