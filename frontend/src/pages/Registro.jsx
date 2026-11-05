import { useEffect, useRef, useState } from 'react'
import { Link, Navigate, useNavigate } from 'react-router-dom'
import { ArrowLeft, Eye, EyeOff, MailCheck, MapPin, RotateCw, ShieldCheck, Terminal, UserPlus } from 'lucide-react'
import { api } from '../api/index.js'
import { useAuth } from '../context/AuthContext.jsx'
import { useSede } from '../context/SedeContext.jsx'
import { REGEX_CORREO, homeDe } from '../lib/constantes.js'
import { Campo, ErrorCaja } from '../components/ui.jsx'
import ConexionBanner from '../components/ConexionBanner.jsx'
import BotonTema from '../components/BotonTema.jsx'

const PROGRAMAS = ['Ingeniería de Software', 'Ingeniería Electrónica', 'Ingeniería Civil', 'Ingeniería Agrícola', 'Ingeniería de Petróleos', 'Ingeniería Agroindustrial']
const VACIO = { nombre: '', correo: '', documento: '', codigoEstudiantil: '', programa: '', password: '', confirmar: '' }
const CLAVE_OK = (c) => c.length >= 8 && c.length <= 72 && /[A-Za-z]/.test(c) && /\d/.test(c)
const KEY = 'usco.labcontrol.registro'

/**
 * Registro de estudiantes en dos pasos:
 *  1. Datos + contraseña → el servidor envía un código de 6 dígitos al correo @usco.edu.co.
 *  2. Código → la cuenta se crea en la base de datos y la sesión queda iniciada.
 * Hasta completar el paso 2 la persona NO existe como usuario y no puede ingresar.
 */
