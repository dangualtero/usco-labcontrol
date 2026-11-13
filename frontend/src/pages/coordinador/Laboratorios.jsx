import { useEffect, useState } from 'react'
import { Beaker, FlaskConical, MapPin, Microscope, Pencil, Plus, Power, RefreshCw, Save } from 'lucide-react'
import { api } from '../../api/index.js'
import { useToast } from '../../context/ToastContext.jsx'
import { useSede } from '../../context/SedeContext.jsx'
import { useDatos } from '../../lib/hooks.js'
import { Campo, Cargando, ErrorCaja, Modal, Vacio } from '../../components/ui.jsx'

/** Laboratorios de la sede abierta: cada sede tiene los suyos. El Coordinador los crea, edita y desactiva. */
export default function Laboratorios() {
  const toast = useToast()
  const { sede } = useSede()
  const { datos, error, cargando, recargar } = useDatos(() => api.laboratorios(), [], { cada: 30000 })
  const [form, setForm] = useState(null) // null cerrado · {} nuevo · lab editar

  const activar = async (l) => {
    try {
      await api.activarLaboratorio(l.id, !l.activo)
      toast.exito(l.activo ? 'Laboratorio desactivado' : 'Laboratorio activado', l.nombre)
      recargar()
    } catch (e) { toast.error('No se pudo cambiar', e.message) }
  }

  const activos = (datos ?? []).filter((l) => l.activo)
  const inactivos = (datos ?? []).filter((l) => !l.activo)

  return (
    <div className="space-y-6">
      <div className="flex flex-col gap-3 sm:flex-row sm:items-end sm:justify-between">
        <div>
          <p className="text-xs font-bold uppercase tracking-widest text-usco">Administración · {sede?.nombre}</p>
          <h1 className="text-2xl font-extrabold sm:text-3xl">Laboratorios</h1>
          <p className="text-sm text-pizarra">Cada sede tiene sus propios laboratorios. Los equipos e insumos se asignan a uno de ellos.</p>
        </div>
        <div className="flex gap-2">
          <button onClick={recargar} disabled={cargando} className="btn-outline"><RefreshCw className={`h-4 w-4 ${cargando ? 'animate-spin' : ''}`} aria-hidden="true" /> Actualizar</button>
          <button onClick={() => setForm({})} className="btn-primary"><Plus className="h-4 w-4" aria-hidden="true" /> Nuevo laboratorio</button>
        </div>
      </div>

      <ErrorCaja mensaje={error} />
      {!datos && !error && <Cargando />}
      {datos && datos.length === 0 && (
        <Vacio icono={FlaskConical} titulo={`La ${sede?.nombre ?? 'sede'} aún no tiene laboratorios`} detalle="Crea el primero para poder registrar equipos e insumos.">
          <button className="btn-primary" onClick={() => setForm({})}><Plus className="h-4 w-4" aria-hidden="true" /> Crear laboratorio</button>
        </Vacio>
      )}

      {activos.length > 0 && (
        <ul className="grid gap-4 sm:grid-cols-2 xl:grid-cols-3">
          {activos.map((l) => <TarjetaLab key={l.id} l={l} onEditar={() => setForm(l)} onActivar={() => activar(l)} />)}
        </ul>
      )}
      {inactivos.length > 0 && (
        <section aria-labelledby="h-inact">
          <h2 id="h-inact" className="mb-3 text-lg font-extrabold">Inactivos ({inactivos.length})</h2>
          <ul className="grid gap-4 sm:grid-cols-2 xl:grid-cols-3">
            {inactivos.map((l) => <TarjetaLab key={l.id} l={l} onEditar={() => setForm(l)} onActivar={() => activar(l)} />)}
          </ul>
        </section>
      )}

      <LabFormModal lab={form} onCerrar={() => setForm(null)} onHecho={recargar} />
    </div>
  )
}

