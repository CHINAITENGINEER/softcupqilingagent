# SafeOps Console

SafeOps Console is the React + TypeScript command center for QilingOS SafeOps Agent. It presents protected agent operations, approval gates, tamper-evident audit traces, and Milvus-backed RAG knowledge retrieval in one responsive interface.

## Stack

- React, TypeScript, Vite
- React Router, Recharts, Lucide icons
- Central typed API adapter with automatic demo fallback

## Start

```powershell
cd web
npm install
npm run dev
```

The default console address is `http://localhost:5173`. Vite proxies `/api` and `/actuator` to the Spring Boot service at `http://localhost:8088` during development.

## Connect a backend

Create `web/.env.local`:

```text
VITE_SAFEOPS_API_BASE_URL=http://localhost:8088
```

The System Settings screen can also override this value locally. The console uses:

- `POST /api/agent/chat`
- `POST /api/approvals/approve` and `/reject`
- `GET /api/audit/traces`
- `GET /api/rag/knowledge/search`
- `POST /api/rag/knowledge/ingest`
- `GET /actuator/health`

The current backend exposes no approval-list endpoint, so that screen intentionally displays a polished demo queue plus manual approval-ID action entry.

## Demo mode

If the backend is unavailable, each page gracefully renders representative SafeOps data and marks the state as **Demo Mode**. No mock action is sent to an external system.

## Verify

```powershell
npm run typecheck
npm run lint
npm run build
npm run preview
```
