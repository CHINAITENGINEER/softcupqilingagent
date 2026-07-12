import type { AgentResponse, Approval, ApprovalDecision, ApprovalExecution, AuditTrace, HealthResponse, Knowledge, KnowledgeInput, RagStats, RagStatus, SystemStatus, ToolDefinition } from './types'
export type { AgentResponse, Approval, AuditTrace, Knowledge } from './types'

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
  health: () => request<HealthResponse>('/actuator/health'),
  chat: (message: string, sessionId: string) => request<AgentResponse>('/api/agent/chat', { method: 'POST', body: JSON.stringify({ message, sessionId, userId: localStorage.getItem('safeops.userId') || 'operator-1' }) }),
  traces: () => request<AuditTrace[]>('/api/audit/traces'),
  approvals: async () => (await request<Array<Record<string, unknown>>>('/api/approvals')).map(normalizeApproval),
  approval: async (id: string) => normalizeApproval(await request<Record<string, unknown>>(`/api/approvals/${id}`)),
  integrity: (id: string) => request<{intact?:boolean; valid?:boolean}>(`/api/audit/traces/${id}/integrity`),
  decide: (id: string, decision: 'approve'|'reject') => request<ApprovalDecision>(`/api/approvals/${decision}`, { method: 'POST', body: JSON.stringify({ approvalId: id }) }),
  execute: (leaseId: string, toolName: string, arguments_: Record<string, unknown>) => request<ApprovalExecution>('/api/approvals/execute', { method: 'POST', body: JSON.stringify({ leaseId, toolName, arguments: arguments_ }) }),
  search: (q: string, maxResults: number) => request<{results:Knowledge[]}>(`/api/rag/knowledge/search?q=${encodeURIComponent(q)}&maxResults=${maxResults}`),
  ingest: (document: KnowledgeInput) => request<{traceId:string; chunkCount:number}>('/api/rag/knowledge/ingest', { method: 'POST', body: JSON.stringify({ documents: [{ knowledgeId: `kb-${Date.now()}`, sourceType: document.source, title: document.title, content: document.content, metadata: { tags: document.tags } }] }) }),
  systemStatus: () => request<SystemStatus>('/api/system/status'),
  ragStatus: () => request<RagStatus>('/api/rag/status'),
  ragStats: () => request<RagStats>('/api/rag/stats'),
  tools: () => request<ToolDefinition[]>('/api/tools'),
}
function normalizeApproval(value: Record<string, unknown>): Approval { return { approvalId: String(value.approvalId || ''), traceId: String(value.traceId || ''), toolName: String(value.toolName || ''), serviceName: String(value.serviceName || ''), requester: String(value.requester ?? value.requesterId ?? 'operator'), riskLevel: String(value.riskLevel || 'MEDIUM') as Approval['riskLevel'], reason: String(value.reason || ''), createdAt: String(value.createdAt || ''), status: String(value.status || 'PENDING'), actionHash: String(value.actionHash || ''), expiresAt: String(value.expiresAt || ''), decidedAt: String(value.decidedAt || ''), decidedBy: String(value.decidedBy || ''), arguments: (value.arguments || undefined) as Record<string, unknown> | undefined } }
