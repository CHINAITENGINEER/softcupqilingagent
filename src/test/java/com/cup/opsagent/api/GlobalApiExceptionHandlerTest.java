package com.cup.opsagent.api;

import com.cup.opsagent.rag.RagProviderErrorCode;
import com.cup.opsagent.rag.RagProviderException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;

import java.util.NoSuchElementException;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalApiExceptionHandlerTest {

    private final GlobalApiExceptionHandler handler = new GlobalApiExceptionHandler();

    @Test
    void shouldMapSecurityExceptionToForbidden() {
        ResponseEntity<ApiErrorResponse> response = handler.handleSecurityException(
                new SecurityException("actor is not allowed"),
                request("/api/approvals/approve")
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo("ACCESS_DENIED");
        assertThat(response.getBody().path()).isEqualTo("/api/approvals/approve");
    }

    @Test
    void shouldMapIllegalArgumentExceptionToBadRequest() {
        ResponseEntity<ApiErrorResponse> response = handler.handleBadRequest(
                new IllegalArgumentException("actorId must not be blank"),
                request("/api/approvals/reject")
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo("BAD_REQUEST");
        assertThat(response.getBody().message()).contains("actorId");
    }

    @Test
    void shouldMapIllegalStateExceptionToConflict() {
        ResponseEntity<ApiErrorResponse> response = handler.handleConflict(
                new IllegalStateException("approval is not pending"),
                request("/api/approvals/approve")
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo("STATE_CONFLICT");
    }

    @Test
    void shouldMapNoSuchElementExceptionToNotFound() {
        ResponseEntity<ApiErrorResponse> response = handler.handleNotFound(
                new NoSuchElementException(),
                request("/api/approvals/approve")
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo("RESOURCE_NOT_FOUND");
    }

    @Test
    void shouldMapRagProviderMisconfigurationToBadRequest() {
        ResponseEntity<ApiErrorResponse> response = handler.handleRagProviderException(
                new RagProviderException(RagProviderErrorCode.RAG_PROVIDER_MISCONFIGURED, "missing required RAG vector store provider config: milvusUri"),
                request("/api/rag/knowledge/ingest")
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo("RAG_PROVIDER_MISCONFIGURED");
        assertThat(response.getBody().message()).contains("milvusUri");
        assertThat(response.getBody().path()).isEqualTo("/api/rag/knowledge/ingest");
    }

    @Test
    void shouldMapRagProviderRateLimitToTooManyRequests() {
        ResponseEntity<ApiErrorResponse> response = handler.handleRagProviderException(
                new RagProviderException(RagProviderErrorCode.RAG_PROVIDER_RATE_LIMITED, "RAG embedding provider request failed with status 429"),
                request("/api/rag/knowledge/search")
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo("RAG_PROVIDER_RATE_LIMITED");
    }

    @Test
    void shouldMapRagProviderTimeoutToGatewayTimeout() {
        ResponseEntity<ApiErrorResponse> response = handler.handleRagProviderException(
                new RagProviderException(RagProviderErrorCode.RAG_PROVIDER_TIMEOUT, "RAG embedding provider request failed with status 408"),
                request("/api/rag/knowledge/search")
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.GATEWAY_TIMEOUT);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo("RAG_PROVIDER_TIMEOUT");
    }

    @Test
    void shouldMapRagProviderServerErrorToBadGatewayAndSanitizeSecrets() {
        ResponseEntity<ApiErrorResponse> response = handler.handleRagProviderException(
                new RagProviderException(
                        RagProviderErrorCode.RAG_PROVIDER_SERVER_ERROR,
                        "Milvus vector search failed token=secret-token Authorization: Bearer sk-validsecret123456789"
                ),
                request("/api/rag/knowledge/search")
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo("RAG_PROVIDER_SERVER_ERROR");
        assertThat(response.getBody().message()).doesNotContain("secret-token");
        assertThat(response.getBody().message()).doesNotContain("sk-validsecret123456789");
    }

    private MockHttpServletRequest request(String path) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI(path);
        return request;
    }
}
