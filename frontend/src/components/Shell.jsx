import { useState } from 'react'
import { Link, NavLink, Outlet, useLocation, useNavigate } from 'react-router-dom'
import {
  BookOpenCheck, CalendarClock, ClipboardList, History, KeyRound, LayoutDashboard, LogOut, Menu, Package, Repeat2,
  ShieldCheck, TimerReset, UserRound, Users, X, GraduationCap, ClipboardCheck, Wallet, Building2, MapPin,
} from 'lucide-react'
import { useAuth } from '../context/AuthContext.jsx'
import { Modal } from './ui.jsx'
import ConexionBanner from './ConexionBanner.jsx'
import BotonTema from './BotonTema.jsx'
import CuentaModal from './CuentaModal.jsx'
import { GRUPOS } from '../lib/constantes.js'
import { useSede, useSedes } from '../context/SedeContext.jsx'

const NAV = {
  USUARIO: [
    { to: '/usuario/catalogo', texto: 'Catálogo', icono: BookOpenCheck },
    { to: '/usuario/reservas', texto: 'Mis reservas', icono: CalendarClock },
    { to: '/usuario/historial', texto: 'Mi historial', icono: History },
  ],
  AUXILIAR: [
    { to: '/auxiliar', texto: 'Ventanilla', icono: ClipboardList, fin: true },
    { to: '/auxiliar/inventario', texto: 'Inventario', icono: Package },
    { to: '/auxiliar/turno', texto: 'Cierre de turno', icono: Repeat2 },
  ],
  COORDINADOR: [
    { to: '/coordinador', texto: 'Dashboard', icono: LayoutDashboard, fin: true },
    { to: '/coordinador/historial', texto: 'Historial', icono: History },
    { to: '/coordinador/inventario', texto: 'Inventario', icono: Package },
    { to: '/coordinador/usuarios', texto: 'Usuarios', icono: Users },
    { to: '/coordinador/presupuesto', texto: 'Presupuesto', icono: Wallet },
    { to: '/coordinador/sedes', texto: 'Sedes', icono: Building2 },
  ],
}
const ICONO_ROL = { USUARIO: GraduationCap, AUXILIAR: ClipboardCheck, COORDINADOR: ShieldCheck }

