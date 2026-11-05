import { createContext, useCallback, useContext, useEffect, useMemo, useState } from 'react'
import { Link, Outlet, useParams } from 'react-router-dom'
import { Building2, Loader2, MapPinOff } from 'lucide-react'
import { api, setSede } from '../api/index.js'
import { homeDe } from '../lib/constantes.js'
import ConexionBanner from '../components/ConexionBanner.jsx'

/**
 * Sedes de la Universidad. La sede activa es el primer segmento de la URL (/neiva, /pitalito, /garzon…):
 * cada sede tiene su propio inventario, sus usuarios y auxiliares, y su presupuesto.
 */
const ListaCtx = createContext(null)
const SedeCtx = createContext(null)

/** Carga la lista de sedes una sola vez (endpoint público). */
export function SedesProvider({ children }) {
  const [sedes, setSedes] = useState(null)
  const [error, setError] = useState(null)
  const cargar = useCallback(async () => {
    try { setSedes(await api.sedes()); setError(null) } catch (e) { setError(e.message) }
  }, [])
  useEffect(() => {
    cargar()
    window.addEventListener('labcontrol:reintentar', cargar)
    window.addEventListener('labcontrol:sedes', cargar)          // tras crear o editar una sede
    return () => { window.removeEventListener('labcontrol:reintentar', cargar); window.removeEventListener('labcontrol:sedes', cargar) }
  }, [cargar])
  const valor = useMemo(() => ({ sedes, error, recargarSedes: cargar }), [sedes, error, cargar])
  return <ListaCtx.Provider value={valor}>{children}</ListaCtx.Provider>
}

export const useSedes = () => useContext(ListaCtx)
export const refrescarSedes = () => window.dispatchEvent(new Event('labcontrol:sedes'))

/**
 * Sede de la URL actual.
 *  - `slug` / `sede`: la sede activa ({ slug, nombre, ciudad, direccion, activa }).
 *  - `ruta('/coordinador')` → '/pitalito/coordinador'.
 *  - `inicio(grupo)` → panel principal del rol en esta sede.
 */
export function useSede() {
  return useContext(SedeCtx) ?? { slug: null, sede: null, ruta: (p) => p, inicio: (g) => homeDe(g) }
}

/** Ruta /:sede — valida que la sede exista y la deja activa para todas las peticiones a la API. */
export function SedeLayout() {
  const { sede: slugUrl } = useParams()
  const slug = (slugUrl ?? '').toLowerCase()
  const { sedes, error } = useSedes()
  // Se fija durante el render (no en un efecto) para que las pantallas hijas ya consulten la API con la sede correcta.
  setSede(slug)

  const sede = sedes?.find((s) => s.slug === slug) ?? null
  const valor = useMemo(() => ({
    slug,
    sede,
    ruta: (p = '') => `/${slug}${p.startsWith('/') || p === '' ? p : `/${p}`}`,
    inicio: (grupo) => homeDe(grupo, slug),
  }), [slug, sede])

  if (!sedes && !error) {
    return <div className="flex min-h-screen items-center justify-center gap-2 text-pizarra" role="status"><Loader2 className="h-5 w-5 animate-spin" aria-hidden="true" /> Cargando sede…</div>
  }
  if (!sedes && error) {
    return (
      <div className="flex min-h-screen flex-col">
        <ConexionBanner />
        <div className="flex flex-1 items-center justify-center p-6 text-center text-pizarra"><p>{error}</p></div>
      </div>
    )
  }
  if (!sede) return <SedeNoEncontrada slug={slug} />
  return <SedeCtx.Provider value={valor}><Outlet /></SedeCtx.Provider>
}

function SedeNoEncontrada({ slug }) {
  return (
    <div className="flex min-h-screen items-center justify-center bg-fondo p-6">
      <div className="card max-w-md p-8 text-center aparecer" role="alert">
        <div className="mx-auto mb-4 flex h-14 w-14 items-center justify-center rounded-full bg-alerta-fondo"><MapPinOff className="h-7 w-7 text-alerta-borde" aria-hidden="true" /></div>
        <h1 className="text-xl font-extrabold">Sede no encontrada</h1>
        <p className="mt-2 text-sm text-pizarra">La dirección <span className="font-mono font-bold">/{slug}</span> no corresponde a ninguna sede de USCO LabControl.</p>
        <Link to="/" className="btn-primary mt-6"><Building2 className="h-4 w-4" aria-hidden="true" /> Elegir mi sede</Link>
      </div>
    </div>
  )
}
