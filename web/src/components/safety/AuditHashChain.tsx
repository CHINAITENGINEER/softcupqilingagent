import { ArrowRight, Copy } from 'lucide-react'
import type { AuditTrace } from '../../api/types'
import { now } from '../../lib/format'
export function AuditHashChain({ trace }: { trace: AuditTrace }) { return <div className="eventchain">{(trace.events || []).map((event, index) => <div className="audit-event" key={index}><span>{index + 1}</span><div><b>{event.eventType || 'AUDIT_EVENT'}</b><p>{event.actor || 'safeops-agent'} · {event.createdAt || now()}</p><code>{event.previousHash || '0000…0000'} <ArrowRight size={11}/> {event.hash || 'hash pending'}</code></div><button className="icon" onClick={() => void navigator.clipboard?.writeText(event.hash || '')} aria-label="复制哈希"><Copy size={14}/></button></div>)}</div> }