function BarraSuperior() {
  const { sede } = useSede()
  const hoy = new Date().toLocaleDateString('es-CO', { weekday: 'long', day: 'numeric', month: 'long', year: 'numeric' })
  return (
    <div className="bg-oro-oliva text-[12px] text-oro-claro">
      <div className="mx-auto flex max-w-7xl items-center justify-between gap-4 px-4 py-1.5 sm:px-6">
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

/** Logo institucional: al hacer clic redirige al panel principal del rol (PF-13). */
export function LogoUsco({ to, className = 'h-11' }) {
  return (
    <Link to={to} aria-label="Universidad Surcolombiana, Acreditada de Alta Calidad. Ir al panel principal" className="flex shrink-0 items-center rounded-md">
      <img src="/logo-usco.png" alt="" className={`${className} w-auto`} />
    </Link>
  )
}

/** Selector de sede del Coordinador general: cambia /neiva/... por /pitalito/... conservando la pantalla. */
function SelectorSede() {
  const { slug } = useSede()
  const { sedes } = useSedes()
  const navegar = useNavigate()
  const loc = useLocation()
  if (!sedes || sedes.length < 2) return null
  const cambiar = (nuevo) => navegar(`/${nuevo}${loc.pathname.replace(/^\/[^/]+/, '')}`)
  return (
    <label className="flex items-center gap-1.5 rounded-lg border border-oro-claro/50 bg-black/15 px-2 text-sm font-semibold">
      <MapPin className="h-4 w-4 shrink-0 text-oro-claro" aria-hidden="true" />
      <span className="sr-only">Sede que estás administrando</span>
      <select value={slug} onChange={(e) => cambiar(e.target.value)}
        className="min-h-[44px] max-w-[150px] cursor-pointer truncate bg-transparent pr-1 text-white focus:outline-none [&>option]:text-slate-900">
        {sedes.map((s) => <option key={s.slug} value={s.slug}>{s.nombre}{s.activa ? '' : ' (inactiva)'}</option>)}
      </select>
    </label>
  )
}

export default function Shell() {
  const { usuario, grupo, cerrar, restante, continuar } = useAuth()
  const { ruta, inicio, sede } = useSede()
  const [menu, setMenu] = useState(false)
  const [cuenta, setCuenta] = useState(false)
  const loc = useLocation()
  const items = NAV[grupo] ?? []
  const IconoRol = ICONO_ROL[grupo] ?? UserRound
  const mm = restante !== null ? `${String(Math.floor(restante / 60)).padStart(2, '0')}:${String(restante % 60).padStart(2, '0')}` : ''

  return (
    <div className="flex min-h-screen flex-col">
      <a href="#contenido" className="sr-only focus:not-sr-only focus:absolute focus:z-[200] focus:m-2 focus:rounded focus:bg-white focus:px-3 focus:py-2">Saltar al contenido</a>
      <BarraSuperior />
      <ConexionBanner />

      <header className="bg-usco text-white">
        <div className="mx-auto flex max-w-7xl items-center justify-between gap-3 px-4 py-3 sm:px-6">
          <div className="flex min-w-0 items-center gap-4">
            <LogoUsco to={inicio(grupo)} />
            <div className="hidden h-9 w-px bg-oro-claro/40 xl:block" aria-hidden="true" />
            <div className="hidden leading-tight xl:block">
              <p className="font-titulo text-[15px] font-extrabold tracking-tight">USCO <span className="text-oro-claro">LabControl</span></p>
              <p className="text-[11px] text-oro-claro/90">Préstamos de laboratorios · Acreditada de Alta Calidad</p>
            </div>
          </div>

          <div className="flex items-center gap-2 sm:gap-3">
            <div className="hidden items-center gap-2.5 rounded-lg bg-black/15 py-1.5 pl-2 pr-3 lg:flex">
              <span className="flex h-8 w-8 items-center justify-center rounded-full bg-oro-claro text-usco-darker"><IconoRol className="h-4 w-4" aria-hidden="true" /></span>
              <span className="leading-tight">
                <span className="block max-w-[170px] truncate text-sm font-bold">{usuario?.nombre}</span>
                <span className="block text-[11px] text-oro-claro">{GRUPOS[grupo]?.etiqueta}</span>
              </span>
            </div>
            {grupo === 'COORDINADOR' && <div className="hidden md:block"><SelectorSede /></div>}
            <BotonTema />
            <button onClick={() => setCuenta(true)} className="inline-flex min-h-[44px] items-center gap-2 rounded-lg border border-oro-claro/50 px-3 text-sm font-semibold hover:bg-white/10" aria-label="Mi cuenta: cambiar contraseña">
              <KeyRound className="h-4 w-4" aria-hidden="true" /> <span className="hidden lg:inline">Mi cuenta</span>
            </button>
            <button onClick={() => cerrar('manual')} className="inline-flex min-h-[44px] items-center gap-2 rounded-lg border border-oro-claro/50 px-3 text-sm font-semibold hover:bg-white/10" aria-label="Cerrar sesión">
              <LogOut className="h-4 w-4" aria-hidden="true" /> <span className="hidden sm:inline">Cerrar sesión</span>
            </button>
            <button onClick={() => setMenu((v) => !v)} className="inline-flex min-h-[44px] min-w-[44px] items-center justify-center rounded-lg border border-oro-claro/50 hover:bg-white/10 md:hidden"
              aria-label={menu ? 'Cerrar menú' : 'Abrir menú principal'} aria-expanded={menu} aria-controls="menu-principal">
              {menu ? <X className="h-5 w-5" /> : <Menu className="h-5 w-5" />}
            </button>
          </div>
        </div>

        {/* Navegación: pestañas en escritorio, menú hamburguesa desplegable en móvil (PA-02) */}
        <div className="border-b-4 border-oro-claro bg-usco-dark">
          <nav id="menu-principal" aria-label="Navegación principal" className="mx-auto max-w-7xl px-2 sm:px-6">
            <ul className={`${menu ? 'flex' : 'hidden'} flex-col py-2 md:flex md:flex-row md:gap-1 md:py-0`}>
              <li className="px-3 pb-2 text-xs text-oro-claro md:hidden">{usuario?.nombre} · {GRUPOS[grupo]?.etiqueta} · {sede?.nombre}</li>
              {grupo === 'COORDINADOR' && <li className="px-3 pb-2 md:hidden"><SelectorSede /></li>}
              {items.map(({ to, texto, icono: Ic, fin }) => (
                <li key={to}>
                  <NavLink to={ruta(to)} end={fin} onClick={() => setMenu(false)}
                    className={({ isActive }) => `flex min-h-[48px] items-center gap-2 px-4 text-sm font-bold transition-colors md:border-b-4 md:-mb-1 ${isActive ? 'bg-white/10 text-white md:border-oro-claro' : 'text-white/80 hover:bg-white/5 hover:text-white md:border-transparent'}`}>
                    <Ic className="h-4 w-4" aria-hidden="true" /> {texto}
                  </NavLink>
                </li>
              ))}
            </ul>
          </nav>
        </div>
      </header>

      <main id="contenido" key={loc.pathname} className="mx-auto w-full max-w-7xl flex-1 px-4 py-6 sm:px-6">
        <Outlet />
      </main>

      <footer className="border-t border-slate-200 bg-white">
        <div className="mx-auto flex max-w-7xl flex-col gap-1 px-4 py-4 text-xs text-pizarra sm:flex-row sm:items-center sm:justify-between sm:px-6">
          <p>© 2026 Universidad Surcolombiana · Facultad de Ingeniería · Programa de Ingeniería de Software · {sede?.nombre ?? 'Sede Neiva'}, Huila</p>
          <p>USCO LabControl v1.1.0 · Tratamiento de datos personales conforme a la Ley 1581 de 2012</p>
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
