import { useMemo, useState } from 'react'
import { Link } from 'react-router-dom'
import { ArrowRight, BellRing, Beaker, CalendarClock, CircleAlert, ClipboardCheck, ClipboardList, Hourglass, Info, ListChecks, PackageX, PowerOff, RefreshCw, Search, TriangleAlert, Undo2, Wrench } from 'lucide-react'
import { useSede } from '../../context/SedeContext.jsx'
import { useDatos } from '../../lib/hooks.js'
import { api } from '../../api/index.js'
import { norm } from '../../lib/constantes.js'
import { fmtFechaHora } from '../../lib/fecha.js'
import { Cargando, ErrorCaja, Kpi, Vacio } from '../../components/ui.jsx'
import { TipoBadge } from '../../components/Existencias.jsx'

export const TIPOS_ALERTA = {
  AGOTADO: { texto: 'Insumo agotado', Icono: PackageX },
  STOCK_BAJO: { texto: 'Existencias bajas', Icono: Beaker },
  FUERA_SERVICIO: { texto: 'Equipo fuera de servicio', Icono: PowerOff },
  NOVEDAD_POR_VALIDAR: { texto: 'Novedad por validar', Icono: ClipboardCheck },
  NOVEDAD: { texto: 'Novedad sin resolver', Icono: BellRing },
  MANTENIMIENTO_VENCIDO: { texto: 'Mantenimiento vencido', Icono: Wrench },
  MANTENIMIENTO_PROXIMO: { texto: 'Mantenimiento próximo', Icono: CalendarClock },
  SOLICITUD_POR_VALIDAR: { texto: 'Solicitud por validar', Icono: ClipboardList },
  SOLICITUD_DEVUELTA: { texto: 'Solicitud devuelta', Icono: Undo2 },
  SOLICITUD_SIN_DECISION: { texto: 'Solicitud sin decisión', Icono: Hourglass },
  COMPROMISO_VENCIDO: { texto: 'Compromiso vencido', Icono: ListChecks },
}
const NIVEL = {
  CRITICA: { texto: 'Crítica', Icono: CircleAlert, caja: 'border-l-alerta-borde', badge: 'border-alerta-borde bg-alerta-fondo text-alerta-texto' },
  ADVERTENCIA: { texto: 'Advertencia', Icono: TriangleAlert, caja: 'border-l-amber-500', badge: 'border-amber-600 bg-amber-50 text-amber-900' },
  INFO: { texto: 'Informativa', Icono: Info, caja: 'border-l-slate-400', badge: 'border-slate-400 bg-slate-100 text-slate-700' },
}

/**
 * Alertas de los laboratorios de la cuenta: existencias, equipos fuera de servicio, novedades por validar, mantenimientos,
 * solicitudes que esperan a alguien y compromisos vencidos. Cada una trae el enlace a la pantalla donde se atiende.
 * Los umbrales se configuran en Parametrización → Parámetros.
 */
