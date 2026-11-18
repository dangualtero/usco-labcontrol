import { Fragment, useEffect, useMemo, useState } from 'react'
import { Lock, Mail, MailCheck, Plus, RotateCcw, Save, ShieldCheck, SlidersHorizontal, Tags } from 'lucide-react'
import { api } from '../../api/index.js'
import { useAuth } from '../../context/AuthContext.jsx'
import { useToast } from '../../context/ToastContext.jsx'
import { useDatos, olvidarCatalogos } from '../../lib/hooks.js'
import { Cargando, Confirmar, ErrorCaja } from '../../components/ui.jsx'
import { Encabezado } from '../../components/Estados.jsx'
import { FiltroTipo } from '../../components/FiltroTipo.jsx'

const PESTANAS = [
  { valor: 'permisos', texto: 'Permisos por rol', Icono: ShieldCheck },
  { valor: 'catalogos', texto: 'Catálogos', Icono: Tags },
  { valor: 'parametros', texto: 'Alertas y plazos', Icono: SlidersHorizontal },
  { valor: 'plantillas', texto: 'Plantillas de correo', Icono: Mail },
]

/** Parametrización (exclusiva del Administrador general): permisos de cada rol, catálogos, umbrales de alertas y plantillas. */
export default function Parametrizacion() {
  const [pestana, setPestana] = useState('permisos')
  return (
    <div className="space-y-5">
      <Encabezado modulo="Tablero de control y parametrización" titulo="Parametrización"
        detalle="Configura qué puede hacer cada rol, las listas desplegables, los umbrales de las alertas y los correos automáticos." />
      <FiltroTipo valor={pestana} onCambio={setPestana} opciones={PESTANAS} etiqueta="Sección de parametrización" />
      {pestana === 'permisos' && <Permisos />}
      {pestana === 'catalogos' && <Catalogos />}
      {pestana === 'parametros' && <Parametros />}
      {pestana === 'plantillas' && <Plantillas />}
    </div>
  )
}

function Permisos() {
  const toast = useToast()
  const { reintentar } = useAuth()
  const { datos, error, recargar } = useDatos(() => api.permisos(), [], { cada: 0 })
  const [lista, setLista] = useState(null)
  const [restablecer, setRestablecer] = useState(false)
  useEffect(() => { if (datos) setLista(datos) }, [datos])
  const grupos = useMemo(() => {
    const g = new Map()
    ;(lista ?? []).forEach((p) => { if (!g.has(p.moduloTexto)) g.set(p.moduloTexto, []); g.get(p.moduloTexto).push(p) })
    return [...g.entries()]
  }, [lista])
  const cambiar = async (p, rol, valor) => {
    try { setLista(await api.cambiarPermiso(rol, p.codigo, valor)); toast.exito('Permiso actualizado', `${p.descripcion} · ${rol === 'MONITOR' ? 'Monitor' : 'Coordinador'}: ${valor ? 'sí' : 'no'}`); reintentar() }
    catch (e) { toast.error('No se pudo cambiar', e.message) }
  }
  const volver = async () => {
    try { setLista(await api.restablecerPermisos()); toast.exito('Permisos restablecidos', 'Se aplican los de la hoja de roles'); setRestablecer(false); recargar() }
    catch (e) { toast.error('No se pudo restablecer', e.message) }
  }
  if (error) return <ErrorCaja mensaje={error} />
  if (!lista) return <Cargando />
  const Celda = ({ p, rol, campo, defecto }) => p.soloAdministrador
    ? <span className="inline-flex items-center gap-1 text-xs font-semibold text-pizarra"><Lock className="h-3.5 w-3.5" aria-hidden="true" /> Solo Administrador</span>
    : (
      <label className="inline-flex cursor-pointer items-center gap-2 text-sm">
        <input type="checkbox" checked={p[campo]} onChange={(e) => cambiar(p, rol, e.target.checked)} className="h-5 w-5 accent-[#8F141B]" aria-label={`${p.descripcion} para ${rol === 'MONITOR' ? 'Monitor' : 'Coordinador'}`} />
        {p[campo] !== p[defecto] && <span className="rounded bg-amber-100 px-1.5 py-0.5 text-[10px] font-bold text-amber-900">Modificado</span>}
      </label>
    )
  return (
    <section className="space-y-3">
      <div className="flex flex-wrap items-center justify-between gap-2">
        <p className="max-w-3xl text-sm text-pizarra">El Administrador general siempre tiene todos los permisos. Las funciones marcadas «Solo Administrador» no se pueden delegar. Los cambios aplican de inmediato (el usuario los ve al recargar).</p>
        <button className="btn-outline btn-sm" onClick={() => setRestablecer(true)}><RotateCcw className="h-4 w-4" aria-hidden="true" /> Restablecer hoja de roles</button>
      </div>
      <div className="card overflow-hidden">
        <div className="overflow-x-auto">
          <table className="w-full min-w-[720px]">
            <thead><tr><th className="th">Permiso</th><th className="th">Coordinador de Laboratorio</th><th className="th">Monitor de Laboratorio</th></tr></thead>
            <tbody>
              {grupos.map(([modulo, ps]) => (
                <Fragment key={modulo}>
                  <tr><td colSpan={3} className="bg-slate-50 px-4 py-2 text-xs font-extrabold uppercase tracking-wider text-usco">{modulo}</td></tr>
                  {ps.map((p) => (
                    <tr key={p.codigo}>
                      <td className="td"><p className="font-semibold">{p.descripcion}</p><p className="font-mono text-[11px] text-pizarra">{p.codigo}</p></td>
                      <td className="td"><Celda p={p} rol="COORDINADOR_LAB" campo="coordinador" defecto="coordinadorPorDefecto" /></td>
                      <td className="td"><Celda p={p} rol="MONITOR" campo="monitor" defecto="monitorPorDefecto" /></td>
                    </tr>
                  ))}
                </Fragment>
              ))}
            </tbody>
          </table>
        </div>
      </div>
      <Confirmar abierto={restablecer} titulo="Restablecer permisos" mensaje="Se descartan los cambios y se vuelve a la matriz de la hoja de roles." textoOk="Restablecer" onOk={volver} onCancelar={() => setRestablecer(false)} />
    </section>
  )
}

