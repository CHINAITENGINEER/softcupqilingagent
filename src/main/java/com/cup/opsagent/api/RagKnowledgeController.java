package com.cup.opsagent.api;

import com.cup.opsagent.audit.AuditEvent;
import com.cup.opsagent.audit.AuditEventType;
import com.cup.opsagent.audit.AuditLogService;
import com.cup.opsagent.audit.AuditTrace;
import com.cup.opsagent.rag.KnowledgeDocument;
import com.cup.opsagent.rag.KnowledgeIngestionResult;
import com.cup.opsagent.rag.KnowledgeIngestionService;
import com.cup.opsagent.rag.KnowledgeQuery;
import com.cup.opsagent.rag.KnowledgeRetriever;
import com.cup.opsagent.rag.RetrievedKnowledge;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Validated
@RestController
@RequestMapping("/api/rag/knowledge")
public class RagKnowledgeController {

    private final KnowledgeIngestionService knowledgeIngestionService;
    private final KnowledgeRetriever knowledgeRetriever;
    private final AuditLogService auditLogService;

    public RagKnowledgeController(
            KnowledgeIngestionService knowledgeIngestionService,
            KnowledgeRetriever knowledgeRetriever,
            AuditLogService auditLogService
    ) {
        this.knowledgeIngestionService = knowledgeIngestionService;
        this.knowledgeRetriever = knowledgeRetriever;
        this.auditLogService = auditLogService;
    }

    @PostMapping("/ingest")
    public KnowledgeIngestionResponse ingest(@Valid @RequestBody KnowledgeIngestionRequest request) {
        AuditTrace trace = auditLogService.startTrace("rag knowledge ingestion");
        String traceId = trace.getTraceId();
        auditLogService.append(traceId, AuditEvent.success(traceId, AuditEventType.RAG_KNOWLEDGE_INGESTION_STARTED, payload(
                "documentCount", request.documents().size(),
                "knowledgeIds", request.documents().stream().map(KnowledgeDocumentRequest::knowledgeId).toList()
        )));
        try {
            KnowledgeIngestionResult result = knowledgeIngestionService.ingest(request.documents().stream()
                    .map(KnowledgeDocumentRequest::toDocument)
                    .toList());
            auditLogService.append(traceId, AuditEvent.success(traceId, AuditEventType.RAG_KNOWLEDGE_INGESTION_COMPLETED, payload(
                    "documentCount", result.documentCount(),
                    "chunkCount", result.chunkCount(),
                    "knowledgeIds", result.knowledgeIds(),
                    "chunkIds", result.chunkIds()
            )));
            auditLogService.finish(traceId, "RAG_KNOWLEDGE_INGESTED", "RAG knowledge ingestion completed");
            return KnowledgeIngestionResponse.from(traceId, result);
        } catch (RuntimeException exception) {
            auditLogService.append(traceId, AuditEvent.failure(traceId, AuditEventType.RAG_KNOWLEDGE_INGESTION_FAILED, payload(
                    "errorType", exception.getClass().getSimpleName(),
                    "documentCount", request.documents().size()
            ), exception.getMessage()));
            auditLogService.finish(traceId, "RAG_KNOWLEDGE_INGESTION_FAILED", "RAG knowledge ingestion failed");
            throw exception;
        }
    }

    @GetMapping("/search")
    public KnowledgeSearchResponse search(
            @NotBlank @RequestParam("q") String query,
            @Min(1) @Max(20) @RequestParam(value = "maxResults", defaultValue = "5") int maxResults
    ) {
        List<RetrievedKnowledge> results = knowledgeRetriever.retrieve(new KnowledgeQuery(query, maxResults, Map.of()));
        return KnowledgeSearchResponse.from(query, maxResults, results);
    }

    private Map<String, Object> payload(Object... entries) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (int index = 0; index + 1 < entries.length; index += 2) {
            Object key = entries[index];
            if (key != null) {
                result.put(String.valueOf(key), entries[index + 1] == null ? "" : entries[index + 1]);
            }
        }
        return Map.copyOf(result);
    }

    public record KnowledgeIngestionRequest(
            @NotEmpty List<@Valid KnowledgeDocumentRequest> documents
    ) {
    }

    public record KnowledgeDocumentRequest(
            @NotBlank String knowledgeId,
            @NotBlank String sourceType,
            @NotBlank String title,
            @NotBlank String content,
            Map<String, String> metadata
    ) {
        KnowledgeDocument toDocument() {
            return new KnowledgeDocument(
                    knowledgeId,
                    sourceType,
                    title,
                    content,
                    Instant.now(),
                    metadata
            );
        }
    }

    public record KnowledgeIngestionResponse(
            String traceId,
            int documentCount,
            int chunkCount,
            List<String> knowledgeIds,
            List<String> chunkIds
    ) {
        static KnowledgeIngestionResponse from(String traceId, KnowledgeIngestionResult result) {
            return new KnowledgeIngestionResponse(
                    traceId,
                    result.documentCount(),
                    result.chunkCount(),
                    result.knowledgeIds(),
                    result.chunkIds()
            );
        }
    }

    public record KnowledgeSearchResponse(
            String query,
            int maxResults,
            int resultCount,
            List<KnowledgeSearchItemResponse> results
    ) {
        static KnowledgeSearchResponse from(String query, int maxResults, List<RetrievedKnowledge> results) {
            List<RetrievedKnowledge> safeResults = results == null ? List.of() : results;
            return new KnowledgeSearchResponse(
                    query,
                    maxResults,
                    safeResults.size(),
                    safeResults.stream().map(KnowledgeSearchItemResponse::from).toList()
            );
        }
    }

    public record KnowledgeSearchItemResponse(
            String knowledgeId,
            String sourceType,
            String title,
            String snippet,
            double score,
            Instant lastUpdatedAt,
            Map<String, String> metadata
    ) {
        static KnowledgeSearchItemResponse from(RetrievedKnowledge knowledge) {
            return new KnowledgeSearchItemResponse(
                    knowledge.knowledgeId(),
                    knowledge.sourceType(),
                    knowledge.title(),
                    knowledge.snippet(),
                    knowledge.score(),
                    knowledge.lastUpdatedAt(),
                    knowledge.metadata()
            );
        }
    }
}
