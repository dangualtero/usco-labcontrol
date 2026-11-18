import { useMemo, useState } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import { Archive, Beaker, BellPlus, ClipboardEdit, FileText, PackageX, Pencil, Plus, RefreshCw, Search, Settings2, TriangleAlert } from 'lucide-react'
import { api } from '../../api/index.js'
import { useAuth } from '../../context/AuthContext.jsx'
import { useToast } from '../../context/ToastContext.jsx'
import { useSede } from '../../context/SedeContext.jsx'
import { useDatos } from '../../lib/hooks.js'
import { norm, puede } from '../../lib/constantes.js'
import { fmtFechaHora } from '../../lib/fecha.js'
import { Campo, Cargando, ErrorCaja, EquipoFoto, Modal } from '../../components/ui.jsx'
import EstadoEquipoModal from '../../components/EstadoEquipoModal.jsx'
import { ReportarNovedadModal } from '../../components/Novedades.jsx'
import { Existencias, TipoBadge, enAlerta } from '../../components/Existencias.jsx'
import { FiltroTipo } from '../../components/FiltroTipo.jsx'
import { Encabezado, EstadoEquipo } from '../../components/Estados.jsx'
import EquipoFormModal from './EquipoFormModal.jsx'
import OperativoModal from './OperativoModal.jsx'

/**
 * Inventario de los laboratorios de la cuenta: equipos (con hoja de vida y mantenimientos) e insumos (por cantidad).
 *  - Administrador y coordinador: registrar, editar la ficha, cambiar el estado y dar de baja.
 *  - Coordinador y monitor: actualizar la información operativa (ubicación, estado físico, observaciones).
 *  - Todos: consultar la hoja de vida y registrar novedades.
 */
