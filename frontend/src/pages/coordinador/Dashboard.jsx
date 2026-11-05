import { useState } from 'react'
import { Link } from 'react-router-dom'
import { Bar, BarChart, CartesianGrid, Cell, Legend, Line, LineChart, Pie, PieChart, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts'
import { Boxes, CircleCheck, Clock, Download, FileBarChart2, ShieldCheck, TimerReset, TriangleAlert, Wrench, ArrowRightLeft, BadgeCheck } from 'lucide-react'
import { api } from '../../api/index.js'
import { useToast } from '../../context/ToastContext.jsx'
import { useTema } from '../../context/TemaContext.jsx'
import { useDatos } from '../../lib/hooks.js'
import { toDia, fmtDuracion } from '../../lib/fecha.js'
import { descargarTexto } from '../../lib/descarga.js'
import { Cargando, ErrorCaja, Kpi } from '../../components/ui.jsx'

const hoy = () => new Date()
const haceDias = (n) => { const d = new Date(); d.setDate(d.getDate() - n); return d }

export default function Dashboard() {
  const toast = useToast()
  const { oscuro } = useTema()
  const g = oscuro
    ? { grid: '#2D3A4A', eje: '#CBD5E1', eje2: '#A0B1BE', cursor: '#2A1A1D', barra: '#E0646C', punto: '#17202B', tip: { borderRadius: 8, border: '1px solid #2D3A4A', background: '#17202B', color: '#E5EAF1', fontSize: 13 } }
    : { grid: '#E2E8F0', eje: '#334155', eje2: '#4D626C', cursor: '#FBEFEF', barra: '#8F141B', punto: '#fff', tip: { borderRadius: 8, border: '1px solid #E2E8F0', fontSize: 13 } }
  const [desde, setDesde] = useState(toDia(haceDias(29)))
  const [hasta, setHasta] = useState(toDia(hoy()))
  const [rango, setRango] = useState({ desde: toDia(haceDias(29)), hasta: toDia(hoy()) })
  const [errFecha, setErrFecha] = useState('')
  const { datos: k, error } = useDatos(() => api.kpis(rango), [rango.desde, rango.hasta], { cada: 10000 })

  const generar = (e) => {
    e.preventDefault()
    if (!desde || !hasta) { setErrFecha('Selecciona el rango de fechas'); return }
    if (desde > hasta) { setErrFecha('La fecha inicial no puede ser posterior a la final'); return }
    setErrFecha(''); setRango({ desde, hasta })
  }

  const exportar = async () => {
    try {
      const csv = await api.reporteCsv(rango)
      descargarTexto(`reporte-uso-labcontrol_${rango.desde}_${rango.hasta}.csv`, csv)
      toast.exito('Reporte exportado', 'Campos no pertinentes enmascarados (••••••).')
    } catch (e) { toast.error('No se pudo exportar', e.message) }
  }

  const estados = k ? [
    { n: 'Disponible', v: k.disponibles, c: '#059669' },
    { n: 'Prestado', v: k.prestados, c: '#D97706' },
    { n: 'En mantenimiento', v: k.mantenimiento, c: '#64748B' },
  ] : []
  const serie = k?.serieDiaria.map((s) => ({ ...s, dia: s.fecha.slice(8) + '/' + s.fecha.slice(5, 7) })) ?? []
  const totalUso = k?.usoPorLaboratorio.reduce((s, x) => s + x.total, 0) ?? 0

  return (
    <div className="space-y-6">
      <div className="flex flex-col gap-4 lg:flex-row lg:items-end lg:justify-between">
        <div>
          <p className="text-xs font-bold uppercase tracking-widest text-usco">Dashboard ejecutivo</p>
          <h1 className="text-2xl font-extrabold sm:text-3xl">Control de inventario y reportes</h1>
        </div>
        <form onSubmit={generar} className="card flex flex-col gap-3 p-3 sm:flex-row sm:items-end" aria-label="Rango de fechas del reporte">
          <div><label htmlFor="d" className="label">Desde</label><input id="d" type="date" className="input" value={desde} max={hasta} onChange={(e) => setDesde(e.target.value)} /></div>
          <div><label htmlFor="h" className="label">Hasta</label><input id="h" type="date" className="input" value={hasta} min={desde} onChange={(e) => setHasta(e.target.value)} /></div>
          <button className="btn-primary" type="submit"><FileBarChart2 className="h-4 w-4" aria-hidden="true" /> Generar reporte</button>
        </form>
      </div>
      {errFecha && <p role="alert" className="flex items-center gap-1.5 text-sm font-semibold text-alerta-borde"><TriangleAlert className="h-4 w-4" aria-hidden="true" /> {errFecha}</p>}
      <ErrorCaja mensaje={error} />
      {!k && !error && <Cargando texto="Generando reporte…" />}

      {k && (
        <>
          {k.vencidos > 0 && (
            <div role="alert" className="flex items-start gap-3 rounded-xl border border-alerta-borde bg-alerta-fondo p-4">
              <TriangleAlert className="mt-0.5 h-6 w-6 shrink-0 text-alerta-borde" aria-hidden="true" />
              <p className="text-sm font-semibold text-alerta-texto"><strong className="font-extrabold">{k.vencidos} {k.vencidos === 1 ? 'préstamo vencido' : 'préstamos vencidos'}</strong> en este momento: la devolución superó el tiempo límite. Revisa el <Link to="/coordinador/historial" className="underline">historial</Link> para identificar al responsable.</p>
            </div>
          )}

          <div className="grid grid-cols-2 gap-3 lg:grid-cols-4">
            <Kpi etiqueta="Equipos en inventario" valor={k.totalEquipos} detalle={`${k.disponibles} disponibles ahora`} icono={Boxes} />
            <Kpi etiqueta="Préstamos del período" valor={k.prestamosPeriodo} detalle={`${k.devueltosPeriodo} devueltos`} icono={ArrowRightLeft} tono="text-salida" fondo="bg-blue-50" />
            <Kpi etiqueta="Devoluciones a tiempo" valor={`${k.devolucionesATiempoPct}%`} detalle={`Duración media ${fmtDuracion(k.tiempoPromedioMin)}`} icono={BadgeCheck} tono="text-entrada" fondo="bg-emerald-50" />
            <Kpi etiqueta="Incidencias del período" valor={k.incidenciasPeriodo} detalle={`${k.mantenimiento} equipos en mantenimiento`} icono={Wrench} tono="text-pizarra" fondo="bg-slate-100" />
          </div>
          <div className="grid grid-cols-2 gap-3 lg:grid-cols-4">
            <Kpi etiqueta="Préstamos en curso" valor={k.activos} icono={Clock} tono="text-amber-700" fondo="bg-amber-50" />
            <Kpi etiqueta="Vencidos ahora" valor={k.vencidos} icono={TimerReset} tono="text-alerta-borde" fondo="bg-alerta-fondo" />
            <Kpi etiqueta="Disponibles" valor={k.disponibles} icono={CircleCheck} tono="text-entrada" fondo="bg-emerald-50" />
            <Kpi etiqueta="Prestados" valor={k.prestados} icono={Clock} tono="text-amber-700" fondo="bg-amber-50" />
          </div>

          <div className="grid gap-4 xl:grid-cols-[1.35fr_1fr]">
            <section className="card p-4 sm:p-5" aria-labelledby="g-uso">
              <h2 id="g-uso" className="text-base font-extrabold">Uso por laboratorio</h2>
              <p className="mb-3 text-xs text-pizarra">Préstamos con salida registrada en el período seleccionado.</p>
              <div className="h-64" role="img" aria-label={`Gráfico de barras de préstamos por laboratorio: ${k.usoPorLaboratorio.map((u) => `${u.laboratorio} ${u.total}`).join(', ')}`}>
                <ResponsiveContainer width="100%" height="100%">
                  <BarChart data={k.usoPorLaboratorio} margin={{ top: 8, right: 8, left: -16, bottom: 0 }}>
                    <CartesianGrid strokeDasharray="3 3" stroke={g.grid} vertical={false} />
                    <XAxis dataKey="laboratorio" tick={{ fontSize: 12, fill: g.eje, fontWeight: 600 }} axisLine={false} tickLine={false} />
                    <YAxis allowDecimals={false} tick={{ fontSize: 12, fill: g.eje2 }} axisLine={false} tickLine={false} />
                    <Tooltip cursor={{ fill: g.cursor }} contentStyle={g.tip} formatter={(v) => [v, 'Préstamos']} />
                    <Bar dataKey="total" name="Préstamos" fill={g.barra} radius={[6, 6, 0, 0]} maxBarSize={64} />
                  </BarChart>
                </ResponsiveContainer>
              </div>
              <table className="mt-3 w-full text-sm">
                <caption className="sr-only">Detalle de préstamos por laboratorio</caption>
                <thead><tr><th className="th">Laboratorio</th><th className="th text-right">Préstamos</th><th className="th text-right">% del total</th></tr></thead>
                <tbody>{k.usoPorLaboratorio.map((u) => (
                  <tr key={u.laboratorio}><td className="td font-semibold">{u.laboratorio}</td><td className="td text-right">{u.total}</td><td className="td text-right">{totalUso ? Math.round((100 * u.total) / totalUso) : 0}%</td></tr>
                ))}</tbody>
              </table>
            </section>

            <section className="card p-4 sm:p-5" aria-labelledby="g-estado">
              <h2 id="g-estado" className="text-base font-extrabold">Estado actual del inventario</h2>
              <p className="mb-3 text-xs text-pizarra">Cada estado se identifica por color, ícono y texto.</p>
              <div className="h-52" role="img" aria-label={`Estado del inventario: ${estados.map((e) => `${e.n} ${e.v}`).join(', ')}`}>
                <ResponsiveContainer width="100%" height="100%">
                  <PieChart>
                    <Pie data={estados} dataKey="v" nameKey="n" innerRadius={52} outerRadius={82} paddingAngle={3} stroke="none">
                      {estados.map((e) => <Cell key={e.n} fill={e.c} />)}
                    </Pie>
                    <Tooltip contentStyle={g.tip} />
                    <Legend iconType="circle" wrapperStyle={{ fontSize: 12 }} />
                  </PieChart>
                </ResponsiveContainer>
              </div>
              <ul className="mt-2 space-y-1.5 text-sm">
                <li className="flex items-center justify-between"><span className="flex items-center gap-2 font-semibold text-emerald-800"><CircleCheck className="h-4 w-4" aria-hidden="true" /> Disponible</span><strong>{k.disponibles}</strong></li>
                <li className="flex items-center justify-between"><span className="flex items-center gap-2 font-semibold text-amber-900"><Clock className="h-4 w-4" aria-hidden="true" /> Prestado</span><strong>{k.prestados}</strong></li>
                <li className="flex items-center justify-between"><span className="flex items-center gap-2 font-semibold text-slate-700"><Wrench className="h-4 w-4" aria-hidden="true" /> En mantenimiento</span><strong>{k.mantenimiento}</strong></li>
              </ul>
            </section>
          </div>

          <section className="card p-4 sm:p-5" aria-labelledby="g-serie">
            <h2 id="g-serie" className="text-base font-extrabold">Préstamos por día</h2>
            <div className="mt-3 h-56" role="img" aria-label="Línea de préstamos por día en el período">
              <ResponsiveContainer width="100%" height="100%">
                <LineChart data={serie} margin={{ top: 8, right: 12, left: -16, bottom: 0 }}>
                  <CartesianGrid strokeDasharray="3 3" stroke={g.grid} vertical={false} />
                  <XAxis dataKey="dia" tick={{ fontSize: 11, fill: g.eje2 }} axisLine={false} tickLine={false} interval="preserveStartEnd" minTickGap={24} />
                  <YAxis allowDecimals={false} tick={{ fontSize: 12, fill: g.eje2 }} axisLine={false} tickLine={false} />
                  <Tooltip contentStyle={g.tip} formatter={(v) => [v, 'Préstamos']} labelFormatter={(l) => `Día ${l}`} />
                  <Line type="monotone" dataKey="total" stroke="#C5A059" strokeWidth={3} dot={{ r: 3, fill: g.barra, stroke: g.punto, strokeWidth: 1.5 }} activeDot={{ r: 5 }} />
                </LineChart>
              </ResponsiveContainer>
            </div>
          </section>

          <section className="card flex flex-col gap-3 p-4 sm:flex-row sm:items-center sm:justify-between sm:p-5">
            <div className="flex items-start gap-3">
              <ShieldCheck className="mt-0.5 h-6 w-6 shrink-0 text-entrada" aria-hidden="true" />
              <div><p className="font-extrabold">Reporte con datos protegidos</p><p className="text-sm text-pizarra">La exportación conserva lo necesario para identificar al responsable y enmascara dirección, teléfono y documento (Ley 1581 de 2012).</p></div>
            </div>
            <button className="btn-primary shrink-0" onClick={exportar}><Download className="h-4 w-4" aria-hidden="true" /> Exportar reporte (CSV)</button>
          </section>
        </>
      )}
    </div>
  )
}
