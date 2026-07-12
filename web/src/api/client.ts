import type { AgentResponse, Approval, ApprovalDecision, ApprovalExecution, AuditTrace, HealthResponse, Knowledge, KnowledgeInput, RagStats, RagStatus, SystemMetrics, SystemStatus, ToolDefinition } from './types'
export type { AgentResponse, Approval, AuditTrace, Knowledge } from './types'

const base = () => localStorage.getItem('safeops.apiBase') || import.meta.env.VITE_SAFEOPS_API_BASE_URL || ''
export class ApiNetworkError extends Error { constructor(message = 'Network unavailable') { super(message); this.name = 'ApiNetworkError' } }
export class ApiTimeoutError extends Error { constructor(message = 'Request timed out') { super(message); this.name = 'ApiTimeoutError' } }
export class ApiHttpError extends Error { constructor(public readonly status: number, public readonly code: string, message: string, public readonly path: string, public readonly body: unknown) { super(message); this.name = 'ApiHttpError' } }
export class ApiUnavailable extends ApiNetworkError {}

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  const controller = new AbortController(); const timeout = window.setTimeout(() => controller.abort(), 4500)
  try {
    const response = await fetch(`${base()}${path}`, { ...init, headers: { 'Content-Type': 'application/json', 'X-Actor-Id': localStorage.getItem('safeops.userId') || 'operator-1', 'X-Actor-Roles': 'APPROVER,OPERATOR,EXECUTOR', ...(init?.headers || {}) }, signal: controller.signal })
    const text = await response.text(); let body: unknown
    try { body = text ? JSON.parse(text) : undefined } catch { body = text }
    if (!response.ok) { const error = body && typeof body === 'object' ? body as Record<string, unknown> : {}; throw new ApiHttpError(response.status, String(error.code || `HTTP_${response.status}`), String(error.message || response.statusText || 'Request failed'), String(error.path || path), body) }
    return body as T
  } catch (error) {
    if (error instanceof ApiHttpError) throw error
    if (error instanceof DOMException && error.name === 'AbortError') throw new ApiTimeoutError()
    throw new ApiNetworkError(error instanceof Error ? error.message : 'Network unavailable')
  } finally { window.clearTimeout(timeout) }
}
export function apiErrorMessage(error: unknown): string { if (error instanceof ApiHttpError) return `${error.status} ${error.code}: ${error.message}`; if (error instanceof ApiTimeoutError) return 'Request timed out; no state was changed.'; if (error instanceof ApiNetworkError) return 'Backend is unavailable; no state was changed.'; return 'Request failed; no state was changed.' }
export const api = {
  health: () => request<HealthResponse>('/actuator/health'), chat: (message: string, sessionId: string) => request<AgentResponse>('/api/agent/chat', { method: 'POST', body: JSON.stringify({ message, sessionId, userId: localStorage.getItem('safeops.userId') || 'operator-1' }) }), traces: () => request<AuditTrace[]>('/api/audit/traces'), approvals: async () => (await request<Array<Record<string, unknown>>>('/api/approvals')).map(normalizeApproval), approval: async (id: string) => normalizeApproval(await request<Record<string, unknown>>(`/api/approvals/${encodeURIComponent(id)}`)), integrity: (id: string) => request<{intact?:boolean; valid?:boolean}>(`/api/audit/traces/${id}/integrity`), decide: (id: string, decision: 'approve'|'reject') => request<ApprovalDecision>(`/api/approvals/${decision}`, { method: 'POST', body: JSON.stringify({ approvalId: id }) }), execute: (leaseId: string, toolName: string, arguments_: Record<string, unknown>) => request<ApprovalExecution>('/api/approvals/execute', { method: 'POST', body: JSON.stringify({ leaseId, toolName, arguments: arguments_ }) }), search: (q: string, maxResults: number) => request<{results:Knowledge[]}>(`/api/rag/knowledge/search?q=${encodeURIComponent(q)}&maxResults=${maxResults}`), ingest: (document: KnowledgeInput) => request<{traceId:string; chunkCount:number}>('/api/rag/knowledge/ingest', { method: 'POST', body: JSON.stringify({ documents: [{ knowledgeId: `kb-${Date.now()}`, sourceType: document.source, title: document.title, content: document.content, metadata: { tags: document.tags } }] }) }), systemStatus: () => request<SystemStatus>('/api/system/status'), systemMetrics: () => request<SystemMetrics>('/api/system/metrics'), ragStatus: () => request<RagStatus>('/api/rag/status'), ragStats: () => request<RagStats>('/api/rag/stats'), tools: () => request<ToolDefinition[]>('/api/tools'),
}
function normalizeApproval(value: Record<string, unknown>): Approval { return { approvalId: String(value.approvalId || ''), traceId: String(value.traceId || ''), toolName: String(value.toolName || ''), serviceName: String(value.serviceName || ''), requester: String(value.requester ?? value.requesterId ?? 'operator'), riskLevel: String(value.riskLevel || 'MEDIUM') as Approval['riskLevel'], reason: String(value.reason || ''), createdAt: String(value.createdAt || ''), status: String(value.status || 'PENDING'), actionHash: String(value.actionHash || ''), expiresAt: String(value.expiresAt || ''), decidedAt: String(value.decidedAt || ''), decidedBy: String(value.decidedBy || ''), arguments: (value.arguments || undefined) as Record<string, unknown> | undefined } }
