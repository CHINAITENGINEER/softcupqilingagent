import { Check } from 'lucide-react'
export type ToastMessage = { text: string; tone?: 'success' | 'danger' }
export function Toast({ message }: { message: ToastMessage | null }) { return message ? <div className={`toast ${message.tone || ''}`} role="status"><Check size={16}/>{message.text}</div> : null }
