import { Badge } from './Badge'
export function StatusBadge({ status }: { status?: string }) { const value = status || 'UNKNOWN'; const tone = /SUCCESS|APPROVED|PASSED|HEALTHY/i.test(value) ? 'success' : /WAIT|PENDING/i.test(value) ? 'warning' : /DENIED|FAILED|CRITICAL/i.test(value) ? 'danger' : 'info'; return <Badge tone={tone}><i/> {value.replaceAll('_', ' ')}</Badge> }
