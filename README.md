# QilingOS SafeOps Agent

[![Java CI](https://github.com/CHINAITENGINEER/softcupqilingagent/actions/workflows/java-ci.yml/badge.svg)](https://github.com/CHINAITENGINEER/softcupqilingagent/actions/workflows/java-ci.yml)
[![Web Console CI](https://github.com/CHINAITENGINEER/softcupqilingagent/actions/workflows/web-console-ci.yml/badge.svg)](https://github.com/CHINAITENGINEER/softcupqilingagent/actions/workflows/web-console-ci.yml)
[![Milvus RAG integration](https://github.com/CHINAITENGINEER/softcupqilingagent/actions/workflows/milvus-integration.yml/badge.svg)](https://github.com/CHINAITENGINEER/softcupqilingagent/actions/workflows/milvus-integration.yml)

QilingOS SafeOps Agent is a Spring Boot based intelligent operations agent for safe, auditable, and approval-aware system operations. It includes rule-based planning, OpenAI-compatible LLM planning, policy checks, approval workflow, audit trace, verification, and RAG knowledge retrieval with Milvus vector store support.

## SafeOps Console

The repository includes a modern React + TypeScript security-operations command center in [`web/`](web/). It provides an Agent Chat experience, approval center, audit hash-chain viewer, RAG/Milvus knowledge console, and system connection settings. The console automatically switches to clearly marked Demo Mode when the backend is unavailable.

The Console is engineered as a portfolio-grade frontend: route-level code splitting, typed API adapters, reusable safety-domain components, responsive command-center UI, and independent Web Console CI-ready build/lint/typecheck commands.

```powershell
cd web
npm install
npm run dev
```

See [`web/README.md`](web/README.md) for integration, demo mode, build, and preview instructions.

<!-- SafeOps Console screenshot placeholder: add `docs/images/safeops-console-overview.png` after UI capture. -->

## Features

- Safe agent orchestration for read-only and risky operations.
- Tool policy engine with risk level, permission, argument schema, and dangerous intent checks.
- Approval workflow for high-risk actions such as service restart.
- Audit trace with hash-chain integrity support.
- Rule-based, fake LLM, and OpenAI-compatible LLM planner modes.
- RAG knowledge ingestion and search APIs.
- In-memory vector store for local tests and demos.
- Milvus vector store with configurable `autoindex`, `hnsw`, and `ivf-flat` index types.
- Real Milvus HNSW and IVF_FLAT integration tests.

## Tech stack

- Java 21
- Spring Boot 3.3.5
- Maven
- H2 / PostgreSQL-compatible schema migration with Flyway
- Milvus Java SDK 2.5.10
- GitHub Actions CI

## Repository layout

```text
src/main/java/com/cup/opsagent
  agent/       Agent orchestration and response model
  api/         REST controllers and API error handling
  approval/   Approval records, repository, and execution lease
  audit/      Audit trace and integrity verification
  auth/       Actor and role/permission model
  executor/   Command template registry and safe command runner
  planner/    Rule-based and LLM-based planning
  rag/        RAG domain model, embedding clients, vector stores, Milvus adapter
  safety/     Policy engine and argument validation
  tool/       Built-in operations tools
  verifier/   Execution verification

docs/         Design notes and teaching materials
.github/      Java CI and Milvus integration workflows
deploy/       Linux deployment helpers
```

## Prerequisites

- JDK 21
- Maven 3.9+
- Docker Desktop or Docker Engine, only required for local Milvus tests

Check versions:

```powershell
java -version
mvn -version
docker --version
```

## Build and test

Run the default test suite:

```powershell
mvn test
```

Default tests do not require Milvus. Real Milvus integration tests are skipped unless explicitly enabled.

Run one focused test class:

```powershell
mvn "-Dtest=AgentControllerWebTest" test
```

## Run locally

Start the application with default settings:

```powershell
mvn spring-boot:run
```

Default port is defined in `src/main/resources/application.properties`.

RAG is disabled by default:

```properties
ops-agent.rag.enabled=false
```

## Start local Milvus

Start the same standalone Milvus stack used by the integration workflow:

```powershell
docker compose -f docker-compose.milvus.yml up -d
docker compose -f docker-compose.milvus.yml ps
```

The compose stack includes:

```text
quay.io/coreos/etcd:v3.5.5
minio/minio:RELEASE.2023-03-20T20-16-18Z
milvusdb/milvus:v2.4.13
```

After Milvus starts, verify:

```powershell
Invoke-WebRequest "http://localhost:9091/healthz" -UseBasicParsing
Test-NetConnection localhost -Port 19530
```

Expected TCP result:

```text
TcpTestSucceeded : True
```

Stop the stack:

```powershell
docker compose -f docker-compose.milvus.yml down
```

Remove local Milvus data volumes when you need a clean demo environment:

```powershell
docker compose -f docker-compose.milvus.yml down -v
```

## RAG + Milvus configuration

A safe environment template is provided:

```text
.env.example
```

Copy it for local use if needed, but do not commit `.env` files:

```powershell
Copy-Item .env.example .env
```

Spring Boot reads OS environment variables. Import values into your shell before starting the application or running tests.

Important Milvus variables:

```powershell
$env:SAFEOPS_RAG_MILVUS_URI="http://localhost:19530"
$env:SAFEOPS_RAG_MILVUS_COLLECTION="safeops_knowledge"
$env:SAFEOPS_RAG_MILVUS_DIMENSION="1536"
$env:SAFEOPS_RAG_MILVUS_METRIC_TYPE="cosine"
$env:SAFEOPS_RAG_MILVUS_INDEX_TYPE="hnsw"
$env:SAFEOPS_RAG_MILVUS_AUTO_CREATE_COLLECTION="true"
$env:SAFEOPS_RAG_MILVUS_AUTO_LOAD_COLLECTION="true"
```

Start with the Milvus profile:

```powershell
mvn spring-boot:run "-Dspring-boot.run.profiles=rag-milvus"
```

### HNSW parameters

```powershell
$env:SAFEOPS_RAG_MILVUS_INDEX_TYPE="hnsw"
$env:SAFEOPS_RAG_MILVUS_HNSW_M="16"
$env:SAFEOPS_RAG_MILVUS_HNSW_EF_CONSTRUCTION="200"
$env:SAFEOPS_RAG_MILVUS_HNSW_EF="64"
```

### IVF_FLAT parameters

```powershell
$env:SAFEOPS_RAG_MILVUS_INDEX_TYPE="ivf-flat"
$env:SAFEOPS_RAG_MILVUS_IVF_FLAT_NLIST="128"
$env:SAFEOPS_RAG_MILVUS_IVF_FLAT_NPROBE="16"
```

## Real Milvus integration tests

Run HNSW integration test:

```powershell
$env:SAFEOPS_RAG_MILVUS_IT="true"
$env:SAFEOPS_RAG_MILVUS_URI="http://localhost:19530"
mvn "-Dtest=RagMilvusKnowledgeIntegrationTest" test
```

Run IVF_FLAT integration test:

```powershell
$env:SAFEOPS_RAG_MILVUS_IVF_FLAT_IT="true"
$env:SAFEOPS_RAG_MILVUS_URI="http://localhost:19530"
mvn "-Dtest=RagMilvusIvfFlatKnowledgeIntegrationTest" test
```

Run both:

```powershell
$env:SAFEOPS_RAG_MILVUS_IT="true"
$env:SAFEOPS_RAG_MILVUS_IVF_FLAT_IT="true"
$env:SAFEOPS_RAG_MILVUS_URI="http://localhost:19530"
mvn "-Dtest=RagMilvusKnowledgeIntegrationTest,RagMilvusIvfFlatKnowledgeIntegrationTest" test
```

By default, integration tests use temporary collections and clean them up. To keep collections for debugging:

```powershell
$env:SAFEOPS_RAG_MILVUS_KEEP_COLLECTION="true"
```

## Demo assets

Demo knowledge documents are provided under:

```text
demo/knowledge/
```

They cover:

- QilingOS Nginx operations.
- System load inspection.
- Milvus RAG troubleshooting.

A Chinese presentation and recording script is available at:

```text
docs/demo-script-zh.md
```

## CI workflows

### Java CI

Workflow:

```text
.github/workflows/java-ci.yml
```

Triggers:

```text
push to main
pull_request to main
```

Command:

```bash
mvn -B test
```

### Milvus RAG integration

Workflow:

```text
.github/workflows/milvus-integration.yml
```

Triggers:

```text
manual workflow_dispatch
weekly schedule: UTC Saturday 18:30, Beijing time Sunday 02:30
```

This workflow starts temporary etcd, MinIO, and Milvus containers, then runs the real HNSW and IVF_FLAT integration tests.

## Secrets and safety

Never commit real credentials. These values must be stored in environment variables or CI secret management:

```text
SAFEOPS_RAG_MILVUS_TOKEN
SAFEOPS_RAG_EMBEDDING_API_KEY
```

Ignored local files include:

```text
.env
.env.local
.env.*.local
target/
release-staging/
*.tar.gz
```

RAG knowledge is treated as untrusted context. It must not bypass:

```text
LlmPlanValidator
PolicyEngine
Approval
Verifier
AuditTrace
```

Milvus SDK usage is guarded by an architecture test so business packages do not directly depend on `io.milvus`.

## Documentation

Project-level documentation:

```text
docs/api.md
docs/architecture.md
docs/security-model.md
docs/roadmap.md
docs/demo-script-zh.md
```

Main RAG implementation and operations notes:

```text
docs/teaching/chapter-16-rag-knowledge-context/06-implementation-plan.md
```

Related teaching materials are under:

```text
docs/teaching/
```

## Current status

- Default Java CI passes on GitHub Actions.
- Real Milvus HNSW and IVF_FLAT integration workflow passes on GitHub Actions.
- Local default test suite passes with real Milvus tests skipped by default.
