import { useEffect, useState } from 'react'
import { Navigate, useLocation, useNavigate } from 'react-router-dom'
import { Eye, EyeOff, LogIn, TriangleAlert, CircleCheck, Radar, History, BellRing, ArrowUp } from 'lucide-react'
import { useAuth } from '../context/AuthContext.jsx'
import { REGEX_CORREO, homeDe } from '../lib/constantes.js'
import { Campo } from '../components/ui.jsx'
import ConexionBanner from '../components/ConexionBanner.jsx'
import BotonTema from '../components/BotonTema.jsx'

const PUNTOS = [
  { icono: Radar, t: 'Disponibilidad actualizada', d: 'Sabe qué equipo está libre antes de ir al laboratorio.' },
  { icono: History, t: 'Historial inmutable', d: 'Cada movimiento queda registrado y asociado a su responsable.' },
  { icono: BellRing, t: 'Alertas de devolución', d: 'Avisos visuales automáticos de préstamos vencidos.' },
]

export default function Login() {
  const { usuario, iniciar } = useAuth()
  const nav = useNavigate()
  const loc = useLocation()
  const [correo, setCorreo] = useState('')
  const [clave, setClave] = useState('')
  const [ver, setVer] = useState(false)
  const [recordar, setRecordar] = useState(false)
  const [errores, setErrores] = useState({})
  const [errorGlobal, setErrorGlobal] = useState('')
  const [cargando, setCargando] = useState(false)
  const [aviso, setAviso] = useState(null)
  const [mayus, setMayus] = useState(false)

  useEffect(() => {
    const c = loc.state?.cierre
    if (c === 'manual') setAviso({ ok: true, t: 'Sesión cerrada correctamente', d: 'Tu sesión se cerró de forma segura. Vuelve pronto.' })
    if (c === 'inactividad') setAviso({ ok: false, t: 'Sesión cerrada por seguridad', d: 'Se detectó inactividad prolongada.' })
    if (c === 'expirada') setAviso({ ok: false, t: 'Tu sesión expiró', d: 'Inicia sesión nuevamente para continuar.' })
    if (c) nav('/login', { replace: true, state: null })
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  // Redirección automática según el rol que devuelve la base de datos
  if (usuario) return <Navigate to={homeDe(usuario.grupo)} replace />

  const validar = () => {
    const e = {}
    const c = correo.trim()
    if (!c) e.correo = 'El correo institucional es obligatorio'                                 // PV-01
    else if (!REGEX_CORREO.test(c)) e.correo = 'Formato de correo no válido (@usco.edu.co)'     // PV-02
    if (!clave) e.clave = 'La contraseña es obligatoria'
    setErrores(e)
    return Object.keys(e).length === 0
  }

  const enviar = async (ev) => {
    ev.preventDefault()
    setErrorGlobal(''); setAviso(null)
    if (!validar()) return
    setCargando(true)
    try {
      const u = await iniciar({ email: correo.trim(), password: clave, recordar })
      nav(homeDe(u.grupo), { replace: true })
    } catch (e) {
      // 401: «Credenciales inválidas» genérico (PS-08). Status 0: la API no responde (mensaje real de conexión).
      setErrorGlobal(e.message)
      if (e.status !== 0) setClave('')
    } finally { setCargando(false) }
  }

  return (
    <div className="grid min-h-screen lg:grid-cols-[1.05fr_1fr]">
      {/* ---------- Panel institucional (izquierdo) ---------- */}
      <aside className="relative hidden flex-col justify-between overflow-hidden bg-usco p-10 text-white lg:flex xl:p-14">
        <div className="pointer-events-none absolute inset-0 opacity-[0.08]" style={{ backgroundImage: 'radial-gradient(#DFD4A6 1.2px, transparent 1.2px)', backgroundSize: '22px 22px' }} />
        <div className="pointer-events-none absolute -bottom-40 -left-32 h-[520px] w-[520px] rounded-full border-[44px] border-usco-dark/70" />
        <div className="pointer-events-none absolute -right-24 top-1/3 h-72 w-72 rounded-full border-[28px] border-oro-claro/10" />
        <div className="relative">
          <img src="/logo-usco.png" alt="Universidad Surcolombiana" className="h-16 w-auto" />
          <div className="mt-2 h-1 w-24 rounded bg-oro-claro" />
        </div>
        <div className="relative max-w-xl">
          <p className="mb-3 inline-block rounded-full border border-oro-claro/50 px-3 py-1 text-xs font-bold uppercase tracking-widest text-oro-claro">Facultad de Ingeniería · Sede Neiva</p>
          <h1 className="font-titulo text-5xl font-extrabold leading-[1.05] tracking-tight text-white xl:text-6xl">USCO <span className="text-oro-claro">LabControl</span></h1>
          <p className="mt-4 text-lg text-white/90">Gestión y trazabilidad de préstamos de equipos en los laboratorios de Electrónica, Petróleos, Civil y Agrícola.</p>
          <ul className="mt-9 space-y-4">
            {PUNTOS.map(({ icono: Ic, t, d }) => (
              <li key={t} className="flex items-start gap-3.5">
                <span className="flex h-10 w-10 shrink-0 items-center justify-center rounded-xl border border-oro-claro/40 bg-white/10"><Ic className="h-5 w-5 text-oro-claro" aria-hidden="true" /></span>
                <span><span className="block font-bold">{t}</span><span className="text-sm text-white/80">{d}</span></span>
              </li>
            ))}
          </ul>
        </div>
        <p className="relative text-xs text-white/70">Universidad Surcolombiana · Acreditada de Alta Calidad · Programa de Ingeniería de Software</p>
      </aside>

      {/* ---------- Formulario ---------- */}
      <main className="flex flex-col bg-fondo">
        <ConexionBanner />
        <div className="bg-usco px-5 py-4 lg:hidden"><img src="/logo-usco.png" alt="Universidad Surcolombiana" className="h-11 w-auto" /></div>
        <div className="h-1 bg-oro-claro lg:hidden" />
        <div className="flex justify-end px-5 pt-4 sm:px-10"><BotonTema variante="claro" conTexto /></div>
        <div className="flex flex-1 items-center justify-center px-5 pb-8 pt-2 sm:px-10">
          <div className="w-full max-w-[440px]">
            <p className="text-sm font-bold uppercase tracking-widest text-usco">Ingreso institucional</p>
            <h2 className="mt-1 text-3xl font-extrabold">Bienvenido a LabControl</h2>
            <p className="mt-1.5 text-sm text-pizarra">Ingresa con tu correo institucional <strong>@usco.edu.co</strong> y contraseña.</p>

            {aviso && (
              <div role="status" className={`aparecer mt-5 flex items-start gap-2.5 rounded-lg border-l-4 px-4 py-3 text-sm ${aviso.ok ? 'border-entrada bg-emerald-50 text-emerald-900' : 'border-alerta-borde bg-alerta-fondo text-alerta-texto'}`}>
                {aviso.ok ? <CircleCheck className="mt-0.5 h-5 w-5 shrink-0 text-entrada" aria-hidden="true" /> : <TriangleAlert className="mt-0.5 h-5 w-5 shrink-0" aria-hidden="true" />}
                <div><p className="font-bold">{aviso.ok ? '✓ ' : ''}{aviso.t}</p><p>{aviso.d}</p></div>
              </div>
            )}

            <form onSubmit={enviar} noValidate className="card mt-6 space-y-4 p-5 sm:p-6">
              <Campo etiqueta="Correo institucional" htmlFor="correo" error={errores.correo}>
                <input id="correo" type="email" inputMode="email" autoComplete="username" value={correo} onChange={(e) => setCorreo(e.target.value)}
                  placeholder="Ingresa tu correo institucional" autoFocus aria-invalid={!!errores.correo} aria-describedby={errores.correo ? 'correo-error' : undefined}
                  className={`input ${errores.correo ? 'input-error' : ''}`} />
              </Campo>
              <Campo etiqueta="Contraseña" htmlFor="clave" error={errores.clave}>
                <div className="relative">
                  <input id="clave" type={ver ? 'text' : 'password'} autoComplete="current-password" value={clave} onChange={(e) => setClave(e.target.value)}
                    placeholder="Ingresa tu contraseña" onKeyUp={(e) => setMayus(e.getModifierState?.('CapsLock') ?? false)} onBlur={() => setMayus(false)}
                    aria-invalid={!!errores.clave} className={`input pr-12 ${errores.clave ? 'input-error' : ''}`} />
                  <button type="button" onClick={() => setVer((v) => !v)} aria-label={ver ? 'Ocultar contraseña' : 'Mostrar contraseña'}
                    className="absolute right-1 top-1/2 flex h-10 w-10 -translate-y-1/2 items-center justify-center rounded-lg text-pizarra hover:bg-slate-100">
                    {ver ? <EyeOff className="h-5 w-5" /> : <Eye className="h-5 w-5" />}
                  </button>
                </div>
                {mayus && <p role="status" className="mt-1.5 flex items-center gap-1.5 text-xs font-semibold text-amber-700"><ArrowUp className="h-3.5 w-3.5" aria-hidden="true" /> Bloq Mayús está activado</p>}
              </Campo>

              <label className="flex min-h-[40px] cursor-pointer items-center gap-2.5 text-sm font-semibold text-pizarra-oscuro">
                <input type="checkbox" checked={recordar} onChange={(e) => setRecordar(e.target.checked)} className="h-4 w-4 rounded border-slate-300 accent-[#8F141B]" />
                Recordar mi sesión en este equipo
              </label>

              {errorGlobal && (
                <p role="alert" className="flex items-start gap-2 rounded-lg border border-alerta-borde bg-alerta-fondo px-3 py-2.5 text-sm font-semibold text-alerta-texto">
                  <TriangleAlert className="mt-0.5 h-4 w-4 shrink-0" aria-hidden="true" /> {errorGlobal}
                </p>
              )}

              <button type="submit" disabled={cargando} className="btn-primary w-full !min-h-[48px] text-[15px]">
                <LogIn className="h-5 w-5" aria-hidden="true" /> {cargando ? 'Verificando…' : 'Ingresar al sistema'}
              </button>
            </form>

            <p className="mt-5 text-center text-xs text-slate-500">¿Olvidaste tu contraseña? Solicita su restablecimiento a la Coordinación de Laboratorios.</p>
            <p className="mt-2 text-center text-[11px] text-slate-400">USCO LabControl · v1.1.0 · Conexión institucional</p>
          </div>
        </div>
      </main>
    </div>
  )
}
