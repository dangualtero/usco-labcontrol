import { Link } from 'react-router-dom'
import {
  ArrowRight, Beaker, CalendarClock, ClipboardCheck, ClipboardList, FlaskConical, ListChecks, Package, PowerOff, RefreshCw, Wrench,
} from 'lucide-react'
import { api } from '../../api/index.js'
import { useAuth } from '../../context/AuthContext.jsx'
import { useSede } from '../../context/SedeContext.jsx'
import { useDatos } from '../../lib/hooks.js'
import { pesos, puede } from '../../lib/constantes.js'
import { fmtFecha, fmtFechaHora } from '../../lib/fecha.js'
import { Cargando, ErrorCaja, Kpi } from '../../components/ui.jsx'
import { ESTADOS_SOLICITUD, Encabezado, EstadoCompromiso, EstadoMantenimiento, EstadoSolicitud, Validacion } from '../../components/Estados.jsx'

function Panel({ titulo, icono: Ic, enlace, children, vacio, hay }) {
  const { ruta } = useSede()
  return (
    <section className="card overflow-hidden" aria-label={titulo}>
      <div className="flex items-center justify-between gap-2 border-b border-slate-200 px-4 py-3">
        <h2 className="flex items-center gap-2 font-extrabold"><Ic className="h-4 w-4 text-usco" aria-hidden="true" /> {titulo}</h2>
        {enlace && <Link to={ruta(enlace)} className="inline-flex items-center gap-1 text-sm font-bold text-usco hover:underline">Ver todo <ArrowRight className="h-4 w-4" aria-hidden="true" /></Link>}
      </div>
      {hay ? <ul className="divide-y divide-slate-100">{children}</ul> : <p className="px-4 py-6 text-center text-sm text-pizarra">{vacio}</p>}
    </section>
  )
}

