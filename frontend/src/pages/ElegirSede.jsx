import { Link, Navigate } from 'react-router-dom'
import { ArrowRight, Building2, Loader2, MapPin } from 'lucide-react'
import { useAuth } from '../context/AuthContext.jsx'
import { useSedes } from '../context/SedeContext.jsx'
import ConexionBanner from '../components/ConexionBanner.jsx'
import BotonTema from '../components/BotonTema.jsx'

/** Pantalla de inicio: «Elige tu sede». Cada sede tiene su propia dirección (/neiva, /pitalito, /garzon…). */
export default function ElegirSede() {
  const { usuario, home, validando } = useAuth()
  const { sedes, error } = useSedes()
  if (validando) return null
  if (usuario) return <Navigate to={home} replace />
  const activas = (sedes ?? []).filter((s) => s.activa)

  return (
    <div className="flex min-h-screen flex-col bg-fondo">
      <ConexionBanner />
      <header className="relative overflow-hidden bg-usco text-white">
        <div className="pointer-events-none absolute inset-0 opacity-[0.08]" style={{ backgroundImage: 'radial-gradient(#DFD4A6 1.2px, transparent 1.2px)', backgroundSize: '22px 22px' }} />
        <div className="pointer-events-none absolute -right-24 -top-24 h-80 w-80 rounded-full border-[32px] border-oro-claro/10" />
        <div className="relative mx-auto flex max-w-5xl flex-col gap-6 px-5 pb-12 pt-6 sm:px-8">
          <div className="flex items-center justify-between gap-4">
            <img src="/logo-usco.png" alt="Universidad Surcolombiana" className="h-12 w-auto" />
            <BotonTema />
          </div>
          <div>
            <p className="text-xs font-bold uppercase tracking-widest text-oro-claro">Facultad de Ingeniería · Préstamo de equipos de laboratorio</p>
            <h1 className="mt-2 font-titulo text-4xl font-extrabold leading-tight tracking-tight text-white sm:text-5xl">USCO <span className="text-oro-claro">LabControl</span></h1>
            <p className="mt-3 max-w-2xl text-base text-white/90 sm:text-lg">Elige tu sede para ingresar. Cada sede tiene su propio inventario, sus auxiliares y sus usuarios.</p>
          </div>
        </div>
        <div className="h-1 bg-oro-claro" />
      </header>

      <main className="mx-auto w-full max-w-5xl flex-1 px-5 py-8 sm:px-8">
        {!sedes && !error && <p className="flex items-center gap-2 text-pizarra" role="status"><Loader2 className="h-5 w-5 animate-spin" aria-hidden="true" /> Cargando sedes…</p>}
        {error && !sedes && <p className="text-pizarra">{error}</p>}
        {sedes && (
          <ul className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
            {activas.map((s) => (
              <li key={s.slug}>
                <Link to={`/${s.slug}/login`} className="card group flex h-full flex-col gap-4 p-5 transition-shadow hover:shadow-lg focus-visible:shadow-lg">
                  <span className="flex h-12 w-12 items-center justify-center rounded-xl bg-usco-soft text-usco"><Building2 className="h-6 w-6" aria-hidden="true" /></span>
                  <span className="flex-1">
                    <span className="block font-titulo text-xl font-extrabold text-slate-900">{s.nombre}</span>
                    <span className="mt-1 flex items-center gap-1.5 text-sm text-pizarra"><MapPin className="h-4 w-4 shrink-0" aria-hidden="true" /> {s.ciudad}{s.direccion ? ` · ${s.direccion}` : ''}</span>
                  </span>
                  <span className="flex items-center justify-between border-t border-slate-100 pt-3">
                    <span className="inline-flex items-center gap-1 text-sm font-bold text-usco">Ingresar <ArrowRight className="h-4 w-4 transition-transform group-hover:translate-x-0.5" aria-hidden="true" /></span>
                  </span>
                </Link>
              </li>
            ))}
          </ul>
        )}
      </main>
      <footer className="border-t border-slate-200 bg-white px-5 py-4 text-center text-xs text-pizarra">© 2026 Universidad Surcolombiana · USCO LabControl</footer>
    </div>
  )
}
