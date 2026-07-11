package com.cup.opsagent.rag;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public class RagAugmentor {

    private final KnowledgeRetriever knowledgeRetriever;
    private final RagContextFactory ragContextFactory;
    private final RagProperties ragProperties;

    @Autowired
    public RagAugmentor(KnowledgeRetriever knowledgeRetriever, RagContextFactory ragContextFactory, RagProperties ragProperties) {
        this.knowledgeRetriever = knowledgeRetriever;
        this.ragContextFactory = ragContextFactory;
        this.ragProperties = ragProperties;
    }

    public RagAugmentor(KnowledgeRetriever knowledgeRetriever, RagContextFactory ragContextFactory) {
        this(knowledgeRetriever, ragContextFactory, new RagProperties());
    }

    public RagAugmentationResult augment(String userInput) {
        ragProperties.validate();
        return augment(new KnowledgeQuery(userInput, ragProperties.getMaxResults(), Map.of()));
    }

    public RagAugmentationResult augment(KnowledgeQuery query) {
        ragProperties.validate();
        KnowledgeQuery safeQuery = query == null
                ? new KnowledgeQuery("", ragProperties.getMaxResults(), Map.of())
                : query;
        List<RetrievedKnowledge> retrievedKnowledge = knowledgeRetriever.retrieve(safeQuery);
        String context = ragContextFactory.createContext(retrievedKnowledge);
        return new RagAugmentationResult(safeQuery, retrievedKnowledge, context, !retrievedKnowledge.isEmpty());
    }
}
