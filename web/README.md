# SafeOps Console

SafeOps Console is the React + TypeScript command center for QilingOS SafeOps Agent. It presents protected agent operations, approval gates, tamper-evident audit traces, and Milvus-backed RAG knowledge retrieval in one responsive interface.

## Stack

- React, TypeScript, Vite
- React Router, Recharts, Lucide icons
- Central typed API adapter with automatic demo fallback

## Architecture

```text
src/
  app/            application composition, shell layout, lazy router
  pages/          route-level feature pages
  components/ui/  shared visual primitives
  components/safety/  domain-specific safety and audit views
  api/            typed client and backend contracts
  hooks/          demo-resource, local-storage, API-status hooks
  lib/            storage and formatting helpers
  styles/         global console styling entrypoint
```

Each route is loaded with `React.lazy` and `Suspense`. Recharts is isolated into a dedicated build chunk, keeping the command-center shell and initial page lightweight.

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

The System Settings screen can also override this value locally. The console uses these live backend endpoints:

- `GET /api/system/status`
- `GET /api/approvals`
- `GET /api/approvals/{approvalId}`
- `POST /api/approvals/approve` and `/reject`
- `POST /api/approvals/execute`
- `GET /api/tools`
- `GET /api/rag/status`
- `GET /api/rag/stats`
- `POST /api/agent/chat`
- `GET /api/audit/traces`
- `GET /api/rag/knowledge/search`
- `POST /api/rag/knowledge/ingest`
- `GET /actuator/health`

Approval Center, Dashboard, RAG Knowledge, and Settings are wired to live APIs first, then fall back to demo resources if the backend is unavailable.

## Demo mode

If the backend is unavailable, each page gracefully renders representative SafeOps data and marks the state as **Demo Mode**. No mock action is sent to an external system.

## Verify

```powershell
npm run typecheck
npm run lint
npm run build
npm run preview
```

## Screenshots

Screenshot placeholders and capture guidance live in [`../docs/images/README.md`](../docs/images/README.md). Add only redacted, non-sensitive showcase images under the documented filenames.
