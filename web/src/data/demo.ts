import type { Approval, AuditTrace, Knowledge } from '../api/client'
export const approvals: Approval[] = [
  { approvalId:'APR-8F21C', toolName:'restart_service', serviceName:'nginx', requester:'operator-1', riskLevel:'HIGH', reason:'检测到 502 错误率超过阈值，需要受控重启', createdAt:'2026-07-12 10:42', status:'PENDING', traceId:'tr-7a92ce' },
  { approvalId:'APR-52B0A', toolName:'rotate_credentials', serviceName:'milvus', requester:'secops-02', riskLevel:'CRITICAL', reason:'例行凭证轮换', createdAt:'2026-07-12 09:18', status:'PENDING', traceId:'tr-c903ad' },
  { approvalId:'APR-1C79D', toolName:'restart_service', serviceName:'redis', requester:'operator-1', riskLevel:'MEDIUM', reason:'内存碎片整理', createdAt:'2026-07-11 18:06', status:'APPROVED', traceId:'tr-9a10bd' },
]
export const traces: AuditTrace[] = [
  { traceId:'tr-7a92ce', status:'WAITING_APPROVAL', summary:'nginx availability remediation', startedAt:'2026-07-12T10:42:12Z', events:[{eventType:'PLAN_CREATED',actor:'agent',createdAt:'10:42:12',hash:'d4a5…8cb1',previousHash:'0000…0000'},{eventType:'POLICY_REQUIRES_APPROVAL',actor:'policy-engine',createdAt:'10:42:13',hash:'9e14…af2e',previousHash:'d4a5…8cb1'}] },
  { traceId:'tr-9a10bd', status:'SUCCESS', summary:'redis health check complete', startedAt:'2026-07-12T09:34:09Z', events:[{eventType:'EXECUTION_COMPLETED',actor:'operator-1',createdAt:'09:34:11',hash:'e932…afc0',previousHash:'d622…10de'},{eventType:'VERIFICATION_PASSED',actor:'verifier',createdAt:'09:34:12',hash:'baa1…09c7',previousHash:'e932…afc0'}] },
  { traceId:'tr-c903ad', status:'DENIED', summary:'credential rotation denied', startedAt:'2026-07-12T08:48:42Z', events:[{eventType:'APPROVAL_REJECTED',actor:'security-lead',createdAt:'08:49:10',hash:'c991…31f5',previousHash:'714c…d22a'}] },
]
export const knowledge: Knowledge[] = [
  {knowledgeId:'kb-nginx-204',sourceType:'runbook',title:'Nginx 502 故障处理 Runbook',snippet:'先验证 upstream 健康状态与连接池，再检查最近发布和错误日志。高风险重启必须进入审批闭环。',score:.96,metadata:{tags:'nginx,incident',collection:'safeops_knowledge'}},
  {knowledgeId:'kb-milvus-019',sourceType:'architecture',title:'Milvus HNSW 索引运维指南',snippet:'HNSW 在低延迟检索场景下建议以 efConstruction=200 开始调优，并记录所有集合变更。',score:.88,metadata:{tags:'milvus,hnsw',collection:'safeops_knowledge'}},
]
