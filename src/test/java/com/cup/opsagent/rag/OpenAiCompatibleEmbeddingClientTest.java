package com.cup.opsagent.rag;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class OpenAiCompatibleEmbeddingClientTest {

    @Test
    void shouldSendOpenAiCompatibleEmbeddingRequestAndReturnVector() {
        RagProperties properties = validProperties();
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        OpenAiCompatibleEmbeddingClient client = new OpenAiCompatibleEmbeddingClient(properties, builder);

        server.expect(requestTo("https://api.example.com/v1/embeddings"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer sk-validsecret123456789"))
                .andExpect(jsonPath("$.model").value("text-embedding-test"))
                .andExpect(jsonPath("$.input").value("nginx upstream timeout"))
                .andRespond(withSuccess("""
                        {
                          "object": "list",
                          "model": "text-embedding-test",
                          "data": [
                            {
                              "object": "embedding",
                              "index": 0,
                              "embedding": [0.1, -0.2, 0.3]
                            }
                          ]
                        }
                        """, MediaType.APPLICATION_JSON));

        EmbeddingVector vector = client.embed("nginx upstream timeout");

        assertThat(vector.values()).containsExactly(0.1f, -0.2f, 0.3f);
        server.verify();
    }

    @Test
    void shouldFailClosedBeforeHttpWhenProviderConfigIsMissing() {
        RagProperties properties = validProperties();
        properties.setEmbeddingApiKey(" ");
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        OpenAiCompatibleEmbeddingClient client = new OpenAiCompatibleEmbeddingClient(properties, builder);

        assertThatThrownBy(() -> client.embed("nginx"))
                .isInstanceOfSatisfying(RagProviderException.class, exception ->
                        assertThat(exception.code()).isEqualTo(RagProviderErrorCode.RAG_PROVIDER_MISCONFIGURED)
                );
        server.verify();
    }

    @Test
    void shouldMapUnauthorizedHttpStatus() {
        assertHttpStatusMapsTo(HttpStatus.UNAUTHORIZED, RagProviderErrorCode.RAG_PROVIDER_UNAUTHORIZED);
        assertHttpStatusMapsTo(HttpStatus.FORBIDDEN, RagProviderErrorCode.RAG_PROVIDER_UNAUTHORIZED);
    }

    @Test
    void shouldMapRateLimitAndTimeoutHttpStatus() {
        assertHttpStatusMapsTo(HttpStatus.TOO_MANY_REQUESTS, RagProviderErrorCode.RAG_PROVIDER_RATE_LIMITED);
        assertHttpStatusMapsTo(HttpStatus.REQUEST_TIMEOUT, RagProviderErrorCode.RAG_PROVIDER_TIMEOUT);
    }

    @Test
    void shouldMapGenericClientAndServerHttpStatus() {
        assertHttpStatusMapsTo(HttpStatus.BAD_REQUEST, RagProviderErrorCode.RAG_PROVIDER_BAD_REQUEST);
        assertHttpStatusMapsTo(HttpStatus.INTERNAL_SERVER_ERROR, RagProviderErrorCode.RAG_PROVIDER_SERVER_ERROR);
    }

    @Test
    void shouldRejectEmptyEmbeddingResponse() {
        RagProperties properties = validProperties();
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        OpenAiCompatibleEmbeddingClient client = new OpenAiCompatibleEmbeddingClient(properties, builder);
        server.expect(requestTo("https://api.example.com/v1/embeddings"))
                .andRespond(withSuccess("{\"data\":[]}", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.embed("nginx"))
                .isInstanceOfSatisfying(RagProviderException.class, exception ->
                        assertThat(exception.code()).isEqualTo(RagProviderErrorCode.RAG_PROVIDER_BAD_RESPONSE)
                );
        server.verify();
    }

    @Test
    void shouldNotLeakHttpErrorBodyInExceptionMessage() {
        RagProviderException exception = catchHttpStatus(HttpStatus.UNAUTHORIZED, "{\"error\":\"apiKey=sk-leakedsecret123456789\"}");

        assertThat(exception.getMessage()).contains("status=401");
        assertThat(exception.getMessage()).doesNotContain("sk-leakedsecret123456789");
        assertThat(exception.getCause()).isNull();
    }

    private void assertHttpStatusMapsTo(HttpStatus status, RagProviderErrorCode expectedCode) {
        RagProviderException exception = catchHttpStatus(status, "{\"error\":\"provider failure\"}");

        assertThat(exception.code()).isEqualTo(expectedCode);
        assertThat(exception.getMessage()).contains("status=" + status.value());
    }

    private RagProviderException catchHttpStatus(HttpStatus status, String body) {
        RagProperties properties = validProperties();
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        OpenAiCompatibleEmbeddingClient client = new OpenAiCompatibleEmbeddingClient(properties, builder);
        server.expect(requestTo("https://api.example.com/v1/embeddings"))
                .andRespond(withStatus(status).contentType(MediaType.APPLICATION_JSON).body(body));

        try {
            client.embed("nginx");
            throw new AssertionError("Expected RagProviderException");
        } catch (RagProviderException exception) {
            server.verify();
            return exception;
        }
    }

    private RagProperties validProperties() {
        RagProperties properties = new RagProperties();
        properties.setEmbeddingProvider("openai");
        properties.setEmbeddingBaseUrl("https://api.example.com/v1");
        properties.setEmbeddingApiKey("sk-validsecret123456789");
        properties.setEmbeddingModel("text-embedding-test");
        return properties;
    }
}
