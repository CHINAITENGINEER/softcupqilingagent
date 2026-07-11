package com.cup.opsagent.api;

import com.cup.opsagent.audit.AuditLogService;
import com.cup.opsagent.auth.ActorResolver;
import com.cup.opsagent.rag.KnowledgeIngestionService;
import com.cup.opsagent.rag.KnowledgeQuery;
import com.cup.opsagent.rag.KnowledgeRetriever;
import com.cup.opsagent.rag.RagProviderErrorCode;
import com.cup.opsagent.rag.RagProviderException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(RagKnowledgeController.class)
class RagProviderExceptionWebTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private KnowledgeIngestionService knowledgeIngestionService;

    @MockBean
    private KnowledgeRetriever knowledgeRetriever;

    @MockBean
    private AuditLogService auditLogService;

    @MockBean
    private ActorResolver actorResolver;

    @Test
    void shouldReturnSafeProviderErrorResponseFromSearchApi() throws Exception {
        when(knowledgeRetriever.retrieve(any(KnowledgeQuery.class))).thenThrow(new RagProviderException(
                RagProviderErrorCode.RAG_PROVIDER_SERVER_ERROR,
                "Milvus vector search failed token=secret-token Authorization: Bearer sk-validsecret123456789"
        ));

        mockMvc.perform(get("/api/rag/knowledge/search")
                        .param("q", "redis memory")
                        .param("maxResults", "3"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.code").value("RAG_PROVIDER_SERVER_ERROR"))
                .andExpect(jsonPath("$.status").value(502))
                .andExpect(jsonPath("$.path").value("/api/rag/knowledge/search"))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("secret-token"))))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("sk-validsecret123456789"))));
    }
}