function Catalogos() {
  const toast = useToast()
  const { datos, error, recargar } = useDatos(() => api.catalogosAdmin(), [], { cada: 0 })
  const [nuevo, setNuevo] = useState({})
  const [editando, setEditando] = useState({})
  const grupos = useMemo(() => {
    const g = new Map()
    ;(datos ?? []).forEach((c) => { if (!g.has(c.tipo)) g.set(c.tipo, { texto: c.tipoTexto, items: [] }); g.get(c.tipo).items.push(c) })
    return [...g.entries()]
  }, [datos])
  const agregar = async (tipo) => {
    const valor = (nuevo[tipo] ?? '').trim()
    if (!valor) return
    try { await api.crearCatalogo({ tipo, valor }); olvidarCatalogos(); setNuevo((x) => ({ ...x, [tipo]: '' })); toast.exito('Valor agregado', valor); recargar() }
    catch (e) { toast.error('No se pudo agregar', e.message) }
  }
  const guardar = async (c, cambios) => {
    try { await api.actualizarCatalogo(c.id, { tipo: c.tipo, valor: c.valor, orden: c.orden, activo: c.activo, ...cambios }); olvidarCatalogos(); setEditando((x) => ({ ...x, [c.id]: undefined })); recargar() }
    catch (e) { toast.error('No se pudo guardar', e.message) }
  }
  if (error) return <ErrorCaja mensaje={error} />
  if (!datos) return <Cargando />
  return (
    <div className="grid gap-4 lg:grid-cols-2">
      {grupos.map(([tipo, g]) => (
        <section key={tipo} className="card p-4" aria-label={g.texto}>
          <h2 className="mb-3 font-extrabold">{g.texto}</h2>
          <ul className="space-y-1.5">
            {g.items.map((c) => (
              <li key={c.id} className={`flex items-center gap-2 rounded-lg border px-2 py-1.5 text-sm ${c.activo ? 'border-slate-200' : 'border-dashed border-slate-300 bg-slate-50 text-slate-500'}`}>
                {editando[c.id] !== undefined
                  ? <input className="input !min-h-[36px] flex-1 py-1" value={editando[c.id]} onChange={(e) => setEditando((x) => ({ ...x, [c.id]: e.target.value }))} maxLength={80} aria-label="Nuevo nombre" />
                  : <span className="flex-1 font-semibold">{c.valor}</span>}
                {editando[c.id] !== undefined
                  ? <button className="btn-primary btn-sm" onClick={() => guardar(c, { valor: editando[c.id] })}><Save className="h-4 w-4" aria-hidden="true" /></button>
                  : <button className="btn-outline btn-sm" onClick={() => setEditando((x) => ({ ...x, [c.id]: c.valor }))}>Renombrar</button>}
                <button className="btn-outline btn-sm" onClick={() => guardar(c, { activo: !c.activo })}>{c.activo ? 'Desactivar' : 'Activar'}</button>
              </li>
            ))}
          </ul>
          <form className="mt-3 flex gap-2" onSubmit={(e) => { e.preventDefault(); agregar(tipo) }}>
            <label htmlFor={`cat-${tipo}`} className="sr-only">Nuevo valor</label>
            <input id={`cat-${tipo}`} className="input" placeholder="Nuevo valor" value={nuevo[tipo] ?? ''} onChange={(e) => setNuevo((x) => ({ ...x, [tipo]: e.target.value }))} maxLength={80} />
            <button className="btn-primary" type="submit"><Plus className="h-4 w-4" aria-hidden="true" /> Agregar</button>
          </form>
        </section>
      ))}
    </div>
  )
}

