package com.cup.opsagent.rag;

import java.util.List;

public record RagAugmentationResult(
        KnowledgeQuery query,
        List<RetrievedKnowledge> retrievedKnowledge,
        String context,
        boolean hasKnowledge
) {
    public RagAugmentationResult {
        retrievedKnowledge = retrievedKnowledge == null ? List.of() : List.copyOf(retrievedKnowledge);
        context = context == null ? RagContextFactory.EMPTY_CONTEXT : context;
        hasKnowledge = !retrievedKnowledge.isEmpty();
    }
}
