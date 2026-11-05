import { useCallback, useEffect, useRef, useState } from 'react'
import { useAuth } from '../context/AuthContext.jsx'

/**
 * Consulta la API periódicamente y a demanda (botón «Actualizar» / «Reintentar»). Si el servidor responde 401
 * (sesión expirada) cierra la sesión de forma segura (PS-09). Nunca rellena con datos inventados: ante un
 * error, `datos` conserva lo último recibido de la API (o null) y `error` trae el mensaje real.
 */
export function useDatos(cargador, deps = [], { cada = 15000 } = {}) {
  const { cerrar } = useAuth()
  const [datos, setDatos] = useState(null)
  const [error, setError] = useState(null)
  const [actualizado, setActualizado] = useState(null)
  const [cargando, setCargando] = useState(false)
  const fn = useRef(cargador)
  fn.current = cargador

  const recargar = useCallback(async () => {
    setCargando(true)
    try {
      const d = await fn.current()
      setDatos(d); setError(null); setActualizado(new Date())
    } catch (e) {
      if (e.status === 401) cerrar('expirada')
      else setError(e.message)
    } finally { setCargando(false) }
  }, [cerrar])

  useEffect(() => {
    let vivo = true
    const go = () => { if (vivo && !document.hidden) recargar() }
    recargar()
    const t = cada ? setInterval(go, cada) : null
    document.addEventListener('visibilitychange', go)
    window.addEventListener('labcontrol:reintentar', recargar)
    return () => { vivo = false; if (t) clearInterval(t); document.removeEventListener('visibilitychange', go); window.removeEventListener('labcontrol:reintentar', recargar) }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [recargar, cada, ...deps])

  return { datos, error, actualizado, cargando, recargar }
}

/** Reloj para textos relativos (vence en…). */
export function useAhora(ms = 30000) {
  const [ahora, setAhora] = useState(new Date())
  useEffect(() => { const t = setInterval(() => setAhora(new Date()), ms); return () => clearInterval(t) }, [ms])
  return ahora
}
