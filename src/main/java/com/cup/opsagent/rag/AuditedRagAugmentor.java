package com.cup.opsagent.rag;

import com.cup.opsagent.audit.AuditEvent;
import com.cup.opsagent.audit.AuditEventType;
import com.cup.opsagent.audit.AuditLogService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class AuditedRagAugmentor {

    private final RagAugmentor ragAugmentor;
    private final AuditLogService auditLogService;
    private final RagProperties ragProperties;

    @Autowired
    public AuditedRagAugmentor(RagAugmentor ragAugmentor, AuditLogService auditLogService, RagProperties ragProperties) {
        this.ragAugmentor = ragAugmentor;
        this.auditLogService = auditLogService;
        this.ragProperties = ragProperties;
    }

    public AuditedRagAugmentor(RagAugmentor ragAugmentor, AuditLogService auditLogService) {
        this(ragAugmentor, auditLogService, new RagProperties());
    }

    public RagAugmentationResult augment(String traceId, String userInput) {
        ragProperties.validate();
        return augment(traceId, new KnowledgeQuery(userInput, ragProperties.getMaxResults(), Map.of()));
    }

    public RagAugmentationResult augment(String traceId, KnowledgeQuery query) {
        ragProperties.validate();
        KnowledgeQuery safeQuery = query == null ? new KnowledgeQuery("", ragProperties.getMaxResults(), Map.of()) : query;
        Instant startedAt = Instant.now();
        auditLogService.append(traceId, AuditEvent.success(traceId, AuditEventType.RAG_RETRIEVAL_STARTED, Map.of(
                "queryHash", queryHash(safeQuery),
                "maxResults", safeQuery.maxResults()
        )));
        try {
            RagAugmentationResult result = ragAugmentor.augment(safeQuery);
            auditLogService.append(traceId, AuditEvent.success(traceId, AuditEventType.RAG_RETRIEVAL_COMPLETED, completedPayload(
                    safeQuery,
                    result,
                    durationMs(startedAt)
            )));
            return result;
        } catch (RuntimeException exception) {
            auditLogService.append(traceId, AuditEvent.failure(traceId, AuditEventType.RAG_RETRIEVAL_FAILED, failurePayload(
                    safeQuery,
                    durationMs(startedAt),
                    exception
            ), safeErrorMessage(exception)));
            throw exception;
        }
    }

    private Map<String, Object> completedPayload(KnowledgeQuery query, RagAugmentationResult result, long durationMs) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("queryHash", queryHash(query));
        payload.put("resultCount", result.retrievedKnowledge().size());
        payload.put("topKnowledgeIds", result.retrievedKnowledge().stream().map(RetrievedKnowledge::knowledgeId).limit(5).toList());
        payload.put("topScores", result.retrievedKnowledge().stream().map(RetrievedKnowledge::score).limit(5).toList());
        payload.put("durationMs", durationMs);
        return Map.copyOf(payload);
    }

    private Map<String, Object> failurePayload(KnowledgeQuery query, long durationMs, RuntimeException exception) {
        return Map.of(
                "queryHash", queryHash(query),
                "durationMs", durationMs,
                "errorType", exception.getClass().getSimpleName()
        );
    }

    private String queryHash(KnowledgeQuery query) {
        return Integer.toHexString(query.text().hashCode());
    }

    private long durationMs(Instant startedAt) {
        return Math.max(0, Duration.between(startedAt, Instant.now()).toMillis());
    }

    private String safeErrorMessage(RuntimeException exception) {
        String message = exception.getMessage();
        if (message == null || message.isBlank()) {
            return exception.getClass().getSimpleName();
        }
        return message.length() <= 200 ? message : message.substring(0, 200) + "...";
    }
}
