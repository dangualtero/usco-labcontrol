import { useMemo, useState } from 'react'
import { Link } from 'react-router-dom'
import { Archive, ArrowRight, Beaker, BellRing, CircleCheck, PackageX, Pencil, Plus, RefreshCw, Search, Trash2, TriangleAlert, Wrench } from 'lucide-react'
import { api } from '../../api/index.js'
import { useToast } from '../../context/ToastContext.jsx'
import { useSede } from '../../context/SedeContext.jsx'
import { useDatos } from '../../lib/hooks.js'
import { norm } from '../../lib/constantes.js'
import { fmtFechaHora } from '../../lib/fecha.js'
import { Campo, Cargando, ErrorCaja, EquipoFoto, EstadoBadge, Modal } from '../../components/ui.jsx'
import EstadoEquipoModal from '../../components/EstadoEquipoModal.jsx'
import EquipoFormModal from './EquipoFormModal.jsx'
import { Existencias, TipoBadge, enAlerta } from '../../components/Existencias.jsx'
import { FiltroTipo } from '../../components/FiltroTipo.jsx'

/**
 * Inventario del coordinador: equipos (se prestan) e insumos (se consumen por cantidad). Alta, edición,
 * mantenimiento y baja lógica (PV-08, PV-09, PS-08). Filtro por categoría: Todos · Equipos · Insumos.
 */
