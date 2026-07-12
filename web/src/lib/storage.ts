export const storage = {
  get: (key: string, fallback = '') => localStorage.getItem(key) ?? fallback,
  set: (key: string, value: string) => localStorage.setItem(key, value),
  reset: () => localStorage.clear(),
}
