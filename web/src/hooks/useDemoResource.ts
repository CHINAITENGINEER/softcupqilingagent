import { useCallback, useEffect, useRef, useState } from 'react'

export function useDemoResource<T>(request: () => Promise<T>, fallback: T) {
  const fallbackRef = useRef(fallback)
  const [data, setData] = useState<T>(fallback)
  const [demo, setDemo] = useState(true)
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    fallbackRef.current = fallback
  }, [fallback])

  const reload = useCallback(async () => {
    setLoading(true)
    try {
      setData(await request())
      setDemo(false)
    } catch {
      setData(fallbackRef.current)
      setDemo(true)
    } finally {
      setLoading(false)
    }
  }, [request])

  useEffect(() => { void reload() }, [reload])

  return { data, demo, loading, reload, setData }
}

