import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

// Cabeceras de seguridad (las mismas de nginx en producción; ver seguridad-headers.conf).
const SEGURIDAD = {
  'X-Frame-Options': 'DENY',
  'X-Content-Type-Options': 'nosniff',
  'Referrer-Policy': 'strict-origin-when-cross-origin',
  'Permissions-Policy': 'camera=(self), microphone=(), geolocation=(), payment=()',
  'Cross-Origin-Opener-Policy': 'same-origin',
}
const CSP = "default-src 'self'; script-src 'self'; style-src 'self' 'unsafe-inline'; img-src 'self' data: blob: https:; " +
  "font-src 'self' data:; connect-src 'self'; object-src 'none'; base-uri 'self'; form-action 'self'; frame-ancestors 'none'"
const api = { '/api': { target: 'http://localhost:8080', changeOrigin: true } }

export default defineConfig({
  plugins: [react()],
  // npm run dev: sin CSP, porque la recarga en caliente de Vite inyecta scripts propios.
  server: { port: 5173, proxy: api, headers: SEGURIDAD },
  // npm run build && npm run preview → http://localhost:4173: la versión compilada con TODAS las cabeceras,
  // igual que en producción. Es la que conviene escanear con OWASP ZAP si no usas Docker.
  preview: { port: 4173, proxy: api, headers: { ...SEGURIDAD, 'Content-Security-Policy': CSP } },
  test: { environment: 'node' },
})