export default function Alertas() {
  const { sede, ruta } = useSede()
  const { datos, error, cargando, recargar, actualizado } = useDatos(() => api.alertas(), [], { cada: 15000 })
  const [tipoAlerta, setTipoAlerta] = useState('todas')
    const [nivel, setNivel] = useState('todos')
  const [lab, setLab] = useState('todos')
  const [q, setQ] = useState('')

  const labs = useMemo(() => [...new Set((datos ?? []).map((a) => a.laboratorio).filter(Boolean))].sort(), [datos])
  const porElemento = datos ?? []
  const lista = useMemo(() => {
    const nq = norm(q)
    return porElemento.filter((a) => (tipoAlerta === 'todas' || a.tipo === tipoAlerta) && (nivel === 'todos' || a.nivel === nivel)
      && (lab === 'todos' || a.laboratorio === lab)
      && (!nq || norm(`${a.placa} ${a.elemento} ${a.titulo} ${a.detalle}`).includes(nq)))
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [datos, tipoAlerta, nivel, lab, q])
  const cuenta = (f) => (datos ?? []).filter(f).length

  return (
    <div className="space-y-5">
      <div className="flex flex-col gap-3 sm:flex-row sm:items-end sm:justify-between">
        <div>
          <p className="text-xs font-bold uppercase tracking-widest text-usco">Alertas · {sede?.nombre}</p>
          <h1 className="text-2xl font-extrabold sm:text-3xl">Alertas</h1>
          <p className="text-sm text-pizarra">Lo que requiere atención en tus laboratorios: existencias bajas, novedades, mantenimientos, solicitudes y compromisos.</p>
        </div>
        <span className="flex items-center gap-2">
          {actualizado && <span className="text-xs font-semibold text-pizarra">Actualizado {actualizado.toLocaleTimeString('es-CO', { hour: '2-digit', minute: '2-digit', second: '2-digit' })}</span>}
          <button onClick={recargar} disabled={cargando} className="btn-outline"><RefreshCw className={`h-4 w-4 ${cargando ? 'animate-spin' : ''}`} aria-hidden="true" /> Actualizar</button>
        </span>
      </div>

      {datos && (
        <div className="grid grid-cols-1 gap-3 sm:grid-cols-3">
          {Object.entries(NIVEL).map(([k, n]) => (
            <button key={k} type="button" onClick={() => setNivel(nivel === k ? 'todos' : k)} aria-pressed={nivel === k} className={`rounded-xl text-left ring-offset-2 ${nivel === k ? 'ring-2 ring-usco' : ''}`}>
              <Kpi etiqueta={`Alertas ${n.texto.toLowerCase()}s`} valor={cuenta((a) => a.nivel === k)} icono={n.Icono}
                tono={k === 'CRITICA' ? 'text-alerta-borde' : k === 'ADVERTENCIA' ? 'text-amber-700' : 'text-pizarra'}
                fondo={k === 'CRITICA' ? 'bg-alerta-fondo' : k === 'ADVERTENCIA' ? 'bg-amber-50' : 'bg-slate-100'} />
            </button>
          ))}
        </div>
      )}

      <section className="card space-y-3 p-4" aria-label="Filtros de alertas">
        <div className="flex flex-wrap gap-2" role="group" aria-label="Filtrar por tipo de alerta">
          {[['todas', 'Todas'], ...Object.entries(TIPOS_ALERTA).filter(([k]) => cuenta((a) => a.tipo === k) > 0).map(([k, v]) => [k, v.texto])].map(([k, t]) => {
            const n = k === 'todas' ? (datos ?? []).length : cuenta((a) => a.tipo === k)
            return (
              <button key={k} type="button" onClick={() => setTipoAlerta(k)} aria-pressed={tipoAlerta === k}
                className={`min-h-[40px] rounded-full border px-3.5 text-sm font-bold transition-colors ${tipoAlerta === k ? 'border-usco bg-usco text-white' : 'border-slate-300 bg-white text-pizarra-oscuro hover:border-usco hover:text-usco'}`}>
                {t} <span className="opacity-80">({n})</span>
              </button>
            )
          })}
        </div>
        <div className="grid gap-3 lg:grid-cols-[1fr_220px]">
          <div className="relative"><label htmlFor="al-q" className="sr-only">Buscar elemento</label><Search className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-pizarra" aria-hidden="true" />
            <input id="al-q" className="input pl-9" placeholder="Buscar por placa, elemento, código o detalle" value={q} onChange={(e) => setQ(e.target.value)} /></div>
          <div><label htmlFor="al-l" className="sr-only">Laboratorio</label>
            <select id="al-l" className="input" value={lab} onChange={(e) => setLab(e.target.value)}><option value="todos">Todos los laboratorios</option>{labs.map((l) => <option key={l}>{l}</option>)}</select></div>
        </div>
      </section>

      <ErrorCaja mensaje={error} />
      {!datos && !error && <Cargando texto="Revisando el inventario…" />}
      {datos && datos.length === 0 && <Vacio icono={BellRing} titulo="Todo en orden" detalle="No hay nada pendiente en tus laboratorios." />}
      {datos && datos.length > 0 && lista.length === 0 && <Vacio titulo="Ninguna alerta con esos filtros" detalle="Cambia el tipo de alerta, el nivel o el laboratorio." />}

      {lista.length > 0 && (
        <ul className="space-y-2.5">
          {lista.map((a) => {
            const t = TIPOS_ALERTA[a.tipo] ?? { texto: a.tipo, Icono: BellRing }
            const n = NIVEL[a.nivel] ?? NIVEL.INFO
            const destino = a.enlace
            return (
              <li key={a.id} className={`card flex flex-col gap-3 border-l-4 p-4 sm:flex-row sm:items-center ${n.caja}`}>
                <span className="flex h-11 w-11 shrink-0 items-center justify-center rounded-xl bg-slate-100"><t.Icono className="h-5 w-5 text-usco" aria-hidden="true" /></span>
                <div className="min-w-0 flex-1">
                  <div className="flex flex-wrap items-center gap-2">
                    <span className={`inline-flex items-center gap-1 rounded-full border px-2 py-0.5 text-[11px] font-extrabold ${n.badge}`}><n.Icono className="h-3 w-3" aria-hidden="true" /> {n.texto}</span>
                    <span className="text-sm font-extrabold text-slate-900">{a.titulo}</span>
                  </div>
                  <p className="mt-1 text-sm text-slate-700">{a.placa && <><span className="placa">{a.placa}</span> · <strong>{a.elemento}</strong> · </>}<span className="text-pizarra">{a.laboratorio}</span></p>
                  <p className="mt-0.5 line-clamp-2 text-sm text-pizarra">{a.detalle}</p>
                  {a.tipo === 'STOCK_BAJO' && a.porcentaje != null && (
                    <div className="mt-2 h-2 max-w-xs overflow-hidden rounded-full bg-slate-200" role="meter" aria-valuemin={0} aria-valuemax={100} aria-valuenow={a.porcentaje} aria-label={`Existencias al ${a.porcentaje} %`}>
                      <div className="h-full rounded-full bg-alerta-borde" style={{ width: `${Math.max(a.porcentaje, 3)}%` }} />
                    </div>
                  )}
                </div>
                <div className="flex shrink-0 flex-row items-center gap-3 sm:flex-col sm:items-end">
                  {a.elementoTipo && <TipoBadge tipo={a.elementoTipo} />}
                  {a.fecha && <span className="text-xs text-pizarra">{fmtFechaHora(a.fecha)}</span>}
                  {destino && <Link to={ruta(destino)} className="inline-flex items-center gap-1 text-sm font-bold text-usco hover:underline">Atender <ArrowRight className="h-4 w-4" aria-hidden="true" /></Link>}
                </div>
              </li>
            )
          })}
        </ul>
      )}
    </div>
  )
}
