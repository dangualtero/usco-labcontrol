import { useState } from 'react'
import { Link, NavLink, Outlet, useLocation, useNavigate } from 'react-router-dom'
import { KeyRound, LogOut, Menu, ShieldCheck, TimerReset, UserCog, UserRound, X, MapPin } from 'lucide-react'
import { useAuth } from '../context/AuthContext.jsx'
import { Modal } from './ui.jsx'
import ConexionBanner from './ConexionBanner.jsx'
import BotonTema from './BotonTema.jsx'
import CuentaModal from './CuentaModal.jsx'
import { modulosDe } from '../lib/modulos.js'
import { useSede, useSedes } from '../context/SedeContext.jsx'

const ICONO_ROL = { ADMINISTRADOR: ShieldCheck, COORDINADOR_LAB: UserCog, MONITOR: UserRound }

function BarraSuperior() {
  const { sede } = useSede()
  const hoy = new Date().toLocaleDateString('es-CO', { weekday: 'long', day: 'numeric', month: 'long', year: 'numeric' })
  return (
    <div className="bg-oro-oliva text-[12px] text-oro-claro">
      <div className="mx-auto flex max-w-[1440px] items-center justify-between gap-4 px-4 py-1.5 sm:px-6">
        <nav aria-label="Enlaces institucionales" className="flex items-center gap-1 font-medium">
          <a href="https://www.usco.edu.co" target="_blank" rel="noreferrer" className="rounded px-1.5 py-0.5 hover:text-white hover:underline">Portal USCO</a>
          <span aria-hidden="true" className="opacity-50">|</span>
          <span className="rounded px-1.5 py-0.5">Facultad de Ingeniería</span>
          <span aria-hidden="true" className="opacity-50">|</span>
          <span className="hidden rounded px-1.5 py-0.5 sm:inline">{sede?.nombre ?? 'Sede Neiva'}</span>
        </nav>
        <span className="hidden font-semibold sm:inline">{hoy}</span>
      </div>
    </div>
  )
}

/** Logo institucional: al hacer clic redirige a la primera pantalla de la cuenta (PF-13). */
export function LogoUsco({ to, className = 'h-11' }) {
  return (
    <Link to={to} aria-label="Universidad Surcolombiana, Acreditada de Alta Calidad. Ir al inicio" className="flex shrink-0 items-center rounded-md">
      <img src="/logo-usco.webp" width="481" height="128" alt="" className={`${className} w-auto`} />
    </Link>
  )
}

/** Selector de sede del Administrador general: cambia /neiva/... por /pitalito/... conservando la pantalla. */
function SelectorSede({ claro = false }) {
  const { slug } = useSede()
  const { sedes } = useSedes()
  const navegar = useNavigate()
  const loc = useLocation()
  if (!sedes || sedes.length < 2) return null
  // En pantallas de detalle (/solicitudes/12) se vuelve a la lista, porque el registro es de otra sede
  const cambiar = (nuevo) => navegar(`/${nuevo}${loc.pathname.replace(/^\/[^/]+/, '').replace(/^(\/[^/]+)\/.+$/, '$1')}`)
  return (
    <label className={`flex items-center gap-1.5 rounded-lg border px-2 text-sm font-semibold ${claro ? 'border-slate-300 bg-white text-slate-800' : 'border-oro-claro/50 bg-black/15'}`}>
      <MapPin className={`h-4 w-4 shrink-0 ${claro ? 'text-usco' : 'text-oro-claro'}`} aria-hidden="true" />
      <span className="sr-only">Sede que estás administrando</span>
      <select value={slug} onChange={(e) => cambiar(e.target.value)}
        className={`min-h-[44px] max-w-[160px] cursor-pointer truncate bg-transparent pr-1 focus:outline-none [&>option]:text-slate-900 ${claro ? 'text-slate-900' : 'text-white'}`}>
        {sedes.map((s) => <option key={s.slug} value={s.slug}>{s.nombre}{s.activa ? '' : ' (inactiva)'}</option>)}
      </select>
    </label>
  )
}

