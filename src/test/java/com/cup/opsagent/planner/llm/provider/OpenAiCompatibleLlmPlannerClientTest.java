package com.cup.opsagent.planner.llm.provider;

import com.cup.opsagent.planner.llm.provider.dto.OpenAiChatMessage;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class OpenAiCompatibleLlmPlannerClientTest {

    @Test
    void shouldSendOpenAiCompatibleRequestAndExtractContent() {
        LlmProviderProperties properties = validProperties();
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        OpenAiCompatibleLlmPlannerClient client = client(properties, builder);

        server.expect(requestTo("https://api.example.com/v1/chat/completions"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer sk-validsecret123456789"))
                .andExpect(jsonPath("$.model").value("test-model"))
                .andExpect(jsonPath("$.messages[0].role").value("system"))
                .andExpect(jsonPath("$.messages[1].content").value("User request"))
                .andExpect(jsonPath("$.temperature").value(0.0))
                .andExpect(jsonPath("$.max_tokens").value(1000))
                .andExpect(jsonPath("$.response_format.type").value("json_object"))
                .andRespond(withSuccess("""
                        {
                          "choices": [
                            {"message": {"role": "assistant", "content": "{\\\"intentType\\\":\\\"DIAGNOSTIC\\\",\\\"summary\\\":\\\"ok\\\",\\\"steps\\\":[]}"}}
                          ]
                        }
                        """, MediaType.APPLICATION_JSON));

        String content = client.createPlanJson("检查系统负载");

        assertThat(content).contains("DIAGNOSTIC");
        server.verify();
    }

    @Test
    void shouldFailClosedBeforeHttpWhenProviderConfigIsMissing() {
        LlmProviderProperties properties = validProperties();
        properties.setApiKey(" ");
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        OpenAiCompatibleLlmPlannerClient client = client(properties, builder);

        assertThatThrownBy(() -> client.createPlanJson("检查系统负载"))
                .isInstanceOfSatisfying(LlmProviderException.class, exception ->
                        assertThat(exception.code()).isEqualTo(LlmProviderErrorCode.LLM_PROVIDER_MISCONFIGURED)
                );
        server.verify();
    }

    @Test
    void shouldOmitResponseFormatWhenDisabled() {
        LlmProviderProperties properties = validProperties();
        properties.setResponseFormatJsonObject(false);
        OpenAiCompatibleLlmPlannerClient client = client(properties, RestClient.builder());

        assertThat(client.buildRequest("检查系统负载").responseFormat()).isNull();
    }

    @Test
    void shouldSendConfiguredThinkingMode() {
        LlmProviderProperties properties = validProperties();
        properties.setThinkingMode("disabled");
        OpenAiCompatibleLlmPlannerClient client = client(properties, RestClient.builder());

        assertThat(client.buildRequest("检查系统负载").thinking()).containsEntry("type", "disabled");
    }

    @Test
    void shouldOmitThinkingWhenNotConfigured() {
        OpenAiCompatibleLlmPlannerClient client = client(validProperties(), RestClient.builder());

        assertThat(client.buildRequest("检查系统负载").thinking()).isNull();
    }

    @Test
    void shouldMapUnauthorizedHttpStatus() {
        assertHttpStatusMapsTo(HttpStatus.UNAUTHORIZED, LlmProviderErrorCode.LLM_PROVIDER_UNAUTHORIZED);
        assertHttpStatusMapsTo(HttpStatus.FORBIDDEN, LlmProviderErrorCode.LLM_PROVIDER_UNAUTHORIZED);
    }

    @Test
    void shouldMapRateLimitAndTimeoutHttpStatus() {
        assertHttpStatusMapsTo(HttpStatus.TOO_MANY_REQUESTS, LlmProviderErrorCode.LLM_PROVIDER_RATE_LIMITED);
        assertHttpStatusMapsTo(HttpStatus.REQUEST_TIMEOUT, LlmProviderErrorCode.LLM_PROVIDER_TIMEOUT);
    }

    @Test
    void shouldMapGenericClientAndServerHttpStatus() {
        assertHttpStatusMapsTo(HttpStatus.BAD_REQUEST, LlmProviderErrorCode.LLM_PROVIDER_BAD_REQUEST);
        assertHttpStatusMapsTo(HttpStatus.INTERNAL_SERVER_ERROR, LlmProviderErrorCode.LLM_PROVIDER_SERVER_ERROR);
    }

    @Test
    void shouldNotLeakHttpErrorBodyInExceptionMessage() {
        LlmProviderException exception = catchHttpStatus(HttpStatus.UNAUTHORIZED, "{\"error\":\"apiKey=sk-leakedsecret123456789\"}");

        assertThat(exception.getMessage()).contains("status=401");
        assertThat(exception.getMessage()).doesNotContain("sk-leakedsecret123456789");
        assertThat(exception.getCause()).isNull();
    }

    private void assertHttpStatusMapsTo(HttpStatus status, LlmProviderErrorCode expectedCode) {
        LlmProviderException exception = catchHttpStatus(status, "{\"error\":\"provider failure\"}");

        assertThat(exception.code()).isEqualTo(expectedCode);
        assertThat(exception.getMessage()).contains("status=" + status.value());
    }

    private LlmProviderException catchHttpStatus(HttpStatus status, String body) {
        LlmProviderProperties properties = validProperties();
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        OpenAiCompatibleLlmPlannerClient client = client(properties, builder);
        server.expect(requestTo("https://api.example.com/v1/chat/completions"))
                .andRespond(withStatus(status).contentType(MediaType.APPLICATION_JSON).body(body));

        try {
            client.createPlanJson("检查系统负载");
            throw new AssertionError("Expected LlmProviderException");
        } catch (LlmProviderException exception) {
            server.verify();
            return exception;
        }
    }

    private OpenAiCompatibleLlmPlannerClient client(LlmProviderProperties properties, RestClient.Builder builder) {
        LlmPromptFactory promptFactory = mock(LlmPromptFactory.class);
        when(promptFactory.createMessages("检查系统负载")).thenReturn(List.of(
                new OpenAiChatMessage("system", "Return JSON only"),
                new OpenAiChatMessage("user", "User request")
        ));
        return new OpenAiCompatibleLlmPlannerClient(
                properties,
                promptFactory,
                new OpenAiChatCompletionResponseExtractor(),
                builder
                        .baseUrl(properties.getBaseUrl())
                        .defaultHeader("Authorization", "Bearer " + properties.getApiKey())
                        .build()
        );
    }

    private LlmProviderProperties validProperties() {
        LlmProviderProperties properties = new LlmProviderProperties();
        properties.setProvider("openai-compatible");
        properties.setBaseUrl("https://api.example.com/v1");
        properties.setApiKey("sk-validsecret123456789");
        properties.setModel("test-model");
        properties.setTemperature(0.0);
        properties.setMaxOutputTokens(1000);
        return properties;
    }
}