function Parametros() {
  const toast = useToast()
  const { datos, error, recargar } = useDatos(() => api.parametros(), [], { cada: 0 })
  const [valores, setValores] = useState({})
  const [enviando, setEnviando] = useState(false)
  useEffect(() => { if (datos) setValores(Object.fromEntries(datos.map((p) => [p.clave, p.valor]))) }, [datos])
  const guardar = async (p) => {
    try { await api.cambiarParametro(p.clave, valores[p.clave]); toast.exito('Parámetro actualizado', p.descripcion); recargar() }
    catch (e) { toast.error('No se pudo guardar', e.message) }
  }
  const resumen = async () => {
    setEnviando(true)
    try {
      const r = await api.enviarResumen()
      if (r.enviado) toast.exito('Resumen diario enviado', `${r.reportes} novedad(es) y ${r.alertasInventario} alerta(s) → ${r.destinatario}`)
      else toast.info('Resumen no enviado', r.detalle)
    } catch (e) { toast.error('No se pudo enviar', e.message) } finally { setEnviando(false) }
  }
  if (error) return <ErrorCaja mensaje={error} />
  if (!datos) return <Cargando />
  return (
    <div className="space-y-4">
      <ul className="grid gap-3 md:grid-cols-2">
        {datos.map((p) => (
          <li key={p.clave} className="card space-y-2 p-4">
            <label htmlFor={`par-${p.clave}`} className="block text-sm font-bold">{p.descripcion}</label>
            <div className="flex gap-2">
              <input id={`par-${p.clave}`} type="number" min="0" className="input max-w-[140px]" value={valores[p.clave] ?? ''} onChange={(e) => setValores((x) => ({ ...x, [p.clave]: e.target.value }))} />
              <button className="btn-primary" onClick={() => guardar(p)}><Save className="h-4 w-4" aria-hidden="true" /> Guardar</button>
            </div>
            <p className="font-mono text-[11px] text-pizarra">{p.clave}</p>
          </li>
        ))}
      </ul>
      <section className="card flex flex-wrap items-center justify-between gap-3 p-4">
        <p className="text-sm text-pizarra">El resumen diario (novedades de prioridad media y baja, insumos en alerta y pendientes) llega una vez al día. Puedes enviarlo ahora para probarlo.</p>
        <button className="btn-outline" onClick={resumen} disabled={enviando}><MailCheck className="h-4 w-4" aria-hidden="true" /> {enviando ? 'Enviando…' : 'Enviar resumen ahora'}</button>
      </section>
    </div>
  )
}

function Plantillas() {
  const toast = useToast()
  const { datos, error, recargar } = useDatos(() => api.plantillas(), [], { cada: 0 })
  const [sel, setSel] = useState(null)
  const [f, setF] = useState({ asunto: '', cuerpo: '', activa: true })
  useEffect(() => { if (datos && !sel) setSel(datos[0]?.codigo) }, [datos, sel])
  const p = datos?.find((x) => x.codigo === sel)
  useEffect(() => { if (p) setF({ asunto: p.asunto, cuerpo: p.cuerpo, activa: p.activa }) }, [p])
  const guardar = async () => {
    try { await api.cambiarPlantilla(p.codigo, f); toast.exito('Plantilla guardada', p.nombre); recargar() }
    catch (e) { toast.error('No se pudo guardar', e.message) }
  }
  if (error) return <ErrorCaja mensaje={error} />
  if (!datos) return <Cargando />
  return (
    <div className="grid gap-4 lg:grid-cols-[300px_1fr]">
      <ul className="card divide-y divide-slate-100 overflow-hidden">
        {datos.map((x) => (
          <li key={x.codigo}><button type="button" onClick={() => setSel(x.codigo)} className={`w-full px-4 py-3 text-left text-sm ${sel === x.codigo ? 'bg-usco-soft font-bold text-usco-darker' : 'hover:bg-slate-50'}`}>
            {x.nombre}{!x.activa && <span className="ml-1 text-xs text-pizarra">(desactivada)</span>}
          </button></li>
        ))}
      </ul>
      {p && (
        <section className="card space-y-3 p-4" aria-label={p.nombre}>
          <h2 className="font-extrabold">{p.nombre}</h2>
          <div><label htmlFor="pl-a" className="label">Asunto</label><input id="pl-a" className="input" value={f.asunto} onChange={(e) => setF((x) => ({ ...x, asunto: e.target.value }))} maxLength={200} /></div>
          <div><label htmlFor="pl-c" className="label">Mensaje</label><textarea id="pl-c" rows={10} className="input font-mono text-[13px]" value={f.cuerpo} onChange={(e) => setF((x) => ({ ...x, cuerpo: e.target.value }))} maxLength={4000} /></div>
          <p className="text-xs text-pizarra">Variables disponibles: {p.variables.map((v) => <code key={v} className="mr-1 rounded bg-slate-100 px-1">{`{{${v}}}`}</code>)}</p>
          <label className="flex cursor-pointer items-center gap-2 text-sm font-semibold"><input type="checkbox" checked={f.activa} onChange={(e) => setF((x) => ({ ...x, activa: e.target.checked }))} className="h-4 w-4 accent-[#8F141B]" /> Enviar este correo automáticamente</label>
          <button className="btn-primary" onClick={guardar}><Save className="h-4 w-4" aria-hidden="true" /> Guardar plantilla</button>
        </section>
      )}
    </div>
  )
}