/** Navegación por módulos (los 8 de la hoja de roles), solo con lo que la cuenta puede ver. */
function Navegacion({ onElegir }) {
  const { usuario } = useAuth()
  const { ruta } = useSede()
  return (
    <nav aria-label="Módulos de LabControl" className="space-y-4">
      {modulosDe(usuario).map((g) => (
        <div key={g.grupo}>
          <p className="px-3 pb-1 text-[11px] font-extrabold uppercase tracking-wider text-pizarra">{g.grupo}</p>
          <ul className="space-y-0.5">
            {g.items.map(({ to, texto, icono: Ic }) => (
              <li key={to}>
                <NavLink to={ruta(to)} onClick={onElegir}
                  className={({ isActive }) => `flex min-h-[42px] items-center gap-2.5 rounded-lg px-3 text-sm font-bold transition-colors ${isActive ? 'bg-usco text-white shadow-sm' : 'text-pizarra-oscuro hover:bg-slate-100'}`}>
                  <Ic className="h-4 w-4 shrink-0" aria-hidden="true" /> {texto}
                </NavLink>
              </li>
            ))}
          </ul>
        </div>
      ))}
    </nav>
  )
}

export default function Shell() {
  const { usuario, cerrar, restante, continuar } = useAuth()
  const { inicio, sede } = useSede()
  const [menu, setMenu] = useState(false)
  const [cuenta, setCuenta] = useState(false)
  const loc = useLocation()
  const admin = usuario?.rol === 'ADMINISTRADOR'
  const IconoRol = ICONO_ROL[usuario?.rol] ?? UserRound
  const mm = restante !== null ? `${String(Math.floor(restante / 60)).padStart(2, '0')}:${String(restante % 60).padStart(2, '0')}` : ''
  const labs = usuario?.laboratorios?.map((l) => l.nombre).join(', ')

  return (
    <div className="flex min-h-screen flex-col">
      <a href="#contenido" className="sr-only focus:not-sr-only focus:absolute focus:z-[200] focus:m-2 focus:rounded focus:bg-white focus:px-3 focus:py-2">Saltar al contenido</a>
      <BarraSuperior />
      <ConexionBanner />

      <header className="border-b-4 border-oro-claro bg-usco text-white">
        <div className="mx-auto flex max-w-[1440px] items-center justify-between gap-3 px-4 py-3 sm:px-6">
          <div className="flex min-w-0 items-center gap-4">
            <LogoUsco to={inicio(usuario)} />
            <div className="hidden h-9 w-px bg-oro-claro/40 xl:block" aria-hidden="true" />
            <div className="hidden leading-tight xl:block">
              <p className="font-titulo text-[15px] font-extrabold tracking-tight">USCO <span className="text-oro-claro">LabControl</span></p>
              <p className="text-[11px] text-oro-claro/90">Gestión de laboratorios · Acreditada de Alta Calidad</p>
            </div>
          </div>

          <div className="flex items-center gap-2 sm:gap-3">
            <div className="hidden items-center gap-2.5 rounded-lg bg-black/15 py-1.5 pl-2 pr-3 lg:flex">
              <span className="flex h-8 w-8 items-center justify-center rounded-full bg-oro-claro text-usco-darker"><IconoRol className="h-4 w-4" aria-hidden="true" /></span>
              <span className="leading-tight">
                <span className="block max-w-[190px] truncate text-sm font-bold">{usuario?.nombre}</span>
                <span className="block max-w-[190px] truncate text-[11px] text-oro-claro" title={labs}>{usuario?.rolTexto}{labs ? ` · ${labs}` : ''}</span>
              </span>
            </div>
            {admin && <div className="hidden md:block"><SelectorSede /></div>}
            <BotonTema />
            <button onClick={() => setCuenta(true)} className="inline-flex min-h-[44px] items-center gap-2 rounded-lg border border-oro-claro/50 px-3 text-sm font-semibold hover:bg-white/10" aria-label="Mi cuenta: cambiar contraseña">
              <KeyRound className="h-4 w-4" aria-hidden="true" /> <span className="hidden lg:inline">Mi cuenta</span>
            </button>
            <button onClick={() => cerrar('manual')} className="inline-flex min-h-[44px] items-center gap-2 rounded-lg border border-oro-claro/50 px-3 text-sm font-semibold hover:bg-white/10" aria-label="Cerrar sesión">
              <LogOut className="h-4 w-4" aria-hidden="true" /> <span className="hidden sm:inline">Cerrar sesión</span>
            </button>
            <button onClick={() => setMenu((v) => !v)} className="inline-flex min-h-[44px] min-w-[44px] items-center justify-center rounded-lg border border-oro-claro/50 hover:bg-white/10 lg:hidden"
              aria-label={menu ? 'Cerrar menú' : 'Abrir menú de módulos'} aria-expanded={menu} aria-controls="menu-movil">
              {menu ? <X className="h-5 w-5" /> : <Menu className="h-5 w-5" />}
            </button>
          </div>
        </div>
      </header>

      {/* Menú de módulos en celular y tableta */}
      {menu && (
        <div id="menu-movil" className="border-b border-slate-200 bg-superficie px-4 py-4 shadow-lg lg:hidden aparecer">
          <p className="mb-3 text-xs text-pizarra">{usuario?.nombre} · {usuario?.rolTexto} · {sede?.nombre}</p>
          {admin && <div className="mb-4 md:hidden"><SelectorSede claro /></div>}
          <Navegacion onElegir={() => setMenu(false)} />
        </div>
      )}

      <div className="mx-auto flex w-full max-w-[1440px] flex-1 gap-6 px-4 sm:px-6">
        <aside className="hidden w-60 shrink-0 py-6 lg:block">
          <div className="sticky top-4 max-h-[calc(100vh-2rem)] overflow-y-auto pr-1"><Navegacion /></div>
        </aside>
        <main id="contenido" key={loc.pathname} className="min-w-0 flex-1 py-6">
          <Outlet />
        </main>
      </div>

      <footer className="border-t border-slate-200 bg-white">
        <div className="mx-auto flex max-w-[1440px] flex-col gap-1 px-4 py-4 text-xs text-pizarra sm:flex-row sm:items-center sm:justify-between sm:px-6">
          <p>© 2026 Universidad Surcolombiana · Facultad de Ingeniería · Programa de Ingeniería de Software · {sede?.nombre ?? 'Sede Neiva'}, Huila</p>
          <p>USCO LabControl v2.0.0 · Tratamiento de datos personales conforme a la Ley 1581 de 2012</p>
        </div>
      </footer>

      <CuentaModal abierto={cuenta} onCerrar={() => setCuenta(false)} />

      {/* Expiración de sesión por inactividad (PS-09) */}
      <Modal abierto={restante !== null} onCerrar={continuar} titulo="Sesión a punto de expirar" acento="bg-alerta-borde" ancho="max-w-md"
        pie={<>
          <button className="btn-outline" onClick={() => cerrar('manual')}>Cerrar sesión ahora</button>
          <button className="btn-primary" onClick={continuar}>Continuar en la sesión</button>
        </>}>
        <div className="flex items-start gap-3">
          <TimerReset className="mt-0.5 h-6 w-6 shrink-0 text-alerta-borde" aria-hidden="true" />
          <div>
            <p className="text-sm font-semibold text-slate-800">Por seguridad, la sesión se cerrará en <span className="font-mono text-lg font-extrabold text-alerta-borde" aria-live="off">{mm}</span></p>
            <p className="mt-1 text-sm text-pizarra">No hemos detectado actividad. Pulsa «Continuar» para mantener la sesión abierta.</p>
          </div>
        </div>
      </Modal>
    </div>
  )
}
