import { useEffect, useState } from 'react'
import { KeyRound, Pencil, Plus, RefreshCw, Search, ShieldCheck, UserCheck, UserCog, UserRound, UserX } from 'lucide-react'
import { api } from '../../api/index.js'
import { useAuth } from '../../context/AuthContext.jsx'
import { useToast } from '../../context/ToastContext.jsx'
import { useSede } from '../../context/SedeContext.jsx'
import { useDatos, useLabsRef } from '../../lib/hooks.js'
import { REGEX_CORREO, ROLES, norm } from '../../lib/constantes.js'
import { Campo, Cargando, ErrorCaja, Modal } from '../../components/ui.jsx'
import { Encabezado } from '../../components/Estados.jsx'

const TONO = { ADMINISTRADOR: 'bg-usco-soft text-usco-darker border-usco', COORDINADOR_LAB: 'bg-blue-50 text-blue-900 border-blue-600', MONITOR: 'bg-amber-50 text-amber-900 border-amber-600' }
const ICONO = { ADMINISTRADOR: ShieldCheck, COORDINADOR_LAB: UserCog, MONITOR: UserRound }
const DESCRIPCION = {
  ADMINISTRADOR: 'Control total de los módulos y de todos los laboratorios y sedes.',
  COORDINADOR_LAB: 'Docente responsable de sus laboratorios: caracterización, inventario, validación de novedades y solicitudes.',
  MONITOR: 'Apoyo operativo bajo supervisión del coordinador: consulta, registro preliminar y borradores de solicitudes.',
}
const claveOk = (c) => c.length >= 8 && /[A-Za-z]/.test(c) && /\d/.test(c)