export default function Inventario() {
  const toast = useToast()
  const { sede, ruta } = useSede()
  const { datos, error, cargando, recargar } = useDatos(() => api.equiposAdmin(), [], { cada: 20000 })
  const [q, setQ] = useState('')
  const [lab, setLab] = useState('todos')
  const [tipo, setTipo] = useState('todos')
  const [soloAlerta, setSoloAlerta] = useState(false)
  const [baja, setBaja] = useState(null)
  const [motivo, setMotivo] = useState('')
  const [err, setErr] = useState('')
  const [enviando, setEnviando] = useState(false)
  const [form, setForm] = useState({ abierto: false, equipo: null, tipo: 'EQUIPO' })
  const [estadoModal, setEstadoModal] = useState(null)

  const nq = norm(q)
  const labs = useMemo(() => [...new Set((datos ?? []).map((x) => x.equipo.laboratorio))].sort(), [datos])
  const filtrar = (l) => l.filter((x) => (lab === 'todos' || x.equipo.laboratorio === lab) && (tipo === 'todos' || x.equipo.tipo === tipo)
    && (!soloAlerta || enAlerta(x.equipo))
    && (!nq || norm(`${x.equipo.placa} ${x.equipo.nombre} ${x.equipo.marca ?? ''} ${x.equipo.modelo ?? ''} ${x.equipo.numeroSerie ?? ''} ${x.equipo.categoria}`).includes(nq)))
  const todosActivos = (datos ?? []).filter((x) => x.activo)
  const conteo = { todos: todosActivos.length, EQUIPO: todosActivos.filter((x) => x.equipo.tipo !== 'INSUMO').length, INSUMO: todosActivos.filter((x) => x.equipo.tipo === 'INSUMO').length }
  const alertas = todosActivos.filter((x) => enAlerta(x.equipo)).length
  const activos = filtrar(todosActivos)
  const bajas = filtrar((datos ?? []).filter((x) => !x.activo))

  const abrirBaja = (x) => { setBaja(x); setMotivo(''); setErr('') }
  const confirmarBaja = async () => {
    if (motivo.trim().length < 5) { setErr('Indica el motivo de la baja'); return }
    setEnviando(true)
    try {
      await api.baja(baja.equipo.placa, motivo.trim())
      toast.exito(baja.equipo.tipo === 'INSUMO' ? 'Insumo dado de baja' : 'Equipo dado de baja', `${baja.equipo.placa} sale del inventario activo y se conserva para auditoría.`)
      setBaja(null); recargar()
    } catch (e) { setErr(e.message) } finally { setEnviando(false) }
  }

  return (
    <div className="space-y-6">
      <div className="flex flex-col gap-3 sm:flex-row sm:items-end sm:justify-between">
        <div>
          <p className="text-xs font-bold uppercase tracking-widest text-usco">Control de inventario · {sede?.nombre}</p>
          <h1 className="text-2xl font-extrabold sm:text-3xl">Inventario de equipos e insumos</h1>
          <p className="text-sm text-pizarra">Los equipos se prestan; los insumos se consumen por cantidad (unidades, mL/L, g/kg, cm/m). Las bajas son lógicas: el registro se conserva para auditoría.</p>
        </div>
        <div className="flex shrink-0 flex-wrap gap-2">
          <button onClick={recargar} disabled={cargando} className="btn-outline" aria-label="Actualizar inventario"><RefreshCw className={`h-4 w-4 ${cargando ? 'animate-spin' : ''}`} aria-hidden="true" /> Actualizar</button>
          <button onClick={() => setForm({ abierto: true, equipo: null, tipo: 'INSUMO' })} className="btn-outline"><Beaker className="h-4 w-4" aria-hidden="true" /> Nuevo insumo</button>
          <button onClick={() => setForm({ abierto: true, equipo: null, tipo: 'EQUIPO' })} className="btn-primary"><Plus className="h-4 w-4" aria-hidden="true" /> Nuevo equipo</button>
        </div>
      </div>

      <FiltroTipo valor={tipo} onCambio={setTipo} conteo={conteo} />

      <section className="card grid gap-3 p-4 sm:grid-cols-[1fr_220px_auto]" aria-label="Filtros">
        <div className="relative"><label htmlFor="i-q" className="sr-only">Buscar</label><Search className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-pizarra" aria-hidden="true" />
          <input id="i-q" className="input pl-9" placeholder="Buscar por placa, nombre, marca, serie o categoría" value={q} onChange={(e) => setQ(e.target.value)} /></div>
        <div><label htmlFor="i-l" className="sr-only">Laboratorio</label>
          <select id="i-l" className="input" value={lab} onChange={(e) => setLab(e.target.value)}><option value="todos">Todos los laboratorios</option>{labs.map((l) => <option key={l}>{l}</option>)}</select></div>
        <label className="flex min-h-[44px] cursor-pointer items-center gap-2 whitespace-nowrap text-sm font-semibold text-pizarra-oscuro">
          <input type="checkbox" checked={soloAlerta} onChange={(e) => setSoloAlerta(e.target.checked)} className="h-4 w-4 accent-[#8F141B]" /> Solo insumos en alerta ({alertas})
        </label>
      </section>

      <Link to={ruta('/coordinador/reportes')} className="card flex items-center justify-between gap-3 p-4 hover:shadow-lg">
        <span className="flex items-center gap-3"><BellRing className="h-5 w-5 shrink-0 text-usco" aria-hidden="true" />
          <span><span className="block font-extrabold">Reportes de novedades</span><span className="block text-sm text-pizarra">Los reportes de los auxiliares, en tiempo real y con seguimiento, están en «Reportes».</span></span></span>
        <ArrowRight className="h-5 w-5 shrink-0 text-usco" aria-hidden="true" />
      </Link>

      <ErrorCaja mensaje={error} />
      {!datos && !error && <Cargando />}

      {datos && (
        <>
          <section aria-labelledby="h-inv">
            <h2 id="h-inv" className="mb-3 text-lg font-extrabold">Inventario activo ({activos.length})</h2>
            {alertas > 0 && !soloAlerta && (
              <p className="mb-3 flex items-center gap-2 rounded-lg border border-alerta-borde bg-alerta-fondo px-3 py-2 text-sm font-semibold text-alerta-texto" role="status">
                <TriangleAlert className="h-4 w-4 shrink-0" aria-hidden="true" /> {alertas} {alertas === 1 ? 'insumo está' : 'insumos están'} agotados o en 25 % o menos.
                <Link to={ruta('/coordinador/alertas')} className="underline">Ver alertas</Link>
              </p>
            )}
            <div className="card overflow-hidden">
              <div className="relative overflow-x-auto">
                <table className="w-full min-w-[1000px]">
                  <thead><tr><th className="th">Elemento</th><th className="th">Laboratorio y ubicación</th><th className="th">Cantidad</th><th className="th">Estado</th><th className="th">Último responsable</th><th className="th"><span className="sr-only">Acciones</span></th></tr></thead>
                  <tbody>
                    {activos.map((x) => (
                      <tr key={x.equipo.placa}>
                        <td className="td">
                          <div className="flex items-center gap-3">
                            <div className="w-20 shrink-0 overflow-hidden rounded-lg ring-1 ring-slate-200"><EquipoFoto equipo={x.equipo} className="h-14" placa={false} zoom={false} /></div>
                            <div className="min-w-0"><p className="font-bold text-slate-900">{x.equipo.nombre}</p><p className="placa">{x.equipo.placa}{x.equipo.marca ? ` · ${x.equipo.marca}` : ''}{x.equipo.modelo ? ` ${x.equipo.modelo}` : ''}</p><TipoBadge tipo={x.equipo.tipo} className="mt-1" /></div>
                          </div>
                        </td>
                        <td className="td"><p className="font-semibold">{x.equipo.laboratorio}</p><p className="max-w-[230px] text-xs text-pizarra">{x.equipo.ubicacion ?? '—'}</p></td>
                        <td className="td"><Existencias equipo={x.equipo} compacto /></td>
                        <td className="td">{x.equipo.tipo === 'INSUMO' ? <span className="text-xs font-semibold text-pizarra">Se consume por cantidad</span> : <EstadoBadge estado={x.equipo.estado} />}</td>
                        <td className="td">{x.equipo.tipo === 'INSUMO' ? <span className="text-slate-400">—</span> : x.ultimoResponsable ?? <span className="text-slate-400">Sin préstamos</span>}</td>
                        <td className="td">
                          <div className="flex flex-wrap justify-end gap-1.5">
                            <button className="btn-outline btn-sm" onClick={() => setForm({ abierto: true, equipo: x.equipo, tipo: x.equipo.tipo })}><Pencil className="h-4 w-4" aria-hidden="true" /> Editar</button>
                            {x.equipo.tipo !== 'INSUMO' && x.equipo.estado !== 'PRESTADO' && (
                              <button className="btn-outline btn-sm" onClick={() => setEstadoModal(x.equipo)}>
                                {x.equipo.estado === 'MANTENIMIENTO' ? <><CircleCheck className="h-4 w-4" aria-hidden="true" /> Disponible</> : <><Wrench className="h-4 w-4" aria-hidden="true" /> Mantenimiento</>}
                              </button>
                            )}
                            <button className="btn-outline btn-sm !text-alerta-texto hover:!border-alerta-borde hover:!bg-alerta-fondo" onClick={() => abrirBaja(x)}><Trash2 className="h-4 w-4" aria-hidden="true" /> Dar de baja</button>
                          </div>
                        </td>
                      </tr>
                    ))}
                    {activos.length === 0 && <tr><td className="td py-8 text-center text-pizarra" colSpan={6}>No se encontraron elementos con esos filtros</td></tr>}
                  </tbody>
                </table>
              </div>
            </div>
          </section>

          <section aria-labelledby="h-baj">
            <h2 id="h-baj" className="mb-3 flex items-center gap-2 text-lg font-extrabold"><Archive className="h-5 w-5 text-pizarra" aria-hidden="true" /> Fuera del inventario activo · auditoría ({bajas.length})</h2>
            {bajas.length === 0 ? <p className="text-sm text-pizarra">No hay equipos dados de baja.</p> : (
              <ul className="grid gap-3 md:grid-cols-2">
                {bajas.map((x) => (
                  <li key={x.equipo.placa} className="card border-dashed bg-slate-50 p-4">
                    <p className="font-bold text-slate-900">{x.equipo.nombre}</p>
                    <p className="placa">{x.equipo.placa} · {x.equipo.laboratorio}</p>
                    <p className="mt-2 text-sm"><strong>Dado de baja</strong> · {fmtFechaHora(x.bajaFecha)}</p>
                    <p className="text-sm text-pizarra">Motivo: {x.bajaMotivo}</p>
                  </li>
                ))}
              </ul>
            )}
          </section>
        </>
      )}

      <EquipoFormModal abierto={form.abierto} equipo={form.equipo} tipoInicial={form.tipo} onCerrar={() => setForm((x) => ({ ...x, abierto: false, equipo: null }))} onHecho={recargar} />
      <EstadoEquipoModal equipo={estadoModal} accion={api.estadoEquipo} onCerrar={() => setEstadoModal(null)} onHecho={recargar} />

      {/* PV-08: confirmación advirtiendo acción irreversible · PV-09: Cancelar / X cierra y el equipo sigue activo */}
      <Modal abierto={!!baja} onCerrar={() => setBaja(null)} titulo={baja?.equipo.tipo === 'INSUMO' ? 'Dar de baja insumo' : 'Dar de baja equipo'} subtitulo={baja ? `${baja.equipo.nombre} · ${baja.equipo.placa}` : undefined} acento="bg-alerta-borde" ancho="max-w-md"
        pie={<>
          <button className="btn-outline" onClick={() => setBaja(null)}>Cancelar</button>
          <button className="btn-danger" onClick={confirmarBaja} disabled={enviando}><PackageX className="h-4 w-4" aria-hidden="true" /> {enviando ? 'Procesando…' : 'Confirmar baja'}</button>
        </>}>
        <div className="space-y-4">
          <p role="alert" className="rounded-lg border border-alerta-borde bg-alerta-fondo p-3 text-sm font-bold text-alerta-texto">Esta acción no se puede deshacer. El equipo saldrá del inventario activo y dejará de mostrarse en el catálogo.</p>
          <Campo etiqueta="Motivo de la baja" htmlFor="b-motivo" error={err}>
            <textarea id="b-motivo" rows={3} className={`input ${err ? 'input-error' : ''}`} value={motivo} onChange={(e) => setMotivo(e.target.value)} maxLength={250} placeholder="Ej. Daño irreparable, obsolescencia, pérdida…" />
          </Campo>
        </div>
      </Modal>
    </div>
  )
}
