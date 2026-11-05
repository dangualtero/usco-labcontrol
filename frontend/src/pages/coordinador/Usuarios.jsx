import { useState } from 'react'
import { KeyRound, Plus, RefreshCw, Search, UserCheck, UserX } from 'lucide-react'
import { api } from '../../api/index.js'
import { useAuth } from '../../context/AuthContext.jsx'
import { useToast } from '../../context/ToastContext.jsx'
import { useSede } from '../../context/SedeContext.jsx'
import { useDatos } from '../../lib/hooks.js'
import { REGEX_CORREO, norm } from '../../lib/constantes.js'
import { Campo, Cargando, ErrorCaja, Modal } from '../../components/ui.jsx'

const ROLES = { ESTUDIANTE: 'Estudiante', DOCENTE: 'Docente', AUXILIAR: 'Auxiliar', COORDINADOR: 'Coordinador' }
const TONO = { ESTUDIANTE: 'bg-blue-50 text-blue-900 border-blue-600', DOCENTE: 'bg-violet-50 text-violet-900 border-violet-600', AUXILIAR: 'bg-amber-50 text-amber-900 border-amber-600', COORDINADOR: 'bg-usco-soft text-usco-darker border-usco' }
const NUEVO = { nombre: '', correo: '', rol: 'ESTUDIANTE', documento: '', codigoEstudiantil: '', programa: '', password: '' }
const claveOk = (c) => c.length >= 8 && /[A-Za-z]/.test(c) && /\d/.test(c)

