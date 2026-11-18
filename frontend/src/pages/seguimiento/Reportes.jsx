import { useState } from 'react'
import { Download, FileSpreadsheet, Gauge, RefreshCw } from 'lucide-react'
import { api } from '../../api/index.js'
import { useAuth } from '../../context/AuthContext.jsx'
import { useSede } from '../../context/SedeContext.jsx'
import { useToast } from '../../context/ToastContext.jsx'
import { useDatos, useLaboratorios } from '../../lib/hooks.js'
import { pesos, puede } from '../../lib/constantes.js'
import { toDia } from '../../lib/fecha.js'
import { descargarTexto } from '../../lib/descarga.js'
import { Cargando, ErrorCaja } from '../../components/ui.jsx'
import { Encabezado } from '../../components/Estados.jsx'

const REPORTES = [
  ['indicadores', 'Indicadores por laboratorio', 'Inventario, alertas, mantenimientos, novedades, solicitudes y compromisos'],
  ['inventario', 'Inventario', 'Equipos e insumos con estado, ubicación y existencias', true],
  ['mantenimientos', 'Mantenimientos', 'Requeridos, programados y realizados'],
  ['novedades', 'Novedades', 'Registros preliminares, validación, prioridad y estado'],
  ['solicitudes', 'Solicitudes', 'Flujo, prioridad, decisión y (si tienes permiso) presupuesto y contratación'],
  ['compromisos', 'Compromisos', 'Responsable, fecha límite, estado y avance', true],
  ['avances', 'Avances', 'Avances con evidencias y verificación'],
  ['historial', 'Historial', 'Trazabilidad completa del inventario', false, 'HISTORIAL_VER'],
]
const COLUMNAS = [
  ['equipos', 'Equipos'], ['insumos', 'Insumos'], ['insumosEnAlerta', 'Insumos en alerta'], ['equiposFueraServicio', 'Fuera de servicio'],
  ['mantenimientosAbiertos', 'Mant. abiertos'], ['mantenimientosVencidos', 'Mant. vencidos'], ['mantenimientosRealizados', 'Mant. realizados'],
  ['novedadesPorValidar', 'Novedades por validar'], ['novedadesAbiertas', 'Novedades abiertas'], ['solicitudesEnTramite', 'Solicitudes en trámite'],
  ['solicitudesAprobadas', 'Aprobadas'], ['solicitudesRechazadas', 'Rechazadas'], ['compromisos', 'Compromisos'], ['compromisosCumplidos', 'Cumplidos'],
  ['compromisosVencidos', 'Vencidos'], ['avancesPendientes', 'Avances por verificar'], ['documentos', 'Documentos'],
]
const haceDias = (n) => { const d = new Date(); d.setDate(d.getDate() - n); return d }

