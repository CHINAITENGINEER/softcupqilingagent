import { useState } from 'react'
import { storage } from '../lib/storage'

export function useLocalStorage(key: string, fallback: string) {
  const [value, setValue] = useState(() => storage.get(key, fallback))
  const update = (next: string) => { setValue(next); storage.set(key, next) }
  return [value, update] as const
}