/** Usuarios y roles (Administrador): crea cuentas, asigna el rol y uno o varios laboratorios, activa y restablece contraseñas. */
export default function Usuarios() {
  const { usuario } = useAuth()
  const toast = useToast()
  const { sede } = useSede()
  const { datos, error, cargando, recargar } = useDatos(() => api.usuarios(), [], { cada: 30000 })
  const [q, setQ] = useState('')
  const [rol, setRol] = useState('todos')
  const [form, setForm] = useState(null)
  const [clave, setClave] = useState(null)
  const [nuevaClave, setNuevaClave] = useState('')
  const [errClave, setErrClave] = useState('')

  const nq = norm(q)
  const lista = (datos ?? []).filter((u) => (rol === 'todos' || u.rol === rol)
    && (!nq || norm(`${u.nombre} ${u.correo} ${u.documento} ${u.laboratorios.map((l) => l.nombre).join(' ')}`).includes(nq)))

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
      <Encabezado modulo={`Tablero de control y parametrización · ${sede?.nombre}`} titulo="Usuarios y roles"
        detalle={`Coordinadores y monitores pertenecen a la ${sede?.nombre ?? 'sede'} y solo ven sus laboratorios asignados. El Administrador general ve todo.`}>
        <button onClick={recargar} disabled={cargando} className="btn-outline" aria-label="Actualizar usuarios"><RefreshCw className={`h-4 w-4 ${cargando ? 'animate-spin' : ''}`} aria-hidden="true" /> Actualizar</button>
        <button onClick={() => setForm({})} className="btn-primary"><Plus className="h-4 w-4" aria-hidden="true" /> Nuevo usuario</button>
      </Encabezado>

      <section className="card grid gap-3 p-4 sm:grid-cols-[1fr_260px]" aria-label="Filtros">
        <div className="relative"><label htmlFor="u-q" className="sr-only">Buscar usuario</label><Search className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-pizarra" aria-hidden="true" />
          <input id="u-q" className="input pl-9" placeholder="Nombre, correo, documento o laboratorio" value={q} onChange={(e) => setQ(e.target.value)} /></div>
        <div><label htmlFor="u-r" className="sr-only">Rol</label>
          <select id="u-r" className="input" value={rol} onChange={(e) => setRol(e.target.value)}><option value="todos">Todos los roles</option>{Object.entries(ROLES).map(([k, v]) => <option key={k} value={k}>{v.etiqueta}</option>)}</select></div>
      </section>

      <ErrorCaja mensaje={error} />
      {!datos && !error && <Cargando />}
      {datos && (
        <div className="card overflow-hidden">
          <div className="relative overflow-x-auto">
            <table className="w-full min-w-[860px]">
              <thead><tr><th className="th">Usuario</th><th className="th">Rol</th><th className="th">Laboratorios</th><th className="th">Estado</th><th className="th"><span className="sr-only">Acciones</span></th></tr></thead>
              <tbody>
                {lista.map((u) => {
                  const Ic = ICONO[u.rol] ?? UserRound
                  return (
                    <tr key={u.id} className={u.activo ? '' : 'bg-slate-50 text-slate-500'}>
                      <td className="td"><p className="font-bold text-slate-900">{u.nombre}</p><p className="text-xs text-pizarra">{u.correo} · {u.documento}{u.programa ? ` · ${u.programa}` : ''}</p></td>
                      <td className="td"><span className={`inline-flex items-center gap-1 rounded-full border px-2.5 py-0.5 text-xs font-bold ${TONO[u.rol]}`}><Ic className="h-3.5 w-3.5" aria-hidden="true" /> {u.rolTexto}</span></td>
                      <td className="td text-sm">{u.rol === 'ADMINISTRADOR' ? <span className="text-pizarra">Todos (todas las sedes)</span> : u.laboratorios.length ? u.laboratorios.map((l) => l.nombre).join(', ') : <span className="font-semibold text-alerta-texto">Sin laboratorios</span>}</td>
                      <td className="td">{u.activo ? <span className="inline-flex items-center gap-1 text-xs font-bold text-emerald-800"><UserCheck className="h-3.5 w-3.5" aria-hidden="true" /> Activo</span> : <span className="inline-flex items-center gap-1 text-xs font-bold text-slate-600"><UserX className="h-3.5 w-3.5" aria-hidden="true" /> Desactivado</span>}</td>
                      <td className="td">
                        <div className="flex justify-end gap-1.5">
                          <button className="btn-outline btn-sm" onClick={() => setForm({ u })}><Pencil className="h-4 w-4" aria-hidden="true" /> Editar</button>
                          <button className="btn-outline btn-sm" onClick={() => { setClave(u); setNuevaClave(''); setErrClave('') }}><KeyRound className="h-4 w-4" aria-hidden="true" /> Contraseña</button>
                          {u.id !== usuario.id && <button className="btn-outline btn-sm" onClick={() => alternar(u)}>{u.activo ? 'Desactivar' : 'Activar'}</button>}
                        </div>
                      </td>
                    </tr>
                  )
                })}
                {lista.length === 0 && <tr><td className="td py-8 text-center text-pizarra" colSpan={5}>No se encontraron usuarios</td></tr>}
              </tbody>
            </table>
          </div>
        </div>
      )}

      <UsuarioModal abierto={!!form} u={form?.u} propio={form?.u?.id === usuario.id} onCerrar={() => setForm(null)} onHecho={() => { setForm(null); recargar() }} />

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

function UsuarioModal({ abierto, u, propio, onCerrar, onHecho }) {
  const toast = useToast()
  const { sede } = useSede()
  const labs = useLabsRef([abierto])
  const nuevo = !u
  const [f, setF] = useState({})
  const [err, setErr] = useState({})
  const [errSrv, setErrSrv] = useState('')
  const [enviando, setEnviando] = useState(false)
  useEffect(() => {
    if (!abierto) return
    setErr({}); setErrSrv('')
    setF(u ? { nombre: u.nombre, correo: u.correo, rol: u.rol, documento: u.documento, programa: u.programa ?? '', telefono: u.telefono ?? '', laboratorios: u.laboratorios.map((l) => l.id) }
      : { nombre: '', correo: '', rol: 'MONITOR', documento: '', programa: '', telefono: '', password: '', laboratorios: [] })
  }, [abierto, u])
  const set = (k) => (e) => setF((x) => ({ ...x, [k]: e.target.value }))
  const marcar = (id) => setF((x) => ({ ...x, laboratorios: x.laboratorios.includes(id) ? x.laboratorios.filter((i) => i !== id) : [...x.laboratorios, id] }))
  const conLabs = f.rol !== 'ADMINISTRADOR'

  const guardar = async (e) => {
    e.preventDefault()
    const er = {}
    if ((f.nombre ?? '').trim().length < 3) er.nombre = 'El nombre es obligatorio'
    if (nuevo && !REGEX_CORREO.test((f.correo ?? '').trim())) er.correo = 'Formato de correo no válido (@usco.edu.co)'
    if (!/^\d{5,15}$/.test((f.documento ?? '').trim())) er.documento = 'Entre 5 y 15 dígitos'
    if (nuevo && !claveOk(f.password ?? '')) er.password = 'Mínimo 8 caracteres, con letras y números'
    if (conLabs && !f.laboratorios.length) er.laboratorios = 'Selecciona al menos un laboratorio'
    setErr(er)
    if (Object.keys(er).length) return
    setEnviando(true); setErrSrv('')
    const cuerpo = { nombre: f.nombre.trim(), rol: f.rol, documento: f.documento.trim(), programa: f.programa || null, telefono: f.telefono || null, laboratorios: conLabs ? f.laboratorios : [] }
    try {
      if (nuevo) await api.crearUsuario({ ...cuerpo, correo: f.correo.trim().toLowerCase(), password: f.password })
      else await api.actualizarUsuario(u.id, cuerpo)
      toast.exito(nuevo ? 'Usuario creado' : 'Usuario actualizado', `${cuerpo.nombre} · ${ROLES[cuerpo.rol].etiqueta}`)
      onHecho()
    } catch (er2) { setErrSrv(er2.message) } finally { setEnviando(false) }
  }

  return (
    <Modal abierto={abierto} onCerrar={onCerrar} ancho="max-w-2xl" titulo={nuevo ? 'Nuevo usuario' : 'Editar usuario'} subtitulo={u ? u.correo : `Coordinadores y monitores ingresarán por /${sede?.slug ?? ''}`}
      pie={<>
        <button type="button" className="btn-outline" onClick={onCerrar}>Cancelar</button>
        <button type="submit" form="form-usuario" className="btn-primary" disabled={enviando}>{enviando ? 'Guardando…' : nuevo ? 'Crear usuario' : 'Guardar cambios'}</button>
      </>}>
      <form id="form-usuario" onSubmit={guardar} noValidate className="grid gap-4 md:grid-cols-2">
        <fieldset className="md:col-span-2">
          <legend className="label">Rol</legend>
          <div className="grid gap-2 sm:grid-cols-3">
            {Object.entries(ROLES).map(([k, v]) => {
              const Ic = ICONO[k]
              const bloqueado = propio && k !== 'ADMINISTRADOR'
              return (
                <label key={k} className={`flex cursor-pointer flex-col gap-1 rounded-lg border-2 p-3 ${f.rol === k ? 'border-usco bg-usco-soft' : 'border-slate-200'} ${bloqueado ? 'cursor-not-allowed opacity-50' : ''}`}>
                  <span className="flex items-center gap-2 text-sm font-extrabold"><input type="radio" name="u-rol" checked={f.rol === k} disabled={bloqueado} onChange={() => setF((x) => ({ ...x, rol: k }))} className="accent-[#8F141B]" /> <Ic className="h-4 w-4 text-usco" aria-hidden="true" /> {v.etiqueta}</span>
                  <span className="text-xs text-pizarra">{DESCRIPCION[k]}</span>
                </label>
              )
            })}
          </div>
        </fieldset>
        <Campo etiqueta="Nombre completo" htmlFor="n-nombre" error={err.nombre}><input id="n-nombre" className={`input ${err.nombre ? 'input-error' : ''}`} value={f.nombre ?? ''} onChange={set('nombre')} maxLength={120} /></Campo>
        <Campo etiqueta="Correo institucional" htmlFor="n-correo" error={err.correo}><input id="n-correo" type="email" disabled={!nuevo} className={`input ${err.correo ? 'input-error' : ''}`} value={f.correo ?? ''} onChange={set('correo')} placeholder="nombre.apellido@usco.edu.co" /></Campo>
        <Campo etiqueta="Documento" htmlFor="n-doc" error={err.documento}><input id="n-doc" inputMode="numeric" className={`input ${err.documento ? 'input-error' : ''}`} value={f.documento ?? ''} onChange={set('documento')} maxLength={15} /></Campo>
        <Campo etiqueta="Teléfono (opcional)" htmlFor="n-tel"><input id="n-tel" className="input" value={f.telefono ?? ''} onChange={set('telefono')} maxLength={30} /></Campo>
        <div className="md:col-span-2"><Campo etiqueta="Programa, cargo o dependencia (opcional)" htmlFor="n-prog"><input id="n-prog" className="input" value={f.programa ?? ''} onChange={set('programa')} maxLength={80} /></Campo></div>
        {conLabs && (
          <fieldset className="md:col-span-2">
            <legend className="label">Laboratorios asignados ({sede?.nombre})</legend>
            <div className="grid gap-2 sm:grid-cols-2 lg:grid-cols-3">
              {labs.map((l) => (
                <label key={l.id} className={`flex min-h-[44px] cursor-pointer items-center gap-2 rounded-lg border-2 px-3 text-sm font-semibold ${f.laboratorios?.includes(l.id) ? 'border-usco bg-usco-soft' : 'border-slate-200'}`}>
                  <input type="checkbox" checked={!!f.laboratorios?.includes(l.id)} onChange={() => marcar(l.id)} className="h-4 w-4 accent-[#8F141B]" /> {l.nombre}
                </label>
              ))}
            </div>
            {err.laboratorios && <p role="alert" className="mt-1 text-sm font-semibold text-alerta-borde">{err.laboratorios}</p>}
          </fieldset>
        )}
        {nuevo && <div className="md:col-span-2"><Campo etiqueta="Contraseña inicial" htmlFor="n-pass" error={err.password} ayuda="La persona puede cambiarla desde «Mi cuenta». Mínimo 8 caracteres, con letras y números."><input id="n-pass" type="text" autoComplete="off" className={`input font-mono ${err.password ? 'input-error' : ''}`} value={f.password ?? ''} onChange={set('password')} /></Campo></div>}
        <div className="md:col-span-2"><ErrorCaja mensaje={errSrv} /></div>
      </form>
    </Modal>
  )
}
