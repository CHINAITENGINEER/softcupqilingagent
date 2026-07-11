package com.cup.opsagent.rag;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.web.client.RestClient;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class RagConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(TestConfiguration.class);

    @Test
    void shouldCreateRagContextFactoryFromProperties() {
        contextRunner
                .withPropertyValues("ops-agent.rag.context-max-length=120")
                .run(context -> {
                    assertThat(context).hasSingleBean(RagContextFactory.class);
                    RagContextFactory factory = context.getBean(RagContextFactory.class);
                    String ragContext = factory.createContext(List.of(knowledge("doc-1", "nginx ".repeat(100))));

                    assertThat(ragContext).endsWith("[TRUNCATED]");
                    assertThat(ragContext.length()).isLessThanOrEqualTo(132);
                });
    }

    @Test
    void shouldCreateKeywordKnowledgeRetrieverByDefault() {
        contextRunner
                .withPropertyValues("ops-agent.rag.snippet-max-length=120")
                .run(context -> {
                    assertThat(context).hasSingleBean(KnowledgeRetriever.class);
                    KnowledgeRetriever retriever = context.getBean(KnowledgeRetriever.class);

                    assertThat(retriever).isInstanceOf(InMemoryKnowledgeRetriever.class);
                    assertThat(ReflectionTestUtils.getField(retriever, "snippetMaxLength")).isEqualTo(120);
                });
    }

    @Test
    void shouldCreateVectorKnowledgeRetrieverWhenConfigured() {
        contextRunner
                .withPropertyValues("ops-agent.rag.retriever-mode=vector")
                .run(context -> {
                    assertThat(context).hasSingleBean(KnowledgeRetriever.class);
                    assertThat(context.getBean(KnowledgeRetriever.class)).isInstanceOf(VectorKnowledgeRetriever.class);
                });
    }

    @Test
    void vectorKnowledgeRetrieverShouldSearchIngestedKnowledgeFromSameVectorStore() {
        contextRunner
                .withPropertyValues("ops-agent.rag.retriever-mode=vector")
                .run(context -> {
                    KnowledgeIngestionService ingestionService = context.getBean(KnowledgeIngestionService.class);
                    KnowledgeRetriever retriever = context.getBean(KnowledgeRetriever.class);

                    ingestionService.ingest(List.of(new KnowledgeDocument(
                            "runbook-nginx-config",
                            "runbook",
                            "Nginx troubleshooting",
                            "nginx upstream timeout troubleshooting",
                            Instant.parse("2026-07-01T00:00:00Z"),
                            Map.of("service", "nginx")
                    )));

                    List<RetrievedKnowledge> results = retriever.retrieve(KnowledgeQuery.of("nginx upstream"));

                    assertThat(results).isNotEmpty();
                    assertThat(results.getFirst().metadata()).containsEntry("parentKnowledgeId", "runbook-nginx-config");
                });
    }

    @Test
    void shouldFailClosedWhenRetrieverModeIsInvalid() {
        contextRunner
                .withPropertyValues("ops-agent.rag.retriever-mode=milvus")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void shouldFailClosedWhenEmbeddingProviderIsInvalid() {
        contextRunner
                .withPropertyValues("ops-agent.rag.embedding-provider=bad-provider")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void shouldFailClosedWhenOpenAiEmbeddingProviderConfigIsMissing() {
        contextRunner
                .withPropertyValues("ops-agent.rag.embedding-provider=openai")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void shouldFailClosedWhenVectorStoreProviderIsInvalid() {
        contextRunner
                .withPropertyValues("ops-agent.rag.vector-store-provider=bad-vector-store")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void shouldFailClosedWhenMilvusVectorStoreProviderConfigIsMissing() {
        contextRunner
                .withPropertyValues("ops-agent.rag.vector-store-provider=milvus")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void shouldCreateMilvusVectorStoreClientWhenConfigured() {
        contextRunner
                .withPropertyValues(
                        "ops-agent.rag.vector-store-provider=milvus",
                        "ops-agent.rag.milvus-uri=http://localhost:19530",
                        "ops-agent.rag.milvus-collection=safeops_knowledge",
                        "ops-agent.rag.milvus-dimension=1536"
                )
                .run(context -> assertThat(context.getBean(VectorStoreClient.class)).isInstanceOf(MilvusVectorStoreClient.class));
    }

    @Test
    void shouldFailClosedWhenRagConfigIsInvalid() {
        contextRunner
                .withPropertyValues("ops-agent.rag.context-max-length=0")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void shouldBindDefaultRagProperties() {
        contextRunner.run(context -> {
            RagProperties properties = context.getBean(RagProperties.class);

            assertThat(properties.isEnabled()).isFalse();
            assertThat(properties.getRetrieverMode()).isEqualTo("keyword");
            assertThat(properties.retrieverMode()).isEqualTo(RagRetrieverMode.KEYWORD);
            assertThat(properties.getEmbeddingProvider()).isEqualTo("deterministic");
            assertThat(properties.embeddingProvider()).isEqualTo(RagEmbeddingProvider.DETERMINISTIC);
            assertThat(properties.getEmbeddingBaseUrl()).isEmpty();
            assertThat(properties.getEmbeddingApiKey()).isEmpty();
            assertThat(properties.getEmbeddingModel()).isEmpty();
            assertThat(properties.getVectorStoreProvider()).isEqualTo("in-memory");
            assertThat(properties.vectorStoreProvider()).isEqualTo(RagVectorStoreProvider.IN_MEMORY);
            assertThat(properties.getMilvusUri()).isEmpty();
            assertThat(properties.getMilvusToken()).isEmpty();
            assertThat(properties.getMilvusCollection()).isEmpty();
            assertThat(properties.getMilvusDimension()).isZero();
            assertThat(properties.isMilvusAutoCreateCollection()).isFalse();
            assertThat(properties.isMilvusAutoLoadCollection()).isTrue();
            assertThat(properties.getMaxResults()).isEqualTo(RagProperties.DEFAULT_MAX_RESULTS);
            assertThat(properties.getContextMaxLength()).isEqualTo(RagProperties.DEFAULT_CONTEXT_MAX_LENGTH);
            assertThat(properties.getSnippetMaxLength()).isEqualTo(RagProperties.DEFAULT_SNIPPET_MAX_LENGTH);
        });
    }

    @Test
    void shouldCreateKnowledgeIngestionBeansFromDefaultProviders() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(EmbeddingClient.class);
            assertThat(context).hasSingleBean(VectorStoreClient.class);
            assertThat(context).hasSingleBean(KnowledgeIngestionService.class);
            assertThat(context.getBean(EmbeddingClient.class)).isInstanceOf(DeterministicEmbeddingClient.class);
            assertThat(context.getBean(VectorStoreClient.class)).isInstanceOf(InMemoryVectorStoreClient.class);
        });
    }

    @Test
    void shouldCreateKnowledgeIngestionBeansFromExplicitLocalProviders() {
        contextRunner
                .withPropertyValues(
                        "ops-agent.rag.embedding-provider=deterministic",
                        "ops-agent.rag.vector-store-provider=in-memory"
                )
                .run(context -> {
                    assertThat(context.getBean(EmbeddingClient.class)).isInstanceOf(DeterministicEmbeddingClient.class);
                    assertThat(context.getBean(VectorStoreClient.class)).isInstanceOf(InMemoryVectorStoreClient.class);
                });
    }

    @Test
    void shouldCreateOpenAiEmbeddingClientWhenConfigured() {
        contextRunner
                .withPropertyValues(
                        "ops-agent.rag.embedding-provider=openai",
                        "ops-agent.rag.embedding-base-url=https://api.example.com/v1",
                        "ops-agent.rag.embedding-api-key=sk-validsecret123456789",
                        "ops-agent.rag.embedding-model=text-embedding-test"
                )
                .run(context -> assertThat(context.getBean(EmbeddingClient.class)).isInstanceOf(OpenAiCompatibleEmbeddingClient.class));
    }

    @Configuration
    @EnableConfigurationProperties(RagProperties.class)
    @Import(RagConfiguration.class)
    static class TestConfiguration {

        @Bean
        RestClient.Builder restClientBuilder() {
            return RestClient.builder();
        }
    }

    private RetrievedKnowledge knowledge(String id, String snippet) {
        return new RetrievedKnowledge(
                id,
                "runbook",
                "Nginx troubleshooting",
                snippet,
                0.91,
                Instant.parse("2026-07-01T00:00:00Z"),
                Map.of()
        );
    }
}
