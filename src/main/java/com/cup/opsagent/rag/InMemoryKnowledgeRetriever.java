package com.cup.opsagent.rag;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class InMemoryKnowledgeRetriever implements KnowledgeRetriever {

    public static final int DEFAULT_SNIPPET_MAX_LENGTH = 500;

    private final List<KnowledgeDocument> documents;
    private final int snippetMaxLength;

    public InMemoryKnowledgeRetriever() {
        this(List.of(), DEFAULT_SNIPPET_MAX_LENGTH);
    }

    public InMemoryKnowledgeRetriever(List<KnowledgeDocument> documents) {
        this(documents, DEFAULT_SNIPPET_MAX_LENGTH);
    }

    public InMemoryKnowledgeRetriever(List<KnowledgeDocument> documents, int snippetMaxLength) {
        this.documents = documents == null ? List.of() : List.copyOf(documents);
        this.snippetMaxLength = snippetMaxLength <= 0 ? DEFAULT_SNIPPET_MAX_LENGTH : snippetMaxLength;
    }

    @Override
    public List<RetrievedKnowledge> retrieve(KnowledgeQuery query) {
        if (query == null || query.text().isBlank()) {
            return List.of();
        }
        Set<String> terms = queryTerms(query.text());
        if (terms.isEmpty()) {
            return List.of();
        }

        List<ScoredDocument> scoredDocuments = new ArrayList<>();
        for (KnowledgeDocument document : documents) {
            double score = score(document, terms);
            if (score > 0) {
                scoredDocuments.add(new ScoredDocument(document, score));
            }
        }

        return scoredDocuments.stream()
                .sorted(Comparator.comparingDouble(ScoredDocument::score).reversed()
                        .thenComparing(scored -> scored.document().knowledgeId()))
                .limit(query.maxResults())
                .map(scored -> toRetrievedKnowledge(scored.document(), scored.score()))
                .toList();
    }

    private RetrievedKnowledge toRetrievedKnowledge(KnowledgeDocument document, double score) {
        return new RetrievedKnowledge(
                document.knowledgeId(),
                document.sourceType(),
                document.title(),
                snippet(document.content()),
                score,
                document.lastUpdatedAt(),
                document.metadata()
        );
    }

    private double score(KnowledgeDocument document, Set<String> terms) {
        String haystack = normalize(document.title() + " " + document.content());
        double score = 0;
        for (String term : terms) {
            if (haystack.contains(term)) {
                score += titleContains(document, term) ? 2.0 : 1.0;
            }
        }
        return score;
    }

    private boolean titleContains(KnowledgeDocument document, String term) {
        return normalize(document.title()).contains(term);
    }

    private Set<String> queryTerms(String text) {
        String normalized = normalize(text);
        String[] parts = normalized.split("[^a-z0-9\\u4e00-\\u9fa5]+");
        Set<String> terms = new HashSet<>();
        for (String part : parts) {
            if (!part.isBlank()) {
                terms.add(part);
            }
        }
        return terms;
    }

    private String snippet(String content) {
        String safeContent = content == null ? "" : content.strip();
        if (safeContent.length() <= snippetMaxLength) {
            return safeContent;
        }
        return safeContent.substring(0, snippetMaxLength) + "...";
    }

    private String normalize(String text) {
        return text == null ? "" : text.toLowerCase(Locale.ROOT);
    }

    private record ScoredDocument(KnowledgeDocument document, double score) {
    }
}