export default function Inventario() {
  const toast = useToast()
  const { usuario } = useAuth()
  const { sede, ruta } = useSede()
  const [params] = useSearchParams()
  const gestiona = puede(usuario, 'INVENTARIO_GESTIONAR')
  const operativo = puede(usuario, 'INVENTARIO_OPERATIVO')
  const reporta = puede(usuario, 'NOVEDAD_REPORTAR')
  const cambiaEstado = puede(usuario, 'INVENTARIO_GESTIONAR', 'MANTENIMIENTO_GESTIONAR')
  const { datos, error, cargando, recargar } = useDatos(() => api.inventario({ bajas: gestiona }), [gestiona], { cada: 20000 })
  const [q, setQ] = useState('')
  const [lab, setLab] = useState(params.get('laboratorio') ?? 'todos')
  const [tipo, setTipo] = useState('todos')
  const [soloAlerta, setSoloAlerta] = useState(false)
  const [baja, setBaja] = useState(null)
  const [motivo, setMotivo] = useState('')
  const [err, setErr] = useState('')
  const [enviando, setEnviando] = useState(false)
  const [form, setForm] = useState({ abierto: false, equipo: null, tipo: 'EQUIPO' })
  const [estadoModal, setEstadoModal] = useState(null)
  const [opModal, setOpModal] = useState(null)
  const [novedad, setNovedad] = useState(null)

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
      toast.exito('Elemento dado de baja', `${baja.equipo.placa} sale del inventario activo y se conserva para auditoría.`)
      setBaja(null); recargar()
    } catch (e) { setErr(e.message) } finally { setEnviando(false) }
  }

  return (
    <div className="space-y-6">
      <Encabezado modulo={`Inventarios y mantenimientos · ${sede?.nombre}`} titulo="Inventario de equipos e insumos"
        detalle="Los equipos llevan hoja de vida y mantenimientos; los insumos se controlan por cantidad. Las bajas son lógicas: el registro se conserva para auditoría.">
        <button onClick={recargar} disabled={cargando} className="btn-outline" aria-label="Actualizar inventario"><RefreshCw className={`h-4 w-4 ${cargando ? 'animate-spin' : ''}`} aria-hidden="true" /> Actualizar</button>
        {gestiona && <button onClick={() => setForm({ abierto: true, equipo: null, tipo: 'INSUMO' })} className="btn-outline"><Beaker className="h-4 w-4" aria-hidden="true" /> Nuevo insumo</button>}
        {gestiona && <button onClick={() => setForm({ abierto: true, equipo: null, tipo: 'EQUIPO' })} className="btn-primary"><Plus className="h-4 w-4" aria-hidden="true" /> Nuevo equipo</button>}
      </Encabezado>

      <FiltroTipo valor={tipo} onCambio={setTipo} conteo={conteo} />

      <section className="card grid gap-3 p-4 sm:grid-cols-[1fr_220px_auto]" aria-label="Filtros">
        <div className="relative"><label htmlFor="i-q" className="sr-only">Buscar</label><Search className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-pizarra" aria-hidden="true" />
          <input id="i-q" className="input pl-9" placeholder="Buscar por placa, nombre, marca, serie o categoría" value={q} onChange={(e) => setQ(e.target.value)} /></div>
        <div><label htmlFor="i-l" className="sr-only">Laboratorio</label>
          <select id="i-l" className="input" value={lab} onChange={(e) => setLab(e.target.value)}><option value="todos">Todos mis laboratorios</option>{labs.map((l) => <option key={l}>{l}</option>)}</select></div>
        <label className="flex min-h-[44px] cursor-pointer items-center gap-2 whitespace-nowrap text-sm font-semibold text-pizarra-oscuro">
          <input type="checkbox" checked={soloAlerta} onChange={(e) => setSoloAlerta(e.target.checked)} className="h-4 w-4 accent-[#8F141B]" /> Solo insumos en alerta ({alertas})
        </label>
      </section>

      <ErrorCaja mensaje={error} />
      {!datos && !error && <Cargando />}

      {datos && (
        <>
          <section aria-labelledby="h-inv">
            <h2 id="h-inv" className="mb-3 text-lg font-extrabold">Inventario activo ({activos.length})</h2>
            {alertas > 0 && !soloAlerta && (
              <p className="mb-3 flex items-center gap-2 rounded-lg border border-alerta-borde bg-alerta-fondo px-3 py-2 text-sm font-semibold text-alerta-texto" role="status">
                <TriangleAlert className="h-4 w-4 shrink-0" aria-hidden="true" /> {alertas} {alertas === 1 ? 'insumo está agotado o con existencias bajas' : 'insumos están agotados o con existencias bajas'}.
                <Link to={ruta('/alertas')} className="underline">Ver alertas</Link>
              </p>
            )}
            <div className="card overflow-hidden">
              <div className="relative overflow-x-auto">
                <table className="w-full min-w-[980px]">
                  <thead><tr><th className="th">Elemento</th><th className="th">Laboratorio y ubicación</th><th className="th">Cantidad</th><th className="th">Estado</th><th className="th"><span className="sr-only">Acciones</span></th></tr></thead>
                  <tbody>
                    {activos.map((x) => (
                      <tr key={x.equipo.placa}>
                        <td className="td">
                          <div className="flex items-center gap-3">
                            <div className="w-20 shrink-0 overflow-hidden rounded-lg ring-1 ring-slate-200"><EquipoFoto equipo={x.equipo} className="h-14" placa={false} zoom={false} /></div>
                            <div className="min-w-0">
                              <Link to={ruta(`/inventario/${x.equipo.placa}`)} className="font-bold text-slate-900 hover:text-usco hover:underline">{x.equipo.nombre}</Link>
                              <p className="placa">{x.equipo.placa}{x.equipo.marca ? ` · ${x.equipo.marca}` : ''}{x.equipo.modelo ? ` ${x.equipo.modelo}` : ''}</p>
                              <TipoBadge tipo={x.equipo.tipo} className="mt-1" />
                            </div>
                          </div>
                        </td>
                        <td className="td"><p className="font-semibold">{x.equipo.laboratorio}</p><p className="max-w-[230px] text-xs text-pizarra">{x.equipo.ubicacion ?? '—'}</p>
                          {x.equipo.observaciones && <p className="mt-1 max-w-[230px] truncate text-xs italic text-pizarra" title={x.equipo.observaciones}>«{x.equipo.observaciones}»</p>}</td>
                        <td className="td"><Existencias equipo={x.equipo} compacto /></td>
                        <td className="td">{x.equipo.tipo === 'INSUMO' ? <span className="text-xs font-semibold text-pizarra">Se controla por cantidad</span> : <EstadoEquipo estado={x.equipo.estado} />}
                          <p className="mt-1 text-xs text-pizarra">Estado físico: {x.equipo.estadoFisico ?? '—'}</p></td>
                        <td className="td">
                          <div className="flex flex-wrap justify-end gap-1.5">
                            <Link to={ruta(`/inventario/${x.equipo.placa}`)} className="btn-outline btn-sm"><FileText className="h-4 w-4" aria-hidden="true" /> Hoja de vida</Link>
                            {reporta && <button className="btn-outline btn-sm" onClick={() => setNovedad(x.equipo)}><BellPlus className="h-4 w-4" aria-hidden="true" /> Novedad</button>}
                            {operativo && !gestiona && <button className="btn-outline btn-sm" onClick={() => setOpModal(x.equipo)}><ClipboardEdit className="h-4 w-4" aria-hidden="true" /> Operativo</button>}
                            {gestiona && <button className="btn-outline btn-sm" onClick={() => setForm({ abierto: true, equipo: x.equipo, tipo: x.equipo.tipo })}><Pencil className="h-4 w-4" aria-hidden="true" /> Editar</button>}
                            {cambiaEstado && x.equipo.tipo !== 'INSUMO' && <button className="btn-outline btn-sm" onClick={() => setEstadoModal(x.equipo)}><Settings2 className="h-4 w-4" aria-hidden="true" /> Estado</button>}
                            {gestiona && <button className="btn-outline btn-sm !text-alerta-texto hover:!border-alerta-borde hover:!bg-alerta-fondo" onClick={() => abrirBaja(x)}><PackageX className="h-4 w-4" aria-hidden="true" /> Baja</button>}
                          </div>
                        </td>
                      </tr>
                    ))}
                    {activos.length === 0 && <tr><td className="td py-8 text-center text-pizarra" colSpan={5}>No se encontraron elementos con esos filtros</td></tr>}
                  </tbody>
                </table>
              </div>
            </div>
          </section>

          {gestiona && (
            <section aria-labelledby="h-baj">
              <h2 id="h-baj" className="mb-3 flex items-center gap-2 text-lg font-extrabold"><Archive className="h-5 w-5 text-pizarra" aria-hidden="true" /> Fuera del inventario activo · auditoría ({bajas.length})</h2>
              {bajas.length === 0 ? <p className="text-sm text-pizarra">No hay elementos dados de baja.</p> : (
                <ul className="grid gap-3 md:grid-cols-2">
                  {bajas.map((x) => (
                    <li key={x.equipo.placa} className="card border-dashed bg-slate-50 p-4">
                      <Link to={ruta(`/inventario/${x.equipo.placa}`)} className="font-bold text-slate-900 hover:underline">{x.equipo.nombre}</Link>
                      <p className="placa">{x.equipo.placa} · {x.equipo.laboratorio}</p>
                      <p className="mt-2 text-sm"><strong>Dado de baja</strong> · {fmtFechaHora(x.bajaFecha)}</p>
                      <p className="text-sm text-pizarra">Motivo: {x.bajaMotivo}</p>
                    </li>
                  ))}
                </ul>
              )}
            </section>
          )}
        </>
      )}

      <EquipoFormModal abierto={form.abierto} equipo={form.equipo} tipoInicial={form.tipo} onCerrar={() => setForm((x) => ({ ...x, abierto: false, equipo: null }))} onHecho={recargar} />
      <EstadoEquipoModal equipo={estadoModal} onCerrar={() => setEstadoModal(null)} onHecho={recargar} />
      <OperativoModal equipo={opModal} onCerrar={() => setOpModal(null)} onHecho={recargar} />
      <ReportarNovedadModal equipo={novedad} onCerrar={() => setNovedad(null)} onHecho={recargar} />

      <Modal abierto={!!baja} onCerrar={() => setBaja(null)} titulo="Dar de baja" subtitulo={baja ? `${baja.equipo.nombre} · ${baja.equipo.placa}` : undefined} acento="bg-alerta-borde" ancho="max-w-md"
        pie={<>
          <button className="btn-outline" onClick={() => setBaja(null)}>Cancelar</button>
          <button className="btn-danger" onClick={confirmarBaja} disabled={enviando}><PackageX className="h-4 w-4" aria-hidden="true" /> {enviando ? 'Procesando…' : 'Confirmar baja'}</button>
        </>}>
        <div className="space-y-4">
          <p role="alert" className="rounded-lg border border-alerta-borde bg-alerta-fondo p-3 text-sm font-bold text-alerta-texto">Esta acción no se puede deshacer. El elemento saldrá del inventario activo; su hoja de vida se conserva para auditoría.</p>
          <Campo etiqueta="Motivo de la baja" htmlFor="b-motivo" error={err}>
            <textarea id="b-motivo" rows={3} className={`input ${err ? 'input-error' : ''}`} value={motivo} onChange={(e) => setMotivo(e.target.value)} maxLength={250} placeholder="Ej. Daño irreparable, obsolescencia, pérdida…" />
          </Campo>
        </div>
      </Modal>
    </div>
  )
}