/** Gestión de usuarios (Coordinador): alta con rol, activar/desactivar y restablecer contraseña. */
export default function Usuarios() {
  const { usuario } = useAuth()
  const toast = useToast()
  const { sede } = useSede()
  const { datos, error, cargando, recargar } = useDatos(() => api.usuarios(), [], { cada: 30000 })
  const [q, setQ] = useState('')
  const [rol, setRol] = useState('todos')
  const [nuevo, setNuevo] = useState(null)
  const [errores, setErrores] = useState({})
  const [errorSrv, setErrorSrv] = useState('')
  const [enviando, setEnviando] = useState(false)
  const [clave, setClave] = useState(null)
  const [nuevaClave, setNuevaClave] = useState('')
  const [errClave, setErrClave] = useState('')

  const nq = norm(q)
  const lista = (datos ?? []).filter((u) => (rol === 'todos' || u.rol === rol) && (!nq || norm(`${u.nombre} ${u.correo} ${u.documento} ${u.codigoEstudiantil ?? ''}`).includes(nq)))

  const set = (k) => (e) => setNuevo((x) => ({ ...x, [k]: e.target.value }))

  const crear = async (e) => {
    e.preventDefault()
    const er = {}
    if (nuevo.nombre.trim().length < 3) er.nombre = 'El nombre es obligatorio'
    if (!REGEX_CORREO.test(nuevo.correo.trim())) er.correo = 'Formato de correo no válido (@usco.edu.co)'
    if (!/^\d{5,15}$/.test(nuevo.documento.trim())) er.documento = 'Entre 5 y 15 dígitos'
    if (!claveOk(nuevo.password)) er.password = 'Mínimo 8 caracteres, con letras y números'
    setErrores(er)
    if (Object.keys(er).length) return
    setEnviando(true); setErrorSrv('')
    try {
      await api.crearUsuario({ ...nuevo, correo: nuevo.correo.trim().toLowerCase() })
      toast.exito('Usuario creado', `${nuevo.nombre} · ${ROLES[nuevo.rol]}`)
      setNuevo(null); recargar()
    } catch (err) { setErrorSrv(err.message) } finally { setEnviando(false) }
  }

  const alternar = async (u) => {
    try { await api.usuarioActivo(u.id, !u.activo); toast.exito(u.activo ? 'Cuenta desactivada' : 'Cuenta activada', u.nombre); recargar() }
    catch (e) { toast.error('No se pudo actualizar', e.message) }
  }

  const restablecer = async () => {
    if (!claveOk(nuevaClave)) { setErrClave('Mínimo 8 caracteres, con letras y números'); return }
    try { await api.usuarioClave(clave.id, nuevaClave); toast.exito('Contraseña restablecida', clave.nombre); setClave(null) }
    catch (e) { setErrClave(e.message) }
  }

  return (
    <div className="space-y-5">
      <div className="flex flex-col gap-3 sm:flex-row sm:items-end sm:justify-between">
        <div>
          <p className="text-xs font-bold uppercase tracking-widest text-usco">Administración · {sede?.nombre}</p>
          <h1 className="text-2xl font-extrabold sm:text-3xl">Usuarios y roles</h1>
          <p className="text-sm text-pizarra">El rol asignado aquí determina automáticamente el panel al que accede cada persona al iniciar sesión. Se listan los usuarios de la {sede?.nombre} y los coordinadores generales.</p>
        </div>
        <div className="flex shrink-0 gap-2">
          <button onClick={recargar} disabled={cargando} className="btn-outline" aria-label="Actualizar usuarios"><RefreshCw className={`h-4 w-4 ${cargando ? 'animate-spin' : ''}`} aria-hidden="true" /> Actualizar</button>
          <button onClick={() => { setNuevo(NUEVO); setErrores({}); setErrorSrv('') }} className="btn-primary"><Plus className="h-4 w-4" aria-hidden="true" /> Nuevo usuario</button>
        </div>
      </div>

      <section className="card grid gap-3 p-4 sm:grid-cols-[1fr_220px]" aria-label="Filtros">
        <div className="relative"><label htmlFor="u-q" className="sr-only">Buscar usuario</label><Search className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-pizarra" aria-hidden="true" />
          <input id="u-q" className="input pl-9" placeholder="Nombre, correo, documento o código" value={q} onChange={(e) => setQ(e.target.value)} /></div>
        <div><label htmlFor="u-r" className="sr-only">Rol</label>
          <select id="u-r" className="input" value={rol} onChange={(e) => setRol(e.target.value)}><option value="todos">Todos los roles</option>{Object.entries(ROLES).map(([k, v]) => <option key={k} value={k}>{v}</option>)}</select></div>
      </section>

      <ErrorCaja mensaje={error} />
      {!datos && !error && <Cargando />}

      {datos && (
        <div className="card overflow-hidden">
          <div className="overflow-x-auto">
            <table className="w-full min-w-[820px]">
              <thead><tr><th className="th">Usuario</th><th className="th">Rol</th><th className="th">Documento / código</th><th className="th">Estado</th><th className="th"><span className="sr-only">Acciones</span></th></tr></thead>
              <tbody>
                {lista.map((u) => (
                  <tr key={u.id} className={u.activo ? '' : 'bg-slate-50 text-slate-500'}>
                    <td className="td"><p className="font-bold text-slate-900">{u.nombre}</p><p className="text-xs text-pizarra">{u.correo}{u.programa ? ` · ${u.programa}` : ''}</p></td>
                    <td className="td"><span className={`inline-flex rounded-full border px-2.5 py-0.5 text-xs font-bold ${TONO[u.rol]}`}>{ROLES[u.rol]}</span></td>
                    <td className="td font-mono text-[13px]">{u.documento}{u.codigoEstudiantil ? <span className="block text-pizarra">{u.codigoEstudiantil}</span> : null}</td>
                    <td className="td">{u.activo ? <span className="inline-flex items-center gap-1 text-xs font-bold text-emerald-800"><UserCheck className="h-3.5 w-3.5" aria-hidden="true" /> Activo</span> : <span className="inline-flex items-center gap-1 text-xs font-bold text-slate-600"><UserX className="h-3.5 w-3.5" aria-hidden="true" /> Desactivado</span>}</td>
                    <td className="td">
                      <div className="flex justify-end gap-1.5">
                        <button className="btn-outline btn-sm" onClick={() => { setClave(u); setNuevaClave(''); setErrClave('') }}><KeyRound className="h-4 w-4" aria-hidden="true" /> Contraseña</button>
                        {u.id !== usuario.id && <button className="btn-outline btn-sm" onClick={() => alternar(u)}>{u.activo ? 'Desactivar' : 'Activar'}</button>}
                      </div>
                    </td>
                  </tr>
                ))}
                {lista.length === 0 && <tr><td className="td py-8 text-center text-pizarra" colSpan={5}>No se encontraron usuarios</td></tr>}
              </tbody>
            </table>
          </div>
        </div>
      )}

      <Modal abierto={!!nuevo} onCerrar={() => setNuevo(null)} titulo="Nuevo usuario" subtitulo={nuevo?.rol === 'COORDINADOR' ? 'Coordinador general: administra todas las sedes' : `Pertenecerá a la ${sede?.nombre ?? 'sede actual'} e ingresará por /${sede?.slug ?? ''}`} ancho="max-w-2xl"
        pie={<>
          <button type="button" className="btn-outline" onClick={() => setNuevo(null)}>Cancelar</button>
          <button type="submit" form="form-usuario" className="btn-primary" disabled={enviando}><Plus className="h-4 w-4" aria-hidden="true" /> {enviando ? 'Creando…' : 'Crear usuario'}</button>
        </>}>
        {nuevo && (
          <form id="form-usuario" onSubmit={crear} noValidate className="grid gap-4 md:grid-cols-2">
            <Campo etiqueta="Nombre completo" htmlFor="n-nombre" error={errores.nombre}><input id="n-nombre" className={`input ${errores.nombre ? 'input-error' : ''}`} value={nuevo.nombre} onChange={set('nombre')} maxLength={120} /></Campo>
            <Campo etiqueta="Correo institucional" htmlFor="n-correo" error={errores.correo}><input id="n-correo" type="email" className={`input ${errores.correo ? 'input-error' : ''}`} value={nuevo.correo} onChange={set('correo')} placeholder="nombre.apellido@usco.edu.co" /></Campo>
            <Campo etiqueta="Rol" htmlFor="n-rol"><select id="n-rol" className="input" value={nuevo.rol} onChange={set('rol')}>{Object.entries(ROLES).map(([k, v]) => <option key={k} value={k}>{v}</option>)}</select></Campo>
            <Campo etiqueta="Documento" htmlFor="n-doc" error={errores.documento}><input id="n-doc" inputMode="numeric" className={`input ${errores.documento ? 'input-error' : ''}`} value={nuevo.documento} onChange={set('documento')} maxLength={15} /></Campo>
            <Campo etiqueta="Código estudiantil (opcional)" htmlFor="n-cod"><input id="n-cod" inputMode="numeric" className="input" value={nuevo.codigoEstudiantil} onChange={set('codigoEstudiantil')} maxLength={20} /></Campo>
            <Campo etiqueta="Programa (opcional)" htmlFor="n-prog"><input id="n-prog" className="input" value={nuevo.programa} onChange={set('programa')} maxLength={80} /></Campo>
            <div className="md:col-span-2"><Campo etiqueta="Contraseña inicial" htmlFor="n-pass" error={errores.password} ayuda="La persona puede cambiarla desde «Mi cuenta». Mínimo 8 caracteres, con letras y números."><input id="n-pass" type="text" autoComplete="off" className={`input font-mono ${errores.password ? 'input-error' : ''}`} value={nuevo.password} onChange={set('password')} /></Campo></div>
            <div className="md:col-span-2"><ErrorCaja mensaje={errorSrv} /></div>
          </form>
        )}
      </Modal>

      <Modal abierto={!!clave} onCerrar={() => setClave(null)} titulo="Restablecer contraseña" subtitulo={clave ? `${clave.nombre} · ${clave.correo}` : undefined} ancho="max-w-md"
        pie={<>
          <button className="btn-outline" onClick={() => setClave(null)}>Cancelar</button>
          <button className="btn-primary" onClick={restablecer}><KeyRound className="h-4 w-4" aria-hidden="true" /> Restablecer</button>
        </>}>
        <Campo etiqueta="Nueva contraseña" htmlFor="rc-clave" error={errClave} ayuda="Entrégala a la persona por un canal seguro; podrá cambiarla desde «Mi cuenta».">
          <input id="rc-clave" type="text" autoComplete="off" className={`input font-mono ${errClave ? 'input-error' : ''}`} value={nuevaClave} onChange={(e) => setNuevaClave(e.target.value)} />
        </Campo>
      </Modal>
    </div>
  )
}