/** Tablero de control: indicadores de los laboratorios de la cuenta y lo que requiere atención. */
export default function Tablero() {
  const { usuario } = useAuth()
  const { sede, ruta } = useSede()
  const { datos: t, error, cargando, recargar } = useDatos(() => api.tablero(), [], { cada: 30000 })
  const admin = usuario.rol === 'ADMINISTRADOR'
  const total = t?.indicadores.total
  const verValores = puede(usuario, 'PRESUPUESTO_CONSULTAR')
  const valida = puede(usuario, 'NOVEDAD_VALIDAR')

  return (
    <div className="space-y-6">
      <Encabezado modulo={`Tablero de control · ${sede?.nombre}`} titulo={`Hola, ${usuario.nombre.split(' ')[0]}`}
        detalle={admin ? 'Seguimiento institucional de todos los laboratorios de la sede.' : `${usuario.rolTexto} de ${usuario.laboratorios.map((l) => l.nombre).join(', ')}.`}>
        <button onClick={recargar} disabled={cargando} className="btn-outline"><RefreshCw className={`h-4 w-4 ${cargando ? 'animate-spin' : ''}`} aria-hidden="true" /> Actualizar</button>
      </Encabezado>

      <ErrorCaja mensaje={error} />
      {!t && !error && <Cargando texto="Calculando indicadores…" />}

      {t && (
        <>
          <div className="grid grid-cols-2 gap-3 lg:grid-cols-4">
            <Kpi etiqueta="Equipos e insumos" valor={total.equipos + total.insumos} detalle={`${total.equipos} equipos · ${total.insumos} insumos`} icono={Package} />
            <Kpi etiqueta="Insumos en alerta" valor={total.insumosEnAlerta} detalle={`${total.equiposFueraServicio} equipo(s) fuera de servicio`} icono={total.insumosEnAlerta ? Beaker : PowerOff}
              tono={total.insumosEnAlerta ? 'text-alerta-borde' : 'text-usco'} fondo={total.insumosEnAlerta ? 'bg-alerta-fondo' : 'bg-usco-soft'} />
            <Kpi etiqueta="Mantenimientos abiertos" valor={total.mantenimientosAbiertos} detalle={`${total.mantenimientosVencidos} vencido(s) · ${total.mantenimientosRealizados} realizado(s)`} icono={Wrench}
              tono={total.mantenimientosVencidos ? 'text-alerta-borde' : 'text-amber-700'} fondo={total.mantenimientosVencidos ? 'bg-alerta-fondo' : 'bg-amber-50'} />
            <Kpi etiqueta="Solicitudes en trámite" valor={total.solicitudesEnTramite} detalle={`${total.solicitudesAprobadas} aprobada(s) · ${total.solicitudesRechazadas} rechazada(s)`} icono={ClipboardList} tono="text-salida" fondo="bg-blue-50" />
            <Kpi etiqueta="Novedades por validar" valor={total.novedadesPorValidar} detalle={`${total.novedadesAbiertas} validada(s) sin resolver`} icono={ClipboardCheck}
              tono={total.novedadesPorValidar ? 'text-amber-700' : 'text-usco'} fondo={total.novedadesPorValidar ? 'bg-amber-50' : 'bg-usco-soft'} />
            <Kpi etiqueta="Compromisos" valor={total.compromisos} detalle={`${total.compromisosCumplidos} cumplido(s) · ${total.compromisosVencidos} vencido(s)`} icono={ListChecks}
              tono={total.compromisosVencidos ? 'text-alerta-borde' : 'text-usco'} fondo={total.compromisosVencidos ? 'bg-alerta-fondo' : 'bg-usco-soft'} />
            <Kpi etiqueta="Valor solicitado" valor={pesos(total.valorSolicitado)} detalle={verValores ? `Aprobado: ${pesos(total.valorAprobado)}` : 'Solicitudes enviadas'} icono={ClipboardList} />
            <Kpi etiqueta="Documentos" valor={total.documentos} detalle={`${total.avancesPendientes} avance(s) por verificar`} icono={FlaskConical} />
          </div>

          {Object.keys(t.solicitudesPorEstado).length > 0 && (
            <section className="card p-4" aria-label="Solicitudes por estado">
              <h2 className="mb-3 font-extrabold">Solicitudes por estado</h2>
              <div className="flex flex-wrap gap-2">
                {Object.entries(t.solicitudesPorEstado).map(([e, n]) => (
                  <Link key={e} to={`${ruta('/solicitudes')}?estado=${e}`} className="inline-flex items-center gap-2 rounded-lg border border-slate-200 px-3 py-2 hover:border-usco">
                    <EstadoSolicitud estado={e} /> <span className="font-titulo text-lg font-extrabold">{n}</span>
                    <span className="sr-only">{ESTADOS_SOLICITUD[e]?.texto}</span>
                  </Link>
                ))}
              </div>
            </section>
          )}

          <div className="grid gap-5 xl:grid-cols-2">
            <Panel titulo={valida ? 'Novedades por validar' : 'Novedades recientes'} icono={ClipboardCheck} enlace="/novedades" hay={t.novedadesPorValidar.length > 0}
              vacio={valida ? 'No hay registros preliminares esperando validación.' : 'Sin novedades recientes.'}>
              {t.novedadesPorValidar.map((n) => (
                <li key={n.id} className="flex flex-wrap items-center justify-between gap-2 px-4 py-2.5 text-sm">
                  <span className="min-w-0"><span className="placa">{n.placa}</span> <strong>{n.tipoTexto}</strong>
                    <span className="block truncate text-xs text-pizarra">{n.equipoNombre} · {n.reportadoPor} · {fmtFechaHora(n.creadoEn)}</span></span>
                  <Validacion estado={n.validacion} />
                </li>
              ))}
            </Panel>
            <Panel titulo="Próximos mantenimientos" icono={CalendarClock} enlace="/mantenimientos" hay={t.proximosMantenimientos.length > 0} vacio="No hay mantenimientos programados.">
              {t.proximosMantenimientos.map((m) => (
                <li key={m.id} className="flex flex-wrap items-center justify-between gap-2 px-4 py-2.5 text-sm">
                  <span className="min-w-0"><span className="placa">{m.placa}</span> <strong>{m.tipoTexto}</strong>
                    <span className="block truncate text-xs text-pizarra">{m.equipo} · {m.laboratorio} · {fmtFecha(m.fechaProgramada)}</span></span>
                  <EstadoMantenimiento estado={m.estado} vencido={m.vencido} />
                </li>
              ))}
            </Panel>
            <Panel titulo="Solicitudes recientes" icono={ClipboardList} enlace="/solicitudes" hay={t.solicitudesRecientes.length > 0} vacio="Aún no hay solicitudes.">
              {t.solicitudesRecientes.map((s) => (
                <li key={s.id}>
                  <Link to={ruta(`/solicitudes/${s.id}`)} className="flex flex-wrap items-center justify-between gap-2 px-4 py-2.5 text-sm hover:bg-slate-50">
                    <span className="min-w-0"><span className="font-mono text-xs font-bold text-pizarra">{s.codigo}</span> <strong>{s.titulo}</strong>
                      <span className="block truncate text-xs text-pizarra">{s.laboratorio} · {s.creadoPor} · {pesos(s.valorEstimado)}</span></span>
                    <EstadoSolicitud estado={s.estado} />
                  </Link>
                </li>
              ))}
            </Panel>
            <Panel titulo="Compromisos próximos" icono={ListChecks} enlace="/seguimiento" hay={t.compromisosProximos.length > 0} vacio="No hay compromisos abiertos.">
              {t.compromisosProximos.map((c) => (
                <li key={c.id} className="flex flex-wrap items-center justify-between gap-2 px-4 py-2.5 text-sm">
                  <span className="min-w-0"><strong>{c.titulo}</strong>
                    <span className="block truncate text-xs text-pizarra">{c.laboratorio} · {c.responsable ?? 'Sin responsable'} · vence {fmtFecha(c.fechaLimite)}{c.ultimoPorcentaje != null ? ` · ${c.ultimoPorcentaje} %` : ''}</span></span>
                  <EstadoCompromiso estado={c.estado} vencido={c.vencido} />
                </li>
              ))}
            </Panel>
          </div>

          {t.indicadores.laboratorios.length > 1 && (
            <section className="card overflow-hidden" aria-labelledby="h-labs">
              <h2 id="h-labs" className="border-b border-slate-200 px-4 py-3 font-extrabold">Indicadores por laboratorio</h2>
              <div className="overflow-x-auto">
                <table className="w-full min-w-[760px]">
                  <thead><tr><th className="th">Laboratorio</th><th className="th">Inventario</th><th className="th">En alerta</th><th className="th">Mant. abiertos</th><th className="th">Novedades por validar</th><th className="th">Solicitudes en trámite</th><th className="th">Compromisos vencidos</th></tr></thead>
                  <tbody>
                    {t.indicadores.laboratorios.map((l) => (
                      <tr key={l.laboratorioId}>
                        <td className="td"><Link to={ruta(`/laboratorios/${l.laboratorioId}`)} className="font-bold text-usco hover:underline">{l.laboratorio}</Link></td>
                        <td className="td">{l.equipos} eq. · {l.insumos} ins.</td>
                        <td className={`td font-bold ${l.insumosEnAlerta ? 'text-alerta-texto' : ''}`}>{l.insumosEnAlerta}</td>
                        <td className="td">{l.mantenimientosAbiertos}{l.mantenimientosVencidos ? <span className="font-bold text-alerta-texto"> ({l.mantenimientosVencidos} vencidos)</span> : ''}</td>
                        <td className="td">{l.novedadesPorValidar}</td>
                        <td className="td">{l.solicitudesEnTramite}</td>
                        <td className={`td font-bold ${l.compromisosVencidos ? 'text-alerta-texto' : ''}`}>{l.compromisosVencidos}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            </section>
          )}
        </>
      )}
    </div>
  )
}
