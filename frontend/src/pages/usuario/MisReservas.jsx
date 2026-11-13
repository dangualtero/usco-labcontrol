import { useState } from 'react'
import { Beaker, CalendarClock, CalendarX2, PackageCheck } from 'lucide-react'
import { api } from '../../api/index.js'
import { useToast } from '../../context/ToastContext.jsx'
import { useAhora, useDatos } from '../../lib/hooks.js'
import { fmtFechaHora, fmtFranja, fmtVencimiento } from '../../lib/fecha.js'
import { AlertaRetraso, Cargando, Confirmar, ErrorCaja, PrestamoBadge, Vacio } from '../../components/ui.jsx'
import { EstadoSolicitud, pedido } from '../auxiliar/SolicitudesInsumo.jsx'

export default function MisReservas() {
  const toast = useToast()
  const ahora = useAhora(30000)
  const { datos, error, recargar } = useDatos(() => api.misPrestamos(), [], { cada: 8000 })
  const [cancelar, setCancelar] = useState(null)
  const [cancelarInsumo, setCancelarInsumo] = useState(null)
  const { datos: insumos, recargar: recargarInsumos } = useDatos(() => api.misInsumos(), [], { cada: 10000 })

  const reservas = (datos ?? []).filter((p) => p.estado === 'RESERVADO')
  const activos = (datos ?? []).filter((p) => p.estado === 'ACTIVO')
  const rechazadas = (datos ?? []).filter((p) => p.estado === 'RECHAZADO').slice(0, 3)

  const confirmarCancelar = async () => {
    try { await api.cancelarReserva(cancelar.id); toast.exito('Reserva cancelada', cancelar.codigo); recargar() }
    catch (e) { toast.error('No se pudo cancelar', e.message) }
    setCancelar(null)
  }

  const confirmarCancelarInsumo = async () => {
    try { await api.cancelarInsumo(cancelarInsumo.id); toast.exito('Solicitud cancelada', cancelarInsumo.codigo); recargarInsumos() }
    catch (e) { toast.error('No se pudo cancelar', e.message) }
    setCancelarInsumo(null)
  }

  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-2xl font-extrabold">Mis reservas, préstamos e insumos</h1>
        <p className="text-sm text-pizarra">Equipos que tienes reservados o que ya retiraste de ventanilla, y los insumos que has pedido.</p>
      </div>
      <ErrorCaja mensaje={error} />
      {!datos && !error && <Cargando />}
      <AlertaRetraso prestamos={activos.filter((p) => p.vencido)} ahora={ahora} titulo="Devolución vencida: entrega el equipo en ventanilla lo antes posible." />

      {datos && (
        <>
          {rechazadas.length > 0 && (
            <div role="alert" className="rounded-xl border border-alerta-borde bg-alerta-fondo p-4">
              <p className="font-bold text-alerta-texto">Solicitudes rechazadas por ventanilla</p>
              <ul className="mt-1.5 space-y-1 text-sm text-alerta-texto">
                {rechazadas.map((p) => <li key={p.id}><span className="font-mono font-bold">{p.placa}</span> · {p.equipoNombre} — {p.observaciones}</li>)}
              </ul>
            </div>
          )}

          <section aria-labelledby="h-curso">
            <h2 id="h-curso" className="mb-3 flex items-center gap-2 text-lg font-extrabold"><PackageCheck className="h-5 w-5 text-usco" aria-hidden="true" /> En mi poder ({activos.length})</h2>
            {activos.length === 0 ? <Vacio icono={PackageCheck} titulo="No tienes equipos en préstamo" detalle="Cuando retires un equipo en ventanilla aparecerá aquí con su hora límite de devolución." /> : (
              <ul className="grid gap-3 md:grid-cols-2">
                {activos.map((p) => (
                  <li key={p.id} className={`card p-4 ${p.vencido ? 'border-alerta-borde bg-alerta-fondo' : ''}`}>
                    <div className="flex items-start justify-between gap-2"><div><p className="font-extrabold">{p.equipoNombre}</p><p className="text-sm text-pizarra"><span className="font-mono font-semibold">{p.placa}</span> · {p.laboratorio}</p></div><PrestamoBadge estado={p.estado} vencido={p.vencido} /></div>
                    <p className="mt-3 text-sm">Salida: <strong>{fmtFechaHora(p.fechaSalida)}</strong></p>
                    <p className={`text-sm font-bold ${p.vencido ? 'text-alerta-texto' : 'text-slate-800'}`}>Límite: {fmtFechaHora(p.fechaLimite)} · {fmtVencimiento(p.fechaLimite, ahora)}</p>
                  </li>
                ))}
              </ul>
            )}
          </section>

          <section aria-labelledby="h-res">
            <h2 id="h-res" className="mb-3 flex items-center gap-2 text-lg font-extrabold"><CalendarClock className="h-5 w-5 text-usco" aria-hidden="true" /> Reservas pendientes ({reservas.length})</h2>
            {reservas.length === 0 ? <Vacio icono={CalendarX2} titulo="No tienes reservas pendientes" detalle="Ve al catálogo, elige un equipo disponible y reserva tu franja en 3 pasos." /> : (
              <ul className="grid gap-3 md:grid-cols-2">
                {reservas.map((p) => (
                  <li key={p.id} className="card p-4">
                    <div className="flex items-start justify-between gap-2"><div><p className="font-extrabold">{p.equipoNombre}</p><p className="text-sm text-pizarra"><span className="font-mono font-semibold">{p.placa}</span> · {p.laboratorio}</p></div><PrestamoBadge estado={p.estado} /></div>
                    <p className="mt-3 text-sm">Franja: <strong>{fmtFranja(p.franjaInicio, p.franjaFin)}</strong></p>
                    <p className="text-sm">Asignatura: <strong>{p.asignatura}</strong>{p.docente ? <span className="text-pizarra"> · {p.docente}</span> : null}</p>
                    <div className="mt-3 flex items-center justify-between gap-2">
                      <span className="rounded bg-slate-100 px-2 py-1 font-mono text-sm font-bold text-usco">{p.codigo}</span>
                      <button className="btn-outline btn-sm" onClick={() => setCancelar(p)}>Cancelar reserva</button>
                    </div>
                  </li>
                ))}
              </ul>
            )}
          </section>
        </>
      )}
      <section aria-labelledby="h-ins">
        <h2 id="h-ins" className="mb-3 flex items-center gap-2 text-lg font-extrabold"><Beaker className="h-5 w-5 text-usco" aria-hidden="true" /> Mis solicitudes de insumos ({(insumos ?? []).length})</h2>
        {insumos && insumos.length === 0 ? <Vacio icono={Beaker} titulo="No has pedido insumos" detalle="En el catálogo, pestaña «Insumos», elige uno y pide la cantidad que necesitas." /> : (
          <ul className="grid gap-3 md:grid-cols-2">
            {(insumos ?? []).slice(0, 20).map((s) => (
              <li key={s.id} className={`card p-4 ${s.estado === 'RECHAZADA' ? 'border-alerta-borde' : ''}`}>
                <div className="flex items-start justify-between gap-2">
                  <div><p className="font-extrabold">{s.insumo}</p><p className="text-sm text-pizarra"><span className="font-mono font-semibold">{s.placa}</span> · {s.laboratorio}</p></div>
                  <EstadoSolicitud estado={s.estado} />
                </div>
                <p className="mt-2 text-lg font-extrabold">{pedido(s)}</p>
                <p className="text-sm text-slate-700">«{s.motivo}»</p>
                <p className="text-xs text-pizarra">{fmtFechaHora(s.creadoEn)}{s.resueltoPor ? ` · atendió ${s.resueltoPor}` : ''}</p>
                {s.respuesta && <p className={`mt-1 text-sm ${s.estado === 'RECHAZADA' ? 'font-semibold text-alerta-texto' : 'text-pizarra'}`}>{s.respuesta}</p>}
                {s.estado === 'PENDIENTE' && (
                  <div className="mt-3 flex items-center justify-between gap-2">
                    <span className="rounded bg-slate-100 px-2 py-1 font-mono text-sm font-bold text-usco">{s.codigo}</span>
                    <button className="btn-outline btn-sm" onClick={() => setCancelarInsumo(s)}>Cancelar solicitud</button>
                  </div>
                )}
              </li>
            ))}
          </ul>
        )}
      </section>

      <Confirmar abierto={!!cancelarInsumo} titulo="Cancelar solicitud de insumo" mensaje={`¿Cancelar la solicitud ${cancelarInsumo?.codigo}?`} textoOk="Sí, cancelar" textoCancelar="Mantener" peligro
        onCancelar={() => setCancelarInsumo(null)} onOk={confirmarCancelarInsumo} />
      <Confirmar abierto={!!cancelar} titulo="Cancelar reserva" mensaje={`¿Cancelar la reserva ${cancelar?.codigo}?`} textoOk="Sí, cancelar reserva" textoCancelar="Mantener reserva" peligro
        onCancelar={() => setCancelar(null)} onOk={confirmarCancelar} />
    </div>
  )
}
