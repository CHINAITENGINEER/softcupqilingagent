export type AgentResponse = { traceId: string; status: string; answer: string; steps?: Array<{ name?: string; toolName?: string; status?: string; decision?: string; executed?: boolean; executionResult?: unknown; verificationResult?: unknown }>; startedAt?: string; endedAt?: string; approvalId?: string }
export type Approval = { approvalId: string; toolName: string; serviceName?: string; requester: string; riskLevel: 'LOW'|'MEDIUM'|'HIGH'|'CRITICAL'; reason: string; createdAt: string; status: string; traceId?: string }
export type AuditTrace = { traceId: string; status?: string; summary?: string; startedAt?: string; endedAt?: string; events?: Array<{ eventType?: string; actor?: string; toolName?: string; createdAt?: string; hash?: string; previousHash?: string; status?: string }> }
export type Knowledge = { knowledgeId: string; sourceType: string; title: string; snippet: string; score: number; lastUpdatedAt?: string; metadata?: Record<string,string> }

const base = () => localStorage.getItem('safeops.apiBase') || import.meta.env.VITE_SAFEOPS_API_BASE_URL || 'http://localhost:8088'
export class ApiUnavailable extends Error {}
async function request<T>(path: string, init?: RequestInit): Promise<T> {
  try {
    const response = await fetch(`${base()}${path}`, { ...init, headers: { 'Content-Type': 'application/json', 'X-Actor-Id': localStorage.getItem('safeops.userId') || 'operator-1', 'X-Actor-Roles': 'APPROVER,OPERATOR', ...(init?.headers || {}) }, signal: AbortSignal.timeout(4500) })
    if (!response.ok) throw new Error(await response.text())
    return response.json() as Promise<T>
  } catch (error) { throw new ApiUnavailable(error instanceof Error ? error.message : 'Backend unavailable') }
}
export const api = {
  health: () => request<{status:string}>('/actuator/health'),
  chat: (message: string, sessionId: string) => request<AgentResponse>('/api/agent/chat', { method: 'POST', body: JSON.stringify({ message, sessionId, userId: localStorage.getItem('safeops.userId') || 'operator-1' }) }),
  traces: () => request<AuditTrace[]>('/api/audit/traces'),
  integrity: (id: string) => request<{intact?:boolean; valid?:boolean}>(`/api/audit/traces/${id}/integrity`),
  decide: (id: string, decision: 'approve'|'reject') => request<{status:string}>(`/api/approvals/${decision}`, { method: 'POST', body: JSON.stringify({ approvalId: id }) }),
  search: (q: string, maxResults: number) => request<{results:Knowledge[]}>(`/api/rag/knowledge/search?q=${encodeURIComponent(q)}&maxResults=${maxResults}`),
  ingest: (document: { title:string; source:string; content:string; tags:string }) => request<{traceId:string; chunkCount:number}>('/api/rag/knowledge/ingest', { method: 'POST', body: JSON.stringify({ documents: [{ knowledgeId: `kb-${Date.now()}`, sourceType: document.source, title: document.title, content: document.content, metadata: { tags: document.tags } }] }) }),
}
