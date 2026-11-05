import { RefreshCw, WifiOff } from 'lucide-react'
import { useAuth } from '../context/AuthContext.jsx'
import { MSG_CONEXION } from '../api/index.js'

/** Banner global: aparece solo cuando la API de Spring Boot no responde. No hay datos de respaldo. */
export default function ConexionBanner({ className = '' }) {
  const { conectado, reintentar } = useAuth()
  if (conectado) return null
  return (
    <div role="alert" className={`flex flex-col gap-2 bg-alerta-borde px-4 py-3 text-white sm:flex-row sm:items-center sm:justify-between ${className}`}>
      <p className="flex items-start gap-2 text-sm font-bold"><WifiOff className="mt-0.5 h-5 w-5 shrink-0" aria-hidden="true" /> {MSG_CONEXION}</p>
      <button onClick={reintentar} className="inline-flex min-h-[40px] items-center justify-center gap-2 rounded-lg bg-white px-4 text-sm font-bold text-alerta-texto hover:bg-red-50">
        <RefreshCw className="h-4 w-4" aria-hidden="true" /> Reintentar
      </button>
    </div>
  )
}
