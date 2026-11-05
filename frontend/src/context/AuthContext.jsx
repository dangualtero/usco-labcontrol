import { createContext, useCallback, useContext, useEffect, useMemo, useRef, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { api, setToken, getSede, estaConectado, suscribirConexion, reintentarTodo } from '../api/index.js'
import { homeDe } from '../lib/constantes.js'

const Ctx = createContext(null)
export const useAuth = () => useContext(Ctx)

const KEY = 'usco.labcontrol.sesion'
const leer = (st) => { try { return JSON.parse(st.getItem(KEY)) } catch { return null } }
const borrar = () => { try { localStorage.removeItem(KEY); sessionStorage.removeItem(KEY) } catch { /* noop */ } }
const guardar = (s, recordar) => {
  borrar()
  try { (recordar ? localStorage : sessionStorage).setItem(KEY, JSON.stringify({ ...s, recordar })) } catch { /* noop */ }
}

// Cierre por inactividad (PS-09) cuando NO se marcó «Recordar mi sesión»: 15 min, con aviso de 90 s.
const IDLE_S = 15 * 60
const AVISO_S = 90

export function AuthProvider({ children }) {
  const nav = useNavigate()
  const inicial = leer(localStorage) ?? leer(sessionStorage)
  const [sesion, setSesion] = useState(inicial)
  // Al recargar la página, el token guardado se valida contra el backend antes de mostrar nada.
  const [validando, setValidando] = useState(!!inicial)
  const [conectado, setConectado] = useState(estaConectado())
  const [restante, setRestante] = useState(null)
  const ultimo = useRef(Date.now())

  useEffect(() => suscribirConexion(setConectado), [])

  const validar = useCallback(async () => {
    const s = leer(localStorage) ?? leer(sessionStorage)
    if (!s) { setValidando(false); return }
    setToken(s.token)
    try {
      const u = await api.yo()
      const nuevo = { token: s.token, usuario: u }
      guardar(nuevo, !!s.recordar)
      setSesion({ ...nuevo, recordar: !!s.recordar })
    } catch (e) {
      if (e.status === 401 || e.status === 403) { borrar(); setToken(null); setSesion(null) }
      // status 0 (sin conexión): se conserva la sesión y el banner global informa el problema.
    } finally { setValidando(false) }
  }, [])

  useEffect(() => { if (inicial) validar() /* eslint-disable-next-line react-hooks/exhaustive-deps */ }, [])

  const iniciar = useCallback(async ({ email, password, recordar }) => {
    const r = await api.login({ email, password, recordar })
    setToken(r.token)
    const s = { token: r.token, usuario: r.usuario }
    guardar(s, !!recordar)
    ultimo.current = Date.now()
    setSesion({ ...s, recordar: !!recordar })
    return r.usuario
  }, [])

  /** Cierra la sesión y destruye el token (PS-06). */
  const cerrar = useCallback((motivo) => {
    setToken(null)
    borrar()
    setSesion(null)
    setRestante(null)
    // Vuelve al ingreso de la misma sede (/pitalito/login); sin sede, a la pantalla «Elige tu sede».
    const s = getSede()
    nav(s ? `/${s}/login` : '/', { replace: true, state: { cierre: motivo ?? 'manual' } })
  }, [nav])

  // Inactividad con cuenta regresiva visible (solo sesiones sin «recordar»)
  const sinRecordar = sesion && !sesion.recordar
  useEffect(() => {
    if (!sinRecordar) return undefined
    const activo = () => { if (restante === null) ultimo.current = Date.now() }
    const eventos = ['mousemove', 'keydown', 'click', 'touchstart', 'scroll']
    eventos.forEach((e) => window.addEventListener(e, activo, { passive: true }))
    const t = setInterval(() => {
      const faltan = Math.ceil(IDLE_S - (Date.now() - ultimo.current) / 1000)
      if (faltan <= 0) cerrar('inactividad')
      else if (faltan <= AVISO_S) setRestante(faltan)
    }, 1000)
    return () => { eventos.forEach((e) => window.removeEventListener(e, activo)); clearInterval(t) }
  }, [sinRecordar, restante, cerrar])

  const continuar = useCallback(() => { ultimo.current = Date.now(); setRestante(null) }, [])

  const valor = useMemo(() => ({
    usuario: sesion?.usuario ?? null,
    grupo: sesion?.usuario?.grupo ?? null,
    // Inicio del rol en su sede (el Coordinador general, en la sede que esté viendo).
    home: sesion ? homeDe(sesion.usuario.grupo, sesion.usuario.sede ?? getSede()) : '/',
    validando, conectado, restante, iniciar, cerrar, continuar, reintentar: () => { reintentarTodo(); validar() },
  }), [sesion, validando, conectado, restante, iniciar, cerrar, continuar, validar])

  return <Ctx.Provider value={valor}>{children}</Ctx.Provider>
}
