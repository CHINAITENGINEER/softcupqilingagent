# Architecture

QilingOS SafeOps Agent is organized around one principle:

> AI can propose operational actions, but deterministic safety components decide whether those actions can run.

## High-level modules

```mermaid
flowchart LR
    User[Operator / Console] --> API[Spring Boot REST API]
    API --> Agent[Agent Orchestrator]
    Agent --> Planner[Planner / LLM Planner]
    Agent --> RAG[RAG Augmentor]
    RAG --> Retriever[Knowledge Retriever]
    Retriever --> VectorStore[In-memory / Milvus Vector Store]
    Agent --> Policy[Policy Engine]
    Policy --> Tools[Tool Registry]
    Agent --> Approval[Approval Service]
    Approval --> Lease[Execution Lease]
    Agent --> Executor[Command Runner]
    Executor --> Verifier[Verifier]
    Agent --> Audit[Audit Log Service]
    Approval --> Audit
    Verifier --> Audit
```

## Request lifecycle

```mermaid
sequenceDiagram
    participant U as Operator
    participant API as AgentController
    participant AO as AgentOrchestrator
    participant RAG as RagAugmentor
    participant P as Planner
    participant PE as PolicyEngine
    participant AP as ApprovalService
    participant EX as CommandRunner
    participant VF as Verifier
    participant AU as AuditLogService

    U->>API: POST /api/agent/chat
    API->>AO: handle(request)
    AO->>AU: startTrace()
    AO->>RAG: retrieve context
    RAG-->>AO: knowledge snippets
    AO->>P: plan task steps
    P-->>AO: tool calls
    AO->>PE: evaluate each tool call
    alt Low-risk read-only action
        AO->>EX: execute command
        EX-->>AO: execution result
        AO->>VF: verify result
        VF-->>AO: verification result
        AO->>AU: append execution and verification events
        AO-->>API: SUCCESS or VERIFICATION_FAILED
    else High-risk action
        AO->>AP: create approval
        AO->>AU: append approval requested
        AO-->>API: WAITING_APPROVAL
    else Denied action
        AO->>AU: append policy denied
        AO-->>API: DENIED
    end
```

## Safety pipeline

```mermaid
flowchart LR
    Plan[Plan] --> Validate[Validate tool call]
    Validate --> Policy[Policy decision]
    Policy -->|ALLOW read-only| Execute[Execute]
    Policy -->|REQUIRE_APPROVAL| Approval[Approval]
    Policy -->|DENY| Stop[Stop]
    Approval --> Lease[Execution lease]
    Lease --> Execute
    Execute --> Verify[Verify]
    Verify --> Audit[Audit trace]
```

## RAG pipeline

```mermaid
flowchart LR
    Knowledge[Knowledge documents] --> Chunk[Chunking]
    Chunk --> Embed[Embedding client]
    Embed --> Store[Vector store]
    Store --> Search[Knowledge search]
    Search --> Context[RAG context]
    Context --> Planner[Planner prompt]
```

Supported vector stores:

- In-memory vector store for local tests and demos.
- Milvus vector store for real vector retrieval.

Supported Milvus index strategies:

- `autoindex`
- `hnsw`
- `ivf-flat`

## Approval execution model

A high-risk action does not execute immediately. The system creates an approval record containing a stable action hash. After approval, a short-lived execution lease is issued. Execution must match the approved tool and arguments.

```mermaid
flowchart TD
    Request[Risky request] --> Pending[Pending approval]
    Pending -->|Approve| Lease[Time-limited lease]
    Pending -->|Reject| Rejected[Rejected]
    Lease --> Execute[Execute approved action]
    Execute --> Verify[Post-execution verification]
    Verify --> Audit[Audit events]
```

## Audit model

Audit traces are grouped by `traceId`. Events are appended through `AuditLogService`; integrity checks verify that trace data has not been tampered with.

Important audit moments:

- Trace started.
- Planner produced tool calls.
- Policy allowed, denied, or required approval.
- Approval requested/granted/rejected.
- Execution completed.
- Verification passed/failed.
- RAG ingestion/search related events.

## CI architecture

```mermaid
flowchart LR
    Push[Push / PR] --> JavaCI[Java CI]
    JavaCI --> MavenTest[mvn -B test]
    Schedule[Weekly / Manual] --> MilvusCI[Milvus RAG integration]
    MilvusCI --> Containers[etcd + MinIO + Milvus]
    Containers --> IT[HNSW + IVF_FLAT tests]
```
