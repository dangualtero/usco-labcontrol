import { useState } from 'react'
import { ChevronLeft, ChevronRight, Download, FileSearch, Lock, Search } from 'lucide-react'
import { api } from '../../api/index.js'
import { useToast } from '../../context/ToastContext.jsx'
import { useDatos, useLaboratorios } from '../../lib/hooks.js'
import { fmtFechaHora, toDia } from '../../lib/fecha.js'
import { descargarTexto } from '../../lib/descarga.js'
import { Cargando, ErrorCaja, Vacio } from '../../components/ui.jsx'

const GRIS = 'bg-slate-100 text-slate-800 border-slate-400'
const TIPOS = {
  ALTA: ['Alta', 'bg-emerald-50 text-emerald-800 border-emerald-600'],
  ESTADO: ['Cambio de estado', 'bg-blue-50 text-blue-900 border-blue-600'],
  OPERATIVO: ['Información operativa', 'bg-blue-50 text-blue-900 border-blue-600'],
  NOVEDAD: ['Novedad', 'bg-amber-50 text-amber-900 border-amber-600'],
  VALIDACION: ['Validación', 'bg-emerald-50 text-emerald-800 border-emerald-600'],
  SEGUIMIENTO: ['Seguimiento', GRIS],
  MANTENIMIENTO: ['Mantenimiento', 'bg-amber-50 text-amber-900 border-amber-600'],
  BAJA: ['Baja', 'bg-slate-200 text-slate-900 border-slate-600'],
  // Eventos del sistema anterior (préstamos), conservados como trazabilidad
  SALIDA: ['Salida (préstamo)', GRIS], ENTRADA: ['Entrada (préstamo)', GRIS], RESERVA: ['Reserva', GRIS], CANCELACION: ['Cancelación', GRIS],
  RECHAZO: ['Rechazo', GRIS], INCIDENCIA: ['Incidencia', 'bg-alerta-fondo text-alerta-texto border-alerta-borde'], CUSTODIA: ['Custodia', GRIS],
}
const POR_PAGINA = 12
const haceDias = (n) => { const d = new Date(); d.setDate(d.getDate() - n); return d }

