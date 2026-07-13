package com.cup.opsagent.planner.llm.provider;

import com.cup.opsagent.planner.llm.LlmPlannerClient;
import com.cup.opsagent.planner.llm.provider.dto.OpenAiChatCompletionRequest;
import com.cup.opsagent.planner.llm.provider.dto.OpenAiChatCompletionResponse;
import com.cup.opsagent.planner.llm.provider.dto.OpenAiResponseFormat;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.util.Map;

@Component
public class OpenAiCompatibleLlmPlannerClient implements LlmPlannerClient {

    private static final String CHAT_COMPLETIONS_PATH = "/chat/completions";

    private final LlmProviderProperties properties;
    private final LlmPromptFactory promptFactory;
    private final OpenAiChatCompletionResponseExtractor responseExtractor;
    private final RestClient restClient;

    @Autowired
    public OpenAiCompatibleLlmPlannerClient(
            LlmProviderProperties properties,
            LlmPromptFactory promptFactory,
            OpenAiChatCompletionResponseExtractor responseExtractor,
            RestClient.Builder restClientBuilder
    ) {
        this(
                properties,
                promptFactory,
                responseExtractor,
                restClientBuilder.clone()
                        .requestFactory(requestFactory(properties))
                        .baseUrl(trimTrailingSlash(properties.getBaseUrl()))
                        .defaultHeader("Authorization", "Bearer " + properties.getApiKey())
                        .build()
        );
    }

    OpenAiCompatibleLlmPlannerClient(
            LlmProviderProperties properties,
            LlmPromptFactory promptFactory,
            OpenAiChatCompletionResponseExtractor responseExtractor,
            RestClient restClient
    ) {
        this.properties = properties;
        this.promptFactory = promptFactory;
        this.responseExtractor = responseExtractor;
        this.restClient = restClient;
    }

    @Override
    public String createPlanJson(String userInput) {
        return createPlanJson(userInput, null);
    }

    @Override
    public String createPlanJson(String userInput, String ragContext) {
        properties.validateForLlmMode();
        OpenAiChatCompletionResponse response = execute(buildRequest(userInput, ragContext));
        return responseExtractor.extractContent(response);
    }

    OpenAiChatCompletionRequest buildRequest(String userInput) {
        return buildRequest(userInput, null);
    }

    OpenAiChatCompletionRequest buildRequest(String userInput, String ragContext) {
        return new OpenAiChatCompletionRequest(
                properties.getModel(),
                ragContext == null ? promptFactory.createMessages(userInput) : promptFactory.createMessages(userInput, ragContext),
                properties.getTemperature(),
                properties.getMaxOutputTokens(),
                properties.isResponseFormatJsonObject() ? OpenAiResponseFormat.jsonObject() : null,
                properties.normalizedThinkingMode().isEmpty()
                        ? null
                        : Map.of("type", properties.normalizedThinkingMode())
        );
    }

    private OpenAiChatCompletionResponse execute(OpenAiChatCompletionRequest request) {
        try {
            return restClient.post()
                    .uri(CHAT_COMPLETIONS_PATH)
                    .body(request)
                    .retrieve()
                    .body(OpenAiChatCompletionResponse.class);
        } catch (RestClientResponseException exception) {
            throw mapHttpException(exception);
        } catch (ResourceAccessException exception) {
            throw new LlmProviderException(LlmProviderErrorCode.LLM_PROVIDER_TIMEOUT, "LLM provider request failed or timed out", exception);
        } catch (RestClientException exception) {
            throw new LlmProviderException(LlmProviderErrorCode.LLM_PROVIDER_SERVER_ERROR, "LLM provider request failed", exception);
        }
    }

    private LlmProviderException mapHttpException(RestClientResponseException exception) {
        HttpStatusCode statusCode = exception.getStatusCode();
        int status = statusCode.value();
        if (status == 401 || status == 403) {
            return providerException(LlmProviderErrorCode.LLM_PROVIDER_UNAUTHORIZED, status, exception);
        }
        if (status == 408) {
            return providerException(LlmProviderErrorCode.LLM_PROVIDER_TIMEOUT, status, exception);
        }
        if (status == 429) {
            return providerException(LlmProviderErrorCode.LLM_PROVIDER_RATE_LIMITED, status, exception);
        }
        if (status >= 400 && status < 500) {
            return providerException(LlmProviderErrorCode.LLM_PROVIDER_BAD_REQUEST, status, exception);
        }
        if (status >= 500) {
            return providerException(LlmProviderErrorCode.LLM_PROVIDER_SERVER_ERROR, status, exception);
        }
        return providerException(LlmProviderErrorCode.LLM_PROVIDER_BAD_RESPONSE, status, exception);
    }

    private LlmProviderException providerException(LlmProviderErrorCode code, int status, RestClientResponseException cause) {
        return new LlmProviderException(code, "LLM provider HTTP error status=" + status);
    }

    private static SimpleClientHttpRequestFactory requestFactory(LlmProviderProperties properties) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(properties.getConnectTimeoutMs());
        requestFactory.setReadTimeout(properties.getReadTimeoutMs());
        return requestFactory;
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
