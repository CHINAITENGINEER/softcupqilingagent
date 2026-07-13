import { defineConfig, loadEnv } from 'vite'
import react from '@vitejs/plugin-react'

export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, '.', '')
  const apiTarget = env.VITE_SAFEOPS_PROXY_TARGET || 'http://localhost:8088'
  return {
    plugins: [react()],
    server: { port: 5173, proxy: { '/api': apiTarget, '/actuator': apiTarget } },
    build: {
      rolldownOptions: {
        output: {
          manualChunks(id) {
            if (id.includes('node_modules/recharts')) return 'charts'
            if (id.includes('node_modules/lucide-react')) return 'icons'
            if (id.includes('node_modules/react') || id.includes('node_modules/react-router')) return 'react'
          },
        },
      },
    },
  }
})
