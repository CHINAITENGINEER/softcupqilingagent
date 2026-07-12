# Roadmap

This roadmap is optimized for two goals:

1. Competition demo quality.
2. Long-term portfolio maintainability.

## v0.1 - Core backend and real Milvus CI

Status: completed.

- Agent orchestration core.
- Tool registry and policy engine.
- Approval workflow.
- Execution verification.
- Audit trace and integrity checks.
- RAG ingestion and retrieval.
- Milvus vector store integration.
- HNSW and IVF_FLAT integration tests.
- Java CI.
- Milvus integration CI.
- README and safe environment template.

## v0.2 - Demo completeness

Status: in progress.

Goals:

- Make the project easy to run and easy to present.

Tasks:

- Local Milvus Docker Compose.
- Demo knowledge base.
- Chinese demo script.
- API documentation.
- Architecture documentation.
- Security model documentation.
- SafeOps Console frontend.

## v0.3 - Extreme frontend and product experience

Status: planned.

Goals:

- Turn the project into a polished security operations command center.

Tasks:

- React + TypeScript + Vite frontend under `web/`.
- Dashboard overview.
- Agent Chat page.
- Approval Center.
- Audit Trace visualization.
- RAG Knowledge management.
- Settings and health checks.
- Demo mode and real API mode.
- Frontend build CI.

## v0.4 - API completeness

Status: planned.

Goals:

- Make frontend integration first-class.

Tasks:

- Approval list/search API.
- Approval detail API.
- RAG statistics API.
- System status API.
- Tool metadata API stabilization.
- OpenAPI/Swagger UI.
- Error code reference.

## v0.5 - Engineering governance

Status: planned.

Goals:

- Improve long-term maintainability.

Tasks:

- Maven `verify` CI.
- JaCoCo coverage report.
- Dependency review workflow.
- Release workflow.
- CHANGELOG.
- Version tags.
- Issue templates.
- Pull request template.

## v0.6 - Deployment and operations

Status: planned.

Goals:

- Make deployment reproducible.

Tasks:

- Production Dockerfile.
- Docker Compose for app + database + Milvus.
- Kubernetes manifests or Helm chart.
- PostgreSQL profile deployment guide.
- Observability guide.
- Backup and restore guide.

## v1.0 - Portfolio-ready release

Status: planned.

Release criteria:

- Backend tests pass.
- Frontend build passes.
- Milvus integration passes.
- Demo script works from a fresh clone.
- README includes screenshots.
- Architecture and security docs are up to date.
- A tagged GitHub release exists.
- No secrets or local artifacts are committed.
