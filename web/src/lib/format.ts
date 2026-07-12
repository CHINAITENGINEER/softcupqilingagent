export const shortDate = (value?: string) => value?.slice(0, 16).replace('T', ' ') || '—'
export const now = () => new Date().toLocaleString('zh-CN', { hour: '2-digit', minute: '2-digit', month: 'short', day: 'numeric' })