function TarjetaLab({ l, onEditar, onActivar }) {
  return (
    <li className={`card flex flex-col gap-3 p-4 ${l.activo ? '' : 'border-dashed bg-slate-50 opacity-90'}`}>
      <div className="flex items-start gap-3">
        <span className="flex h-11 w-11 shrink-0 items-center justify-center rounded-xl bg-usco-soft"><FlaskConical className="h-5 w-5 text-usco" aria-hidden="true" /></span>
        <div className="min-w-0">
          <p className="text-lg font-extrabold text-slate-900">{l.nombre}</p>
          {!l.activo && <span className="text-xs font-bold text-pizarra">Inactivo · no aparece al registrar elementos</span>}
          {l.ubicacion && <p className="flex items-center gap-1 text-sm text-pizarra"><MapPin className="h-3.5 w-3.5 shrink-0" aria-hidden="true" /> {l.ubicacion}</p>}
        </div>
      </div>
      {l.descripcion && <p className="text-sm text-slate-700">{l.descripcion}</p>}
      <div className="flex gap-4 text-sm">
        <span className="flex items-center gap-1.5 font-semibold"><Microscope className="h-4 w-4 text-pizarra" aria-hidden="true" /> {l.equipos} equipos</span>
        <span className="flex items-center gap-1.5 font-semibold"><Beaker className="h-4 w-4 text-pizarra" aria-hidden="true" /> {l.insumos} insumos</span>
      </div>
      <div className="mt-auto flex gap-2 pt-1">
        <button className="btn-outline btn-sm flex-1" onClick={onEditar}><Pencil className="h-4 w-4" aria-hidden="true" /> Editar</button>
        <button className="btn-outline btn-sm flex-1" onClick={onActivar}><Power className="h-4 w-4" aria-hidden="true" /> {l.activo ? 'Desactivar' : 'Activar'}</button>
      </div>
    </li>
  )
}

function LabFormModal({ lab, onCerrar, onHecho }) {
  const toast = useToast()
  const { sede } = useSede()
  const nuevo = lab && !lab.id
  const [f, setF] = useState({ nombre: '', ubicacion: '', descripcion: '' })
  const [err, setErr] = useState({})
  const [errSrv, setErrSrv] = useState('')
  const [enviando, setEnviando] = useState(false)

  useEffect(() => {
    if (!lab) return
    setF({ nombre: lab.nombre ?? '', ubicacion: lab.ubicacion ?? '', descripcion: lab.descripcion ?? '' }); setErr({}); setErrSrv('')
  }, [lab])

  const set = (k) => (e) => setF((x) => ({ ...x, [k]: e.target.value }))
  const guardar = async (e) => {
    e.preventDefault()
    const er = {}
    if (f.nombre.trim().length < 2) er.nombre = 'Escribe el nombre del laboratorio'
    setErr(er)
    if (Object.keys(er).length) return
    setEnviando(true); setErrSrv('')
    try {
      const cuerpo = { nombre: f.nombre.trim(), ubicacion: f.ubicacion.trim() || null, descripcion: f.descripcion.trim() || null }
      if (nuevo) await api.crearLaboratorio(cuerpo); else await api.actualizarLaboratorio(lab.id, cuerpo)
      toast.exito(nuevo ? 'Laboratorio creado' : 'Laboratorio actualizado', `${cuerpo.nombre} · ${sede?.nombre ?? ''}`)
      onHecho?.(); onCerrar()
    } catch (er2) { setErrSrv(er2.message) } finally { setEnviando(false) }
  }

  return (
    <Modal abierto={!!lab} onCerrar={onCerrar} ancho="max-w-lg" titulo={nuevo ? 'Nuevo laboratorio' : 'Editar laboratorio'} subtitulo={sede?.nombre}
      pie={<>
        <button type="button" className="btn-outline" onClick={onCerrar}>Cancelar</button>
        <button type="submit" form="form-lab" className="btn-primary" disabled={enviando}><Save className="h-4 w-4" aria-hidden="true" /> {enviando ? 'Guardando…' : nuevo ? 'Crear laboratorio' : 'Guardar cambios'}</button>
      </>}>
      <form id="form-lab" onSubmit={guardar} noValidate className="space-y-4">
        <Campo etiqueta="Nombre" htmlFor="lab-nombre" error={err.nombre} ayuda={!nuevo ? 'Si cambias el nombre, los equipos e insumos de este laboratorio se actualizan solos' : 'Ej. Química, Física, Hidráulica'}>
          <input id="lab-nombre" className={`input ${err.nombre ? 'input-error' : ''}`} value={f.nombre} onChange={set('nombre')} maxLength={40} />
        </Campo>
        <Campo etiqueta="Ubicación (opcional)" htmlFor="lab-ubi"><input id="lab-ubi" className="input" value={f.ubicacion} onChange={set('ubicacion')} maxLength={160} placeholder="Ej. Bloque de Ingeniería, piso 2" /></Campo>
        <Campo etiqueta="Descripción (opcional)" htmlFor="lab-desc"><textarea id="lab-desc" rows={3} className="input" value={f.descripcion} onChange={set('descripcion')} maxLength={300} /></Campo>
        <ErrorCaja mensaje={errSrv} />
      </form>
    </Modal>
  )
}