/** Indicadores por laboratorio y reportes exportables (CSV que abre Excel) de cada módulo. */
export default function Reportes() {
  const { usuario } = useAuth()
  const { sede } = useSede()
  const toast = useToast()
  const labs = useLaboratorios()
  const { datos, error, cargando, recargar } = useDatos(() => api.indicadores(), [], { cada: 60000 })
  const [f, setF] = useState({ desde: toDia(haceDias(90)), hasta: toDia(new Date()), laboratorio: 'todos' })
  const [bajando, setBajando] = useState(null)
  const verValores = puede(usuario, 'PRESUPUESTO_CONSULTAR')

  const exportar = async (tipo, sinFechas) => {
    setBajando(tipo)
    try {
      const params = sinFechas ? { laboratorio: f.laboratorio } : f
      descargarTexto(`labcontrol-${tipo}-${sede?.slug ?? ''}-${toDia(new Date())}.csv`, await api.reporteCsv(tipo, params))
      toast.exito('Reporte exportado', 'Archivo CSV listo para abrir en Excel')
    } catch (e) { toast.error('No se pudo exportar', e.message) } finally { setBajando(null) }
  }

  return (
    <div className="space-y-6">
      <Encabezado modulo={`Reportes y seguimiento · ${sede?.nombre}`} titulo="Indicadores y reportes"
        detalle={usuario.rol === 'ADMINISTRADOR' ? 'Indicadores institucionales de todos los laboratorios de la sede y reportes exportables.' : 'Indicadores y reportes de tus laboratorios.'}>
        <button onClick={recargar} disabled={cargando} className="btn-outline"><RefreshCw className={`h-4 w-4 ${cargando ? 'animate-spin' : ''}`} aria-hidden="true" /> Actualizar</button>
      </Encabezado>

      <ErrorCaja mensaje={error} />
      {!datos && !error && <Cargando texto="Calculando indicadores…" />}
      {datos && (
        <section className="card overflow-hidden" aria-labelledby="h-ind">
          <h2 id="h-ind" className="flex items-center gap-2 border-b border-slate-200 px-4 py-3 font-extrabold"><Gauge className="h-4 w-4 text-usco" aria-hidden="true" /> Indicadores</h2>
          <div className="overflow-x-auto">
            <table className="w-full min-w-[1100px] text-sm">
              <thead><tr><th className="th sticky left-0 bg-superficie">Laboratorio</th>{COLUMNAS.map(([, t]) => <th key={t} className="th text-right">{t}</th>)}<th className="th text-right">Valor solicitado</th>{verValores && <th className="th text-right">Valor aprobado</th>}</tr></thead>
              <tbody>
                {[...datos.laboratorios, datos.total].map((l) => (
                  <tr key={l.laboratorioId ?? 'total'} className={l.laboratorioId ? '' : 'bg-slate-50 font-extrabold'}>
                    <td className="td sticky left-0 bg-inherit font-bold">{l.laboratorio}</td>
                    {COLUMNAS.map(([k]) => <td key={k} className={`td text-right ${['insumosEnAlerta', 'mantenimientosVencidos', 'compromisosVencidos'].includes(k) && l[k] ? 'font-bold text-alerta-texto' : ''}`}>{l[k]}</td>)}
                    <td className="td text-right">{pesos(l.valorSolicitado)}</td>
                    {verValores && <td className="td text-right">{pesos(l.valorAprobado)}</td>}
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </section>
      )}

      <section className="card space-y-4 p-4" aria-labelledby="h-exp">
        <h2 id="h-exp" className="flex items-center gap-2 font-extrabold"><FileSpreadsheet className="h-4 w-4 text-usco" aria-hidden="true" /> Exportar reportes</h2>
        <div className="grid gap-3 sm:grid-cols-3">
          <div><label htmlFor="r-d" className="label">Desde</label><input id="r-d" type="date" className="input" value={f.desde} max={f.hasta} onChange={(e) => setF((x) => ({ ...x, desde: e.target.value }))} /></div>
          <div><label htmlFor="r-h" className="label">Hasta</label><input id="r-h" type="date" className="input" value={f.hasta} min={f.desde} onChange={(e) => setF((x) => ({ ...x, hasta: e.target.value }))} /></div>
          <div><label htmlFor="r-l" className="label">Laboratorio</label>
            <select id="r-l" className="input" value={f.laboratorio} onChange={(e) => setF((x) => ({ ...x, laboratorio: e.target.value }))}><option value="todos">Todos</option>{labs.map((l) => <option key={l}>{l}</option>)}</select></div>
        </div>
        <ul className="grid gap-2 md:grid-cols-2">
          {REPORTES.filter(([, , , , permiso]) => !permiso || puede(usuario, permiso)).map(([tipo, titulo, detalle, sinFechas]) => (
            <li key={tipo} className="flex items-center justify-between gap-3 rounded-lg border border-slate-200 p-3">
              <span className="min-w-0"><span className="block font-bold">{titulo}</span><span className="block text-xs text-pizarra">{detalle}{sinFechas ? ' · estado actual' : ' · según el rango de fechas'}</span></span>
              <button className="btn-outline btn-sm shrink-0" onClick={() => exportar(tipo, sinFechas || tipo === 'indicadores')} disabled={bajando === tipo}><Download className="h-4 w-4" aria-hidden="true" /> {bajando === tipo ? 'Generando…' : 'CSV'}</button>
            </li>
          ))}
        </ul>
      </section>
    </div>
  )
}