export default function Registro() {
  const { usuario, abrirSesion } = useAuth()
  const { sede, slug, ruta, inicio } = useSede()
  const nav = useNavigate()
  const [f, setF] = useState(VACIO)
  const [errores, setErrores] = useState({})
  const [errorSrv, setErrorSrv] = useState('')
  const [enviando, setEnviando] = useState(false)
  const [ver, setVer] = useState(false)
  // Paso 2: { correo, minutos, correoEnviado }
  const [pendiente, setPendiente] = useState(() => { try { return JSON.parse(sessionStorage.getItem(KEY)) } catch { return null } })
  const [codigo, setCodigo] = useState('')
  const [espera, setEspera] = useState(0)
  const [aviso, setAviso] = useState('')
  const refCodigo = useRef(null)

  useEffect(() => { try { pendiente ? sessionStorage.setItem(KEY, JSON.stringify(pendiente)) : sessionStorage.removeItem(KEY) } catch { /* noop */ } }, [pendiente])
  useEffect(() => { if (espera <= 0) return undefined; const t = setTimeout(() => setEspera((s) => s - 1), 1000); return () => clearTimeout(t) }, [espera])
  useEffect(() => { if (pendiente) setTimeout(() => refCodigo.current?.focus(), 50) }, [pendiente])

  if (usuario) return <Navigate to={homeDe(usuario.grupo, usuario.sede ?? slug)} replace />

  const set = (k) => (e) => setF((x) => ({ ...x, [k]: e.target.value }))

  const registrar = async (e) => {
    e.preventDefault()
    const er = {}
    const nombre = f.nombre.trim()
    if (nombre.length < 5 || !nombre.includes(' ')) er.nombre = 'Escribe tus nombres y apellidos'
    if (!REGEX_CORREO.test(f.correo.trim())) er.correo = 'Usa tu correo institucional @usco.edu.co'
    if (!/^\d{5,15}$/.test(f.documento.trim())) er.documento = 'Entre 5 y 15 dígitos, sin puntos'
    if (!/^\d{6,20}$/.test(f.codigoEstudiantil.trim())) er.codigoEstudiantil = 'Solo números (ej. 20232215593)'
    if (f.programa.trim().length < 3) er.programa = 'Indica tu programa académico'
    if (!CLAVE_OK(f.password)) er.password = 'Mínimo 8 caracteres, con letras y números'
    else if (f.password !== f.confirmar) er.confirmar = 'Las contraseñas no coinciden'
    setErrores(er)
    if (Object.keys(er).length) return
    setEnviando(true); setErrorSrv('')
    try {
      const r = await api.registro({ nombre, correo: f.correo.trim().toLowerCase(), documento: f.documento.trim(),
        codigoEstudiantil: f.codigoEstudiantil.trim(), programa: f.programa.trim(), password: f.password })
      setPendiente({ correo: r.correo, minutos: r.minutosVigencia, correoEnviado: r.correoEnviado })
      setEspera(r.reenviarEnSegundos); setCodigo(''); setAviso('')
      setF((x) => ({ ...x, password: '', confirmar: '' }))
    } catch (err) { setErrorSrv(err.message) } finally { setEnviando(false) }
  }

  const verificar = async (e) => {
    e.preventDefault()
    if (!/^\d{6}$/.test(codigo)) { setErrorSrv('El código tiene 6 dígitos'); return }
    setEnviando(true); setErrorSrv(''); setAviso('')
    try {
      const r = await api.verificarRegistro(pendiente.correo, codigo)
      setPendiente(null)
      const u = abrirSesion(r)
      nav(homeDe(u.grupo, u.sede ?? slug), { replace: true })
    } catch (err) { setErrorSrv(err.message); setCodigo('') } finally { setEnviando(false) }
  }

  const reenviar = async () => {
    setErrorSrv(''); setAviso('')
    try {
      const r = await api.reenviarCodigo(pendiente.correo)
      setPendiente((p) => ({ ...p, correoEnviado: r.correoEnviado }))
      setEspera(r.reenviarEnSegundos); setCodigo('')
      setAviso('Te enviamos un código nuevo. El anterior ya no sirve.')
    } catch (err) { setErrorSrv(err.message) }
  }

  return (
    <div className="flex min-h-screen flex-col bg-fondo">
      <ConexionBanner />
      <header className="bg-usco text-white">
        <div className="mx-auto flex max-w-3xl items-center justify-between gap-3 px-5 py-4">
          <Link to={ruta('/login')} aria-label="Volver al ingreso"><img src="/logo-usco.png" alt="Universidad Surcolombiana" className="h-11 w-auto" /></Link>
          <BotonTema />
        </div>
        <div className="h-1 bg-oro-claro" />
      </header>

      <main className="mx-auto w-full max-w-3xl flex-1 px-5 py-8">
        <Link to={ruta('/login')} className="inline-flex items-center gap-1 text-sm font-semibold text-pizarra hover:text-usco hover:underline"><ArrowLeft className="h-4 w-4" aria-hidden="true" /> Volver al ingreso</Link>
        <div className="mt-3 flex flex-wrap items-center gap-2">
          <span className="inline-flex items-center gap-1.5 rounded-full bg-usco-soft px-3 py-1 text-xs font-bold text-usco-darker ring-1 ring-usco/20"><MapPin className="h-3.5 w-3.5" aria-hidden="true" /> {sede?.nombre}</span>
        </div>
        <p className="mt-3 text-sm font-bold uppercase tracking-widest text-usco">Registro de estudiantes</p>
        <h1 className="mt-1 text-3xl font-extrabold">Crea tu cuenta de LabControl</h1>

        {/* Indicador de pasos */}
        <ol className="mt-5 flex items-center gap-3 text-sm font-bold" aria-label="Pasos del registro">
          {[['1', 'Tus datos', !pendiente], ['2', 'Código del correo', !!pendiente]].map(([n, t, act], i) => (
            <li key={n} className="flex items-center gap-2" aria-current={act ? 'step' : undefined}>
              {i > 0 && <span className="h-px w-8 bg-slate-300" aria-hidden="true" />}
              <span className={`flex h-7 w-7 items-center justify-center rounded-full ${act ? 'bg-usco text-white' : 'bg-slate-200 text-pizarra'}`}>{n}</span>
              <span className={act ? 'text-slate-900' : 'text-pizarra'}>{t}</span>
            </li>
          ))}
        </ol>

        {!pendiente ? (
          <form onSubmit={registrar} noValidate className="card mt-6 grid gap-4 p-5 sm:grid-cols-2 sm:p-6">
            <div className="sm:col-span-2"><Campo etiqueta="Nombre completo" htmlFor="r-nombre" error={errores.nombre}><input id="r-nombre" autoComplete="name" className={`input ${errores.nombre ? 'input-error' : ''}`} value={f.nombre} onChange={set('nombre')} maxLength={120} placeholder="Nombres y apellidos" /></Campo></div>
            <div className="sm:col-span-2"><Campo etiqueta="Correo institucional" htmlFor="r-correo" error={errores.correo} ayuda="A este correo llegará el código de verificación"><input id="r-correo" type="email" inputMode="email" autoComplete="email" className={`input ${errores.correo ? 'input-error' : ''}`} value={f.correo} onChange={set('correo')} placeholder="u20232215593@usco.edu.co" /></Campo></div>
            <Campo etiqueta="Documento de identidad" htmlFor="r-doc" error={errores.documento}><input id="r-doc" inputMode="numeric" className={`input ${errores.documento ? 'input-error' : ''}`} value={f.documento} onChange={set('documento')} maxLength={15} /></Campo>
            <Campo etiqueta="Código estudiantil" htmlFor="r-cod" error={errores.codigoEstudiantil}><input id="r-cod" inputMode="numeric" className={`input font-mono ${errores.codigoEstudiantil ? 'input-error' : ''}`} value={f.codigoEstudiantil} onChange={set('codigoEstudiantil')} maxLength={20} placeholder="20232215593" /></Campo>
            <div className="sm:col-span-2">
              <Campo etiqueta="Programa académico" htmlFor="r-prog" error={errores.programa}>
                <input id="r-prog" list="r-programas" className={`input ${errores.programa ? 'input-error' : ''}`} value={f.programa} onChange={set('programa')} maxLength={80} placeholder="Ingeniería de Software" />
                <datalist id="r-programas">{PROGRAMAS.map((p) => <option key={p} value={p} />)}</datalist>
              </Campo>
            </div>
            <Campo etiqueta="Contraseña" htmlFor="r-pass" error={errores.password} ayuda="Mínimo 8 caracteres, con letras y números">
              <div className="relative">
                <input id="r-pass" type={ver ? 'text' : 'password'} autoComplete="new-password" className={`input pr-12 ${errores.password ? 'input-error' : ''}`} value={f.password} onChange={set('password')} maxLength={72} />
                <button type="button" onClick={() => setVer((v) => !v)} aria-label={ver ? 'Ocultar contraseña' : 'Mostrar contraseña'} className="absolute right-1 top-1/2 flex h-10 w-10 -translate-y-1/2 items-center justify-center rounded-lg text-pizarra hover:bg-slate-100">{ver ? <EyeOff className="h-5 w-5" /> : <Eye className="h-5 w-5" />}</button>
              </div>
            </Campo>
            <Campo etiqueta="Confirmar contraseña" htmlFor="r-conf" error={errores.confirmar}><input id="r-conf" type={ver ? 'text' : 'password'} autoComplete="new-password" className={`input ${errores.confirmar ? 'input-error' : ''}`} value={f.confirmar} onChange={set('confirmar')} maxLength={72} /></Campo>
            <div className="sm:col-span-2 space-y-3">
              <ErrorCaja mensaje={errorSrv} />
              <button type="submit" disabled={enviando} className="btn-primary w-full !min-h-[48px] text-[15px]"><MailCheck className="h-5 w-5" aria-hidden="true" /> {enviando ? 'Enviando código…' : 'Enviar código a mi correo'}</button>
              <p className="text-center text-xs text-pizarra">Tu cuenta solo se crea cuando confirmes el código. Docentes y auxiliares: la cuenta la crea la Coordinación de Laboratorios.</p>
            </div>
          </form>
        ) : (
          <form onSubmit={verificar} noValidate className="card mt-6 space-y-4 p-5 sm:p-6">
            <div className="flex items-start gap-3">
              <span className="flex h-11 w-11 shrink-0 items-center justify-center rounded-xl bg-usco-soft text-usco"><ShieldCheck className="h-6 w-6" aria-hidden="true" /></span>
              <div>
                <h2 className="text-lg font-extrabold">Revisa tu correo</h2>
                <p className="text-sm text-pizarra">Enviamos un código de 6 dígitos a <strong className="text-slate-900">{pendiente.correo}</strong>. Vence en {pendiente.minutos} minutos.</p>
              </div>
            </div>
            {!pendiente.correoEnviado && (
              <p role="status" className="flex items-start gap-2 rounded-lg border border-amber-600 bg-amber-50 px-3 py-2.5 text-sm text-amber-900">
                <Terminal className="mt-0.5 h-4 w-4 shrink-0" aria-hidden="true" />
                <span><strong>Modo local:</strong> el servidor de correo no está configurado, así que el código se escribió en la consola del backend (busca «CORREO NO ENVIADO»).</span>
              </p>
            )}
            {aviso && <p role="status" className="rounded-lg bg-emerald-50 px-3 py-2 text-sm font-semibold text-emerald-900">{aviso}</p>}
            <Campo etiqueta="Código de verificación" htmlFor="r-codigo">
              <input id="r-codigo" ref={refCodigo} inputMode="numeric" autoComplete="one-time-code" maxLength={6} value={codigo}
                onChange={(e) => setCodigo(e.target.value.replace(/\D/g, '').slice(0, 6))}
                className="input text-center font-mono text-3xl font-extrabold tracking-[0.5em]" placeholder="••••••" />
            </Campo>
            <ErrorCaja mensaje={errorSrv} />
            <button type="submit" disabled={enviando || codigo.length !== 6} className="btn-primary w-full !min-h-[48px] text-[15px]"><UserPlus className="h-5 w-5" aria-hidden="true" /> {enviando ? 'Verificando…' : 'Verificar y crear mi cuenta'}</button>
            <div className="flex flex-wrap items-center justify-between gap-2 text-sm">
              <button type="button" className="font-semibold text-pizarra hover:text-usco hover:underline" onClick={() => { setPendiente(null); setErrorSrv(''); setAviso('') }}>Corregir mis datos</button>
              <button type="button" disabled={espera > 0} onClick={reenviar} className="inline-flex items-center gap-1 font-bold text-usco hover:underline disabled:cursor-not-allowed disabled:text-slate-400 disabled:no-underline">
                <RotateCw className="h-4 w-4" aria-hidden="true" /> {espera > 0 ? `Reenviar código en ${espera} s` : 'Reenviar código'}
              </button>
            </div>
          </form>
        )}
        <p className="mt-6 text-center text-sm text-pizarra">¿Ya tienes cuenta? <Link to={ruta('/login')} className="font-bold text-usco hover:underline">Inicia sesión</Link></p>
      </main>
    </div>
  )
}
