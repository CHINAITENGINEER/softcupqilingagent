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
