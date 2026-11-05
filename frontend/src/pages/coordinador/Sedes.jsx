import { useState } from 'react'
import { Link } from 'react-router-dom'
import { ArrowRight, Building2, MapPin, Package, Pencil, Plus, Power, RefreshCw, Users, Wallet } from 'lucide-react'
import { api } from '../../api/index.js'
import { useToast } from '../../context/ToastContext.jsx'
import { refrescarSedes, useSede } from '../../context/SedeContext.jsx'
import { useDatos } from '../../lib/hooks.js'
import { pesos, slugDe } from '../../lib/constantes.js'
import { Campo, Cargando, Confirmar, ErrorCaja, Modal } from '../../components/ui.jsx'

const RESERVADOS = ['login', 'api', 'images', 'assets', 'sedes', 'coordinador', 'auxiliar', 'usuario', 'admin']

/** Sedes de la Universidad (Coordinador general): cada una con su dirección web, inventario, usuarios y presupuesto. */
export default function Sedes() {
  const toast = useToast()
  const { slug: actual } = useSede()
  const { datos, error, cargando, recargar } = useDatos(() => api.sedesAdmin(), [], { cada: 60000 })
  const [form, setForm] = useState(null)          // { slug?: string, nombre, ciudad, direccion }
  const [errores, setErrores] = useState({})
  const [errorSrv, setErrorSrv] = useState('')
  const [enviando, setEnviando] = useState(false)
  const [estado, setEstado] = useState(null)

  const editando = !!form?.slug
  const set = (k) => (e) => setForm((x) => ({ ...x, [k]: e.target.value }))
  const slugNuevo = form && !editando ? slugDe(form.ciudad) : ''

  const guardar = async (e) => {
    e.preventDefault()
    const er = {}
    const ciudad = form.ciudad.trim()
    if (!editando) {
      if (ciudad.length < 3) er.ciudad = 'Indica la ciudad o municipio (ej. Pitalito)'
      else if (!slugNuevo || RESERVADOS.includes(slugNuevo)) er.ciudad = 'Ese nombre no sirve como dirección web'
      else if ((datos ?? []).some((s) => s.sede.slug === slugNuevo)) er.ciudad = `Ya existe la sede /${slugNuevo}`
    }
    const nombre = form.nombre.trim()
    if (nombre && (nombre.length < 4 || nombre.length > 80)) er.nombre = 'Entre 4 y 80 caracteres'
    setErrores(er)
    if (Object.keys(er).length) return
    setEnviando(true); setErrorSrv('')
    const cuerpo = { nombre: nombre || null, ciudad, direccion: form.direccion.trim() || null }
    try {
      const r = editando ? await api.actualizarSede(form.slug, cuerpo) : await api.crearSede(cuerpo)
      toast.exito(editando ? 'Sede actualizada' : 'Sede creada', `${r.sede.nombre} · /${r.sede.slug}`)
      setForm(null); recargar(); refrescarSedes()
    } catch (err) { setErrorSrv(err.message) } finally { setEnviando(false) }
  }

  const alternar = async () => {
    const s = estado
    setEstado(null)
    try {
      await api.activarSede(s.sede.slug, !s.sede.activa)
      toast.exito(s.sede.activa ? 'Sede desactivada' : 'Sede activada', s.sede.nombre)
      recargar(); refrescarSedes()
    } catch (err) { toast.error('No se pudo cambiar el estado', err.message) }
  }

  return (
    <div className="space-y-6">
      <div className="flex flex-col gap-3 sm:flex-row sm:items-end sm:justify-between">
        <div>
          <p className="text-xs font-bold uppercase tracking-widest text-usco">Administración general</p>
          <h1 className="text-2xl font-extrabold sm:text-3xl">Sedes</h1>
          <p className="text-sm text-pizarra">Cada sede tiene su propia dirección web, su inventario, sus auxiliares, sus usuarios y su presupuesto.</p>
        </div>
        <div className="flex shrink-0 gap-2">
          <button onClick={recargar} disabled={cargando} className="btn-outline" aria-label="Actualizar sedes"><RefreshCw className={`h-4 w-4 ${cargando ? 'animate-spin' : ''}`} aria-hidden="true" /> Actualizar</button>
          <button onClick={() => { setForm({ nombre: '', ciudad: '', direccion: '' }); setErrores({}); setErrorSrv('') }} className="btn-primary"><Plus className="h-4 w-4" aria-hidden="true" /> Nueva sede</button>
        </div>
      </div>

      <ErrorCaja mensaje={error} />
      {!datos && !error && <Cargando texto="Cargando sedes…" />}

      {datos && (
        <ul className="grid gap-4 md:grid-cols-2 xl:grid-cols-3">
          {datos.map((s) => (
            <li key={s.sede.slug} className={`card flex flex-col p-5 ${s.sede.activa ? '' : 'opacity-75'} ${s.sede.slug === actual ? 'ring-2 ring-usco' : ''}`}>
              <div className="flex items-start justify-between gap-3">
                <span className="flex h-11 w-11 items-center justify-center rounded-xl bg-usco-soft text-usco"><Building2 className="h-5 w-5" aria-hidden="true" /></span>
                <span className={`rounded-full border px-2.5 py-0.5 text-xs font-bold ${s.sede.activa ? 'border-emerald-600 bg-emerald-50 text-emerald-900' : 'border-slate-400 bg-slate-100 text-slate-600'}`}>{s.sede.activa ? 'Activa' : 'Inactiva'}</span>
              </div>
              <h2 className="mt-3 font-titulo text-lg font-extrabold">{s.sede.nombre}{s.sede.slug === actual && <span className="ml-2 align-middle text-xs font-bold text-usco">(estás aquí)</span>}</h2>
              <p className="flex items-center gap-1.5 text-sm text-pizarra"><MapPin className="h-4 w-4 shrink-0" aria-hidden="true" /> {s.sede.ciudad}{s.sede.direccion ? ` · ${s.sede.direccion}` : ''}</p>
              <p className="mt-1 font-mono text-xs font-semibold text-usco">{window.location.host}/{s.sede.slug}</p>
              <dl className="mt-4 grid grid-cols-3 gap-2 rounded-lg bg-slate-50 p-3 text-center">
                <div><dt className="flex items-center justify-center gap-1 text-[11px] font-bold uppercase text-pizarra"><Package className="h-3.5 w-3.5" aria-hidden="true" /> Equipos</dt><dd className="font-extrabold">{s.equipos}</dd></div>
                <div><dt className="flex items-center justify-center gap-1 text-[11px] font-bold uppercase text-pizarra"><Users className="h-3.5 w-3.5" aria-hidden="true" /> Usuarios</dt><dd className="font-extrabold">{s.usuarios}</dd></div>
                <div><dt className="flex items-center justify-center gap-1 text-[11px] font-bold uppercase text-pizarra"><Wallet className="h-3.5 w-3.5" aria-hidden="true" /> Saldo</dt><dd className="truncate text-sm font-extrabold" title={pesos(s.saldo)}>{pesos(s.saldo)}</dd></div>
              </dl>
              <div className="mt-4 flex flex-wrap gap-2">
                {s.sede.slug !== actual && <Link to={`/${s.sede.slug}/coordinador`} className="btn-primary btn-sm">Entrar <ArrowRight className="h-4 w-4" aria-hidden="true" /></Link>}
                <button className="btn-outline btn-sm" onClick={() => { setForm({ slug: s.sede.slug, nombre: s.sede.nombre, ciudad: s.sede.ciudad, direccion: s.sede.direccion ?? '' }); setErrores({}); setErrorSrv('') }}><Pencil className="h-4 w-4" aria-hidden="true" /> Editar</button>
                <button className="btn-outline btn-sm" onClick={() => setEstado(s)}><Power className="h-4 w-4" aria-hidden="true" /> {s.sede.activa ? 'Desactivar' : 'Activar'}</button>
              </div>
            </li>
          ))}
        </ul>
      )}

      <Modal abierto={!!form} onCerrar={() => setForm(null)} titulo={editando ? 'Editar sede' : 'Nueva sede'} subtitulo={editando ? `/${form?.slug} · la dirección web no cambia` : 'Ej. Sede Garzón, Sede Pitalito, Sede La Plata'} ancho="max-w-lg"
        pie={<>
          <button type="button" className="btn-outline" onClick={() => setForm(null)}>Cancelar</button>
          <button type="submit" form="form-sede" className="btn-primary" disabled={enviando}>{editando ? <Pencil className="h-4 w-4" aria-hidden="true" /> : <Plus className="h-4 w-4" aria-hidden="true" />} {enviando ? 'Guardando…' : editando ? 'Guardar cambios' : 'Crear sede'}</button>
        </>}>
        {form && (
          <form id="form-sede" onSubmit={guardar} noValidate className="grid gap-4">
            <Campo etiqueta="Ciudad o municipio" htmlFor="s-ciudad" error={errores.ciudad} ayuda={editando ? 'No se puede cambiar para no romper los enlaces ya compartidos' : slugNuevo ? `Dirección web: ${window.location.host}/${slugNuevo}` : 'De aquí sale la dirección web de la sede'}>
              <input id="s-ciudad" className={`input ${errores.ciudad ? 'input-error' : ''}`} value={form.ciudad} onChange={set('ciudad')} disabled={editando} maxLength={60} placeholder="Pitalito" />
            </Campo>
            <Campo etiqueta="Nombre de la sede" htmlFor="s-nombre" error={errores.nombre} ayuda={!editando && !form.nombre.trim() && form.ciudad.trim() ? `Si lo dejas vacío: «Sede ${form.ciudad.trim()}»` : undefined}>
              <input id="s-nombre" className={`input ${errores.nombre ? 'input-error' : ''}`} value={form.nombre} onChange={set('nombre')} maxLength={80} placeholder="Sede Pitalito" />
            </Campo>
            <Campo etiqueta="Dirección física (opcional)" htmlFor="s-dir"><input id="s-dir" className="input" value={form.direccion} onChange={set('direccion')} maxLength={160} placeholder="Ej. Km 1 vía Vereda El Macal" /></Campo>
            <ErrorCaja mensaje={errorSrv} />
          </form>
        )}
      </Modal>

      <Confirmar abierto={!!estado} onCancelar={() => setEstado(null)} onOk={alternar} peligro={estado?.sede.activa}
        titulo={estado?.sede.activa ? 'Desactivar sede' : 'Activar sede'} textoOk={estado?.sede.activa ? 'Desactivar' : 'Activar'}
        mensaje={estado?.sede.activa ? `${estado?.sede.nombre} dejará de aparecer en «Elige tu sede» y sus usuarios no podrán ingresar.` : `${estado?.sede.nombre} volverá a estar disponible para sus usuarios.`}>
        <p className="mt-1 text-pizarra">No se borra ningún dato: inventario, usuarios, historial y presupuesto se conservan.</p>
      </Confirmar>
    </div>
  )
}
