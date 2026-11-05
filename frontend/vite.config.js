import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

// En desarrollo, /api se reenvía al backend Spring Boot (puerto 8080).
// Si el backend no está activo, la app cae automáticamente al modo Mock en memoria.
export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    proxy: { '/api': { target: 'http://localhost:8080', changeOrigin: true } },
  },
  test: { environment: 'node' },
})
