import { Database } from 'lucide-react'
export function Empty({ title, detail }: { title: string; detail: string }) { return <div className="empty"><Database size={28}/><b>{title}</b><span>{detail}</span></div> }
