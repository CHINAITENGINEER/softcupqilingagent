package com.cup.opsagent.rag;

import io.milvus.v2.client.ConnectConfig;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

import java.util.List;

@Configuration
public class RagConfiguration {

    @Bean
    public RagContextFactory ragContextFactory(RagProperties ragProperties) {
        ragProperties.validate();
        return new RagContextFactory(ragProperties.getContextMaxLength());
    }

    @Bean
    public KnowledgeRetriever knowledgeRetriever(
            RagProperties ragProperties,
            EmbeddingClient embeddingClient,
            VectorStoreClient vectorStoreClient
    ) {
        ragProperties.validate();
        return switch (ragProperties.retrieverMode()) {
            case KEYWORD -> new InMemoryKnowledgeRetriever(List.of(), ragProperties.getSnippetMaxLength());
            case VECTOR -> new VectorKnowledgeRetriever(embeddingClient, vectorStoreClient, ragProperties.getSnippetMaxLength());
        };
    }

    @Bean
    public EmbeddingClient embeddingClient(RagProperties ragProperties, RestClient.Builder restClientBuilder) {
        ragProperties.validateEmbeddingProviderConfig();
        return switch (ragProperties.embeddingProvider()) {
            case DETERMINISTIC -> new DeterministicEmbeddingClient();
            case OPENAI -> new OpenAiCompatibleEmbeddingClient(ragProperties, restClientBuilder);
        };
    }

    @Bean
    public VectorStoreClient vectorStoreClient(RagProperties ragProperties) {
        ragProperties.validateVectorStoreProviderConfig();
        return switch (ragProperties.vectorStoreProvider()) {
            case IN_MEMORY -> new InMemoryVectorStoreClient();
            case MILVUS -> new MilvusVectorStoreClient(ragProperties, new MilvusSdkVectorClientOperations(
                    ConnectConfig.builder()
                            .uri(ragProperties.getMilvusUri())
                            .token(ragProperties.getMilvusToken())
                            .build()
            ));
        };
    }

    @Bean
    public KnowledgeIngestionService knowledgeIngestionService(EmbeddingClient embeddingClient, VectorStoreClient vectorStoreClient) {
        return new KnowledgeIngestionService(embeddingClient, vectorStoreClient);
    }
}
