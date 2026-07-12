# API Reference

Base URL for local development:

```text
http://localhost:8088
```

All examples use JSON unless noted otherwise.

## Health

### GET `/actuator/health`

Checks whether the Spring Boot application is running.

Example:

```powershell
Invoke-RestMethod "http://localhost:8088/actuator/health"
```

## System status

### GET `/api/system/status`

Returns console-oriented backend status, including registered tools, approval counts, audit trace count, RAG mode, active profiles, and runtime metadata.

Response highlights:

```json
{
  "status": "UP",
  "serviceName": "QilingOS SafeOps Agent",
  "version": "0.1.0-SNAPSHOT",
  "activeProfiles": [],
  "agent": {
    "status": "ONLINE",
    "registeredToolCount": 3
  },
  "approvals": {
    "total": 1,
    "pending": 1,
    "highRiskPending": 1
  },
  "audit": {
    "traceCount": 4,
    "integrityMode": "ENABLED"
  },
  "rag": {
    "enabled": false,
    "retrieverMode": "keyword",
    "vectorStoreProvider": "in-memory"
  }
}
```

## Agent

### POST `/api/agent/chat`

Submits an operations request to the SafeOps Agent.

Request:

```json
{
  "message": "health check",
  "sessionId": "demo-session-1",
  "userId": "operator-1"
}
```

Response highlights:

```json
{
  "traceId": "...",
  "status": "SUCCESS",
  "answer": "...",
  "steps": [
    {
      "stepId": "step-1",
      "toolName": "get_system_load",
      "decision": "ALLOW",
      "executed": true,
      "executionResult": {},
      "verificationResult": {}
    }
  ],
  "startedAt": "...",
  "endedAt": "..."
}
```

Important statuses:

| Status | Meaning |
| --- | --- |
| `SUCCESS` | The request completed and verification passed. |
| `WAITING_APPROVAL` | A high-risk action was blocked until approval. |
| `VERIFICATION_FAILED` | The command ran but post-check verification failed. |
| `DENIED` | Policy rejected the planned action. |

## Approvals

### GET `/api/approvals`

Lists approval records for the console, ordered by newest first.

Response item:

```json
{
  "approvalId": "approval-xxx",
  "traceId": "trace-xxx",
  "stepId": "step-1",
  "requesterId": "operator-1",
  "toolName": "restart_service",
  "serviceName": "nginx",
  "riskLevel": "HIGH",
  "status": "PENDING",
  "reason": "requires approval",
  "actionHash": "...",
  "createdAt": "...",
  "expiresAt": "...",
  "decidedAt": null,
  "decidedBy": ""
}
```

### GET `/api/approvals/{approvalId}`

Returns one approval with canonical arguments.

### POST `/api/approvals/approve`

Approves a pending high-risk action and creates a time-limited execution lease.

Request:

```json
{
  "approvalId": "approval-xxx"
}
```

Response:

```json
{
  "approvalId": "approval-xxx",
  "status": "APPROVED",
  "leaseId": "lease-xxx",
  "actionHash": "...",
  "leaseExpiresAt": "..."
}
```

### POST `/api/approvals/reject`

Rejects a pending approval.

Request:

```json
{
  "approvalId": "approval-xxx"
}
```

Response:

```json
{
  "approvalId": "approval-xxx",
  "status": "REJECTED",
  "leaseId": "none",
  "actionHash": "...",
  "leaseExpiresAt": "none"
}
```

### POST `/api/approvals/execute`

Executes an approved action by lease.

Request:

```json
{
  "leaseId": "lease-xxx",
  "toolName": "restart_service",
  "arguments": {
    "serviceName": "nginx"
  }
}
```

Response:

```json
{
  "leaseId": "lease-xxx",
  "executionSuccess": true,
  "verified": true,
  "failureCode": "none",
  "verificationReason": "..."
}
```

## Audit

### GET `/api/audit/traces`

Lists audit traces.

```powershell
Invoke-RestMethod "http://localhost:8088/api/audit/traces"
```

### GET `/api/audit/traces/{traceId}`

Gets one trace by ID.

### GET `/api/audit/traces/{traceId}/integrity`

Checks hash-chain integrity for one trace.

Response fields depend on `AuditIntegrityResult`, but the result indicates whether the trace is intact and where any mismatch is found.

## RAG status

### GET `/api/rag/status`

Returns RAG configuration safe for UI display. Secret fields such as API keys and tokens are never returned.

```json
{
  "enabled": false,
  "retrieverMode": "keyword",
  "embeddingProvider": "deterministic",
  "embeddingModel": "",
  "vectorStoreProvider": "in-memory",
  "milvusUri": "",
  "milvusCollection": "",
  "milvusDimension": 0,
  "milvusMetricType": "cosine",
  "milvusIndexType": "autoindex",
  "maxResults": 5
}
```

### GET `/api/rag/stats`

Returns vector store statistics. In-memory vector store exposes exact record count; external providers may return `vectorRecordCountStatus = "unknown"`.

```json
{
  "vectorStoreProvider": "in-memory",
  "collection": "",
  "vectorRecordCount": 0,
  "vectorRecordCountStatus": "known",
  "indexParameters": {
    "hnswM": 16,
    "hnswEfConstruction": 200,
    "hnswEf": 64,
    "ivfFlatNlist": 128,
    "ivfFlatNprobe": 16
  }
}
```

## RAG Knowledge

### POST `/api/rag/knowledge/ingest`

Ingests knowledge documents into the configured retriever/vector store.

Request:

```json
{
  "documents": [
    {
      "knowledgeId": "qiling-nginx-ops",
      "sourceType": "demo",
      "title": "QilingOS Nginx 运维处置手册",
      "content": "...",
      "metadata": {
        "domain": "nginx",
        "os": "qiling"
      }
    }
  ]
}
```

Response:

```json
{
  "traceId": "...",
  "documentCount": 1,
  "chunkCount": 3,
  "knowledgeIds": ["qiling-nginx-ops"],
  "chunkIds": ["..."]
}
```

### GET `/api/rag/knowledge/search?q={query}&maxResults={n}`

Searches knowledge context.

Example:

```powershell
Invoke-RestMethod "http://localhost:8088/api/rag/knowledge/search?q=nginx%20restart&maxResults=5"
```

Response:

```json
{
  "query": "nginx restart",
  "maxResults": 5,
  "resultCount": 1,
  "results": [
    {
      "knowledgeId": "qiling-nginx-ops",
      "sourceType": "demo",
      "title": "QilingOS Nginx 运维处置手册",
      "snippet": "...",
      "score": 0.92,
      "lastUpdatedAt": "...",
      "metadata": {}
    }
  ]
}
```

## Tools

The project also exposes a tool controller. Use it for discovery in UI integrations after checking `ToolController` for the current response shape.

## Error handling

Validation errors return `400` with a structured error payload. Common causes:

- Blank `message` in `/api/agent/chat`.
- Missing `approvalId`.
- Empty `documents` during RAG ingestion.
- `maxResults` outside `1..20`.
