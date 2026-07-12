import { useCallback, useRef, useState } from 'react'
import type { ToastMessage } from '../components/ui/Toast'
export function useToast() { const [message, setMessage] = useState<ToastMessage | null>(null); const timer = useRef<number | undefined>(undefined); const show = useCallback((text: string, tone: ToastMessage['tone'] = 'success') => { window.clearTimeout(timer.current); setMessage({ text, tone }); timer.current = window.setTimeout(() => setMessage(null), 2800) }, []); return { message, show } }
