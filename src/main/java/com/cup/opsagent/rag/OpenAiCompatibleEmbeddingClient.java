package com.cup.opsagent.rag;

import com.cup.opsagent.rag.dto.OpenAiEmbeddingData;
import com.cup.opsagent.rag.dto.OpenAiEmbeddingRequest;
import com.cup.opsagent.rag.dto.OpenAiEmbeddingResponse;
import org.springframework.http.HttpStatusCode;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.util.List;

public class OpenAiCompatibleEmbeddingClient implements EmbeddingClient {

    private static final String EMBEDDINGS_PATH = "/embeddings";

    private final RagProperties properties;
    private final RestClient restClient;

    public OpenAiCompatibleEmbeddingClient(RagProperties properties, RestClient.Builder restClientBuilder) {
        this(
                properties,
                restClientBuilder
                        .baseUrl(trimTrailingSlash(properties.getEmbeddingBaseUrl()))
                        .defaultHeader("Authorization", "Bearer " + properties.getEmbeddingApiKey())
                        .build()
        );
    }

    OpenAiCompatibleEmbeddingClient(RagProperties properties, RestClient restClient) {
        this.properties = properties;
        this.restClient = restClient;
    }

    @Override
    public EmbeddingVector embed(String text) {
        properties.validateEmbeddingProviderConfig();
        OpenAiEmbeddingResponse response = execute(new OpenAiEmbeddingRequest(properties.getEmbeddingModel(), text == null ? "" : text));
        return toVector(response);
    }

    private OpenAiEmbeddingResponse execute(OpenAiEmbeddingRequest request) {
        try {
            return restClient.post()
                    .uri(EMBEDDINGS_PATH)
                    .body(request)
                    .retrieve()
                    .body(OpenAiEmbeddingResponse.class);
        } catch (RestClientResponseException exception) {
            throw mapHttpException(exception);
        } catch (ResourceAccessException exception) {
            throw new RagProviderException(RagProviderErrorCode.RAG_PROVIDER_TIMEOUT, "RAG embedding provider request failed or timed out", exception);
        } catch (RestClientException exception) {
            throw new RagProviderException(RagProviderErrorCode.RAG_PROVIDER_SERVER_ERROR, "RAG embedding provider request failed", exception);
        }
    }

    private EmbeddingVector toVector(OpenAiEmbeddingResponse response) {
        if (response == null || response.data() == null || response.data().isEmpty()) {
            throw new RagProviderException(RagProviderErrorCode.RAG_PROVIDER_BAD_RESPONSE, "RAG embedding provider returned no embedding data");
        }
        OpenAiEmbeddingData first = response.data().getFirst();
        List<Double> rawEmbedding = first == null ? List.of() : first.embedding();
        if (rawEmbedding == null || rawEmbedding.isEmpty()) {
            throw new RagProviderException(RagProviderErrorCode.RAG_PROVIDER_BAD_RESPONSE, "RAG embedding provider returned empty embedding");
        }
        float[] values = new float[rawEmbedding.size()];
        for (int index = 0; index < rawEmbedding.size(); index++) {
            Double value = rawEmbedding.get(index);
            values[index] = value == null ? 0.0f : value.floatValue();
        }
        return new EmbeddingVector(values);
    }

    private RagProviderException mapHttpException(RestClientResponseException exception) {
        HttpStatusCode statusCode = exception.getStatusCode();
        int status = statusCode.value();
        if (status == 401 || status == 403) {
            return providerException(RagProviderErrorCode.RAG_PROVIDER_UNAUTHORIZED, status);
        }
        if (status == 408) {
            return providerException(RagProviderErrorCode.RAG_PROVIDER_TIMEOUT, status);
        }
        if (status == 429) {
            return providerException(RagProviderErrorCode.RAG_PROVIDER_RATE_LIMITED, status);
        }
        if (status >= 400 && status < 500) {
            return providerException(RagProviderErrorCode.RAG_PROVIDER_BAD_REQUEST, status);
        }
        if (status >= 500) {
            return providerException(RagProviderErrorCode.RAG_PROVIDER_SERVER_ERROR, status);
        }
        return providerException(RagProviderErrorCode.RAG_PROVIDER_BAD_RESPONSE, status);
    }

    private RagProviderException providerException(RagProviderErrorCode code, int status) {
        return new RagProviderException(code, "RAG embedding provider HTTP error status=" + status);
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
