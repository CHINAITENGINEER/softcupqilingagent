package com.cup.opsagent.rag;

import com.cup.opsagent.audit.AuditEvent;
import com.cup.opsagent.audit.AuditEventType;
import com.cup.opsagent.audit.AuditLogService;
import com.cup.opsagent.audit.AuditTrace;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AuditedRagAugmentorTest {

    @Test
    void shouldAuditRetrievalStartedAndCompleted() {
        AuditLogService auditLogService = new AuditLogService();
        AuditTrace trace = auditLogService.startTrace("nginx 502");
        AuditedRagAugmentor augmentor = new AuditedRagAugmentor(
                new RagAugmentor(new StubKnowledgeRetriever(List.of(knowledge("runbook-nginx")), false), new RagContextFactory()),
                auditLogService
        );

        RagAugmentationResult result = augmentor.augment(trace.getTraceId(), "nginx 502");

        assertThat(result.hasKnowledge()).isTrue();
        List<AuditEvent> events = auditLogService.findTrace(trace.getTraceId()).orElseThrow().getEvents();
        assertThat(events).extracting(AuditEvent::eventType).containsSubsequence(
                AuditEventType.RECEIVE_REQUEST,
                AuditEventType.RAG_RETRIEVAL_STARTED,
                AuditEventType.RAG_RETRIEVAL_COMPLETED
        );
        AuditEvent completed = event(events, AuditEventType.RAG_RETRIEVAL_COMPLETED);
        assertThat(completed.success()).isTrue();
        assertThat(completed.payload())
                .containsEntry("resultCount", 1)
                .containsKey("queryHash")
                .containsKey("durationMs");
        assertThat(completed.payload().get("topKnowledgeIds")).isEqualTo(List.of("runbook-nginx"));
        assertThat(completed.payload()).doesNotContainKey("userInput");
        assertThat(completed.payload()).doesNotContainKey("context");
    }

    @Test
    void shouldAuditZeroResults() {
        AuditLogService auditLogService = new AuditLogService();
        AuditTrace trace = auditLogService.startTrace("unknown issue");
        AuditedRagAugmentor augmentor = new AuditedRagAugmentor(
                new RagAugmentor(new StubKnowledgeRetriever(List.of(), false), new RagContextFactory()),
                auditLogService
        );

        RagAugmentationResult result = augmentor.augment(trace.getTraceId(), "unknown issue");

        assertThat(result.hasKnowledge()).isFalse();
        AuditEvent completed = event(auditLogService.findTrace(trace.getTraceId()).orElseThrow().getEvents(), AuditEventType.RAG_RETRIEVAL_COMPLETED);
        assertThat(completed.payload()).containsEntry("resultCount", 0);
        assertThat(completed.payload().get("topKnowledgeIds")).isEqualTo(List.of());
    }

    @Test
    void shouldAuditRetrievalFailureAndRethrow() {
        AuditLogService auditLogService = new AuditLogService();
        AuditTrace trace = auditLogService.startTrace("nginx 502");
        AuditedRagAugmentor augmentor = new AuditedRagAugmentor(
                new RagAugmentor(new StubKnowledgeRetriever(List.of(), true), new RagContextFactory()),
                auditLogService
        );

        assertThatThrownBy(() -> augmentor.augment(trace.getTraceId(), "nginx 502"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("retriever unavailable");

        List<AuditEvent> events = auditLogService.findTrace(trace.getTraceId()).orElseThrow().getEvents();
        assertThat(events).extracting(AuditEvent::eventType).containsSubsequence(
                AuditEventType.RAG_RETRIEVAL_STARTED,
                AuditEventType.RAG_RETRIEVAL_FAILED
        );
        AuditEvent failed = event(events, AuditEventType.RAG_RETRIEVAL_FAILED);
        assertThat(failed.success()).isFalse();
        assertThat(failed.errorMessage()).contains("retriever unavailable");
        assertThat(failed.payload())
                .containsEntry("errorType", "IllegalStateException")
                .containsKey("queryHash")
                .containsKey("durationMs");
        assertThat(failed.payload()).doesNotContainKey("userInput");
    }

    @Test
    void shouldUseQueryHashInsteadOfRawUserInput() {
        AuditLogService auditLogService = new AuditLogService();
        AuditTrace trace = auditLogService.startTrace("nginx apiKey=sk-secret");
        AuditedRagAugmentor augmentor = new AuditedRagAugmentor(
                new RagAugmentor(new StubKnowledgeRetriever(List.of(), false), new RagContextFactory()),
                auditLogService
        );

        augmentor.augment(trace.getTraceId(), "nginx apiKey=sk-secret");

        List<AuditEvent> events = auditLogService.findTrace(trace.getTraceId()).orElseThrow().getEvents().stream()
                .filter(event -> event.eventType() == AuditEventType.RAG_RETRIEVAL_STARTED || event.eventType() == AuditEventType.RAG_RETRIEVAL_COMPLETED)
                .toList();
        assertThat(events).isNotEmpty();
        for (AuditEvent event : events) {
            assertThat(event.payload().toString()).doesNotContain("sk-secret");
            assertThat(event.payload()).containsKey("queryHash");
        }
    }

    @Test
    void shouldUseConfiguredMaxResultsForStringInputAndAuditPayload() {
        AuditLogService auditLogService = new AuditLogService();
        AuditTrace trace = auditLogService.startTrace("nginx 502");
        RagProperties properties = new RagProperties();
        properties.setMaxResults(3);
        StubKnowledgeRetriever retriever = new StubKnowledgeRetriever(List.of(), false);
        AuditedRagAugmentor augmentor = new AuditedRagAugmentor(
                new RagAugmentor(retriever, new RagContextFactory(), properties),
                auditLogService,
                properties
        );

        RagAugmentationResult result = augmentor.augment(trace.getTraceId(), "nginx 502");

        assertThat(result.query().maxResults()).isEqualTo(3);
        assertThat(retriever.seenQueries()).hasSize(1);
        assertThat(retriever.seenQueries().getFirst().maxResults()).isEqualTo(3);
        AuditEvent started = event(auditLogService.findTrace(trace.getTraceId()).orElseThrow().getEvents(), AuditEventType.RAG_RETRIEVAL_STARTED);
        assertThat(started.payload()).containsEntry("maxResults", 3);
    }

    private AuditEvent event(List<AuditEvent> events, AuditEventType eventType) {
        return events.stream()
                .filter(event -> event.eventType() == eventType)
                .findFirst()
                .orElseThrow();
    }

    private RetrievedKnowledge knowledge(String id) {
        return new RetrievedKnowledge(
                id,
                "runbook",
                "Nginx troubleshooting",
                "Check nginx status before restart.",
                0.93,
                Instant.parse("2026-07-01T00:00:00Z"),
                Map.of("owner", "safeops")
        );
    }

    private static class StubKnowledgeRetriever implements KnowledgeRetriever {
        private final List<RetrievedKnowledge> retrievedKnowledge;
        private final boolean fail;
        private final List<KnowledgeQuery> seenQueries = new ArrayList<>();

        StubKnowledgeRetriever(List<RetrievedKnowledge> retrievedKnowledge, boolean fail) {
            this.retrievedKnowledge = retrievedKnowledge;
            this.fail = fail;
        }

        @Override
        public List<RetrievedKnowledge> retrieve(KnowledgeQuery query) {
            seenQueries.add(query);
            if (fail) {
                throw new IllegalStateException("retriever unavailable");
            }
            return retrievedKnowledge;
        }

        List<KnowledgeQuery> seenQueries() {
            return seenQueries;
        }
    }
}