/** Historial inmutable (RF-03, PF-14) del inventario: solo lectura, con filtros por fecha, laboratorio y búsqueda. */
export default function Historial() {
  const toast = useToast()
  const [f, setF] = useState({ desde: toDia(haceDias(29)), hasta: toDia(new Date()), laboratorio: 'todos', q: '' })
  const [pag, setPag] = useState(0)
  const labs = useLaboratorios()
  const { datos, error } = useDatos(() => api.historial(f), [f.desde, f.hasta, f.laboratorio, f.q], { cada: 10000 })

  const set = (k, v) => { setF((x) => ({ ...x, [k]: v })); setPag(0) }
  const total = datos?.length ?? 0
  const paginas = Math.max(1, Math.ceil(total / POR_PAGINA))
  const filas = (datos ?? []).slice(pag * POR_PAGINA, (pag + 1) * POR_PAGINA)

  const exportar = async () => {
    try {
      descargarTexto(`historial-labcontrol_${f.desde}_${f.hasta}.csv`, await api.reporteCsv('historial', { desde: f.desde, hasta: f.hasta, laboratorio: f.laboratorio }))
      toast.exito('Reporte exportado', 'Historial en CSV (se abre con Excel)')
    } catch (e) { toast.error('No se pudo exportar', e.message) }
  }

  return (
    <div className="space-y-5">
      <div className="flex flex-col gap-3 sm:flex-row sm:items-end sm:justify-between">
        <div>
          <p className="text-xs font-bold uppercase tracking-widest text-usco">Reportes y seguimiento · Trazabilidad</p>
          <h1 className="text-2xl font-extrabold sm:text-3xl">Historial del inventario</h1>
          <p className="mt-1 inline-flex items-center gap-1.5 rounded-full bg-slate-800 px-3 py-1 text-xs font-bold text-white"><Lock className="h-3.5 w-3.5" aria-hidden="true" /> Registro inmutable · solo lectura</p>
        </div>
        <button className="btn-primary" onClick={exportar}><Download className="h-4 w-4" aria-hidden="true" /> Exportar reporte</button>
      </div>

      <section className="card grid gap-3 p-4 sm:grid-cols-2 lg:grid-cols-4" aria-label="Filtros del historial">
        <div><label htmlFor="f-d" className="label">Desde</label><input id="f-d" type="date" className="input" value={f.desde} max={f.hasta} onChange={(e) => set('desde', e.target.value)} /></div>
        <div><label htmlFor="f-h" className="label">Hasta</label><input id="f-h" type="date" className="input" value={f.hasta} min={f.desde} onChange={(e) => set('hasta', e.target.value)} /></div>
        <div><label htmlFor="f-l" className="label">Laboratorio</label>
          <select id="f-l" className="input" value={f.laboratorio} onChange={(e) => set('laboratorio', e.target.value)}>
            <option value="todos">Todos</option>{labs.map((l) => <option key={l}>{l}</option>)}
          </select></div>
        <div><label htmlFor="f-q" className="label">Buscar</label>
          <div className="relative"><Search className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-pizarra" aria-hidden="true" />
            <input id="f-q" className="input pl-9" placeholder="Placa, elemento, persona o código" value={f.q} onChange={(e) => set('q', e.target.value)} /></div></div>
      </section>

      <ErrorCaja mensaje={error} />
      {!datos && !error && <Cargando />}
      {datos && total === 0 && <Vacio icono={FileSearch} titulo="Sin movimientos en este rango" detalle="Amplía el rango de fechas o cambia los filtros." />}

      {total > 0 && (
        <>
          <div className="card hidden overflow-hidden lg:block">
            <table className="w-full">
              <thead><tr><th className="th">Fecha y hora</th><th className="th">Evento</th><th className="th">Elemento</th><th className="th">Referencia</th><th className="th">Registró</th><th className="th">Detalle</th></tr></thead>
              <tbody>
                {filas.map((h) => {
                  const [t, c] = TIPOS[h.tipo] ?? [h.tipo, GRIS]
                  return (
                    <tr key={h.id}>
                      <td className="td whitespace-nowrap">{fmtFechaHora(h.fecha)}</td>
                      <td className="td"><span className={`inline-flex rounded-full border px-2.5 py-0.5 text-xs font-bold ${c}`}>{t}</span></td>
                      <td className="td"><p className="font-bold text-slate-900">{h.equipo}</p><p className="placa">{h.placa} · {h.laboratorio}</p></td>
                      <td className="td"><span className="font-mono text-xs">{h.referencia ?? '—'}</span>{h.involucrado && <p className="text-xs text-pizarra">{h.involucrado}</p>}</td>
                      <td className="td">{h.actor ?? '—'}</td>
                      <td className="td max-w-[320px] text-[13px]">{h.detalle}</td>
                    </tr>
                  )
                })}
              </tbody>
            </table>
          </div>
          <ul className="space-y-3 lg:hidden">
            {filas.map((h) => {
              const [t, c] = TIPOS[h.tipo] ?? [h.tipo, GRIS]
              return (
                <li key={h.id} className="card p-4">
                  <div className="flex items-center justify-between gap-2"><span className={`rounded-full border px-2.5 py-0.5 text-xs font-bold ${c}`}>{t}</span><span className="text-xs text-pizarra">{fmtFechaHora(h.fecha)}</span></div>
                  <p className="mt-2 font-extrabold">{h.equipo}</p><p className="placa">{h.placa}</p>
                  <p className="mt-1 text-sm">{h.actor ?? '—'}{h.referencia ? ` · ${h.referencia}` : ''}</p>
                  <p className="mt-1 text-[13px] text-pizarra">{h.detalle}</p>
                </li>
              )
            })}
          </ul>
          <nav className="flex items-center justify-between text-sm" aria-label="Paginación del historial">
            <span className="text-pizarra">{total} {total === 1 ? 'registro' : 'registros'} · página {pag + 1} de {paginas}</span>
            <div className="flex gap-2">
              <button className="btn-outline btn-sm" disabled={pag === 0} onClick={() => setPag(pag - 1)}><ChevronLeft className="h-4 w-4" aria-hidden="true" /> Anterior</button>
              <button className="btn-outline btn-sm" disabled={pag >= paginas - 1} onClick={() => setPag(pag + 1)}>Siguiente <ChevronRight className="h-4 w-4" aria-hidden="true" /></button>
            </div>
          </nav>
        </>
      )}
    </div>
  )
}
