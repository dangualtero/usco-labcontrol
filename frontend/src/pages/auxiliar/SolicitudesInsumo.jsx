import { useState } from 'react'
import { Beaker, Check, ClipboardList, RefreshCw, TriangleAlert, X } from 'lucide-react'
import { api } from '../../api/index.js'
import { useToast } from '../../context/ToastContext.jsx'
import { useDatos } from '../../lib/hooks.js'
import { fmtCantidad } from '../../lib/constantes.js'
import { fmtFechaHora } from '../../lib/fecha.js'
import { Campo, Cargando, ErrorCaja, Modal, Vacio } from '../../components/ui.jsx'

export const ESTADO_SOLICITUD = {
  PENDIENTE: ['Pendiente', 'border-blue-600 bg-blue-50 text-blue-900'],
  APROBADA: ['Aprobada', 'border-emerald-600 bg-emerald-50 text-emerald-800'],
  RECHAZADA: ['Rechazada', 'border-alerta-borde bg-alerta-fondo text-alerta-texto'],
  CANCELADA: ['Cancelada', 'border-slate-400 bg-slate-100 text-slate-700'],
}
export function EstadoSolicitud({ estado }) {
  const [t, c] = ESTADO_SOLICITUD[estado] ?? ESTADO_SOLICITUD.CANCELADA
  return <span className={`inline-flex items-center rounded-full border px-2.5 py-0.5 text-xs font-bold ${c}`}>{t}</span>
}

/** Cantidad pedida y, si se pidió en otra unidad, su equivalente en la unidad del insumo. */
export const pedido = (s) => fmtCantidad(s.cantidad, s.unidad) + (s.unidad !== s.unidadInsumo ? ` (= ${fmtCantidad(s.cantidadEquivalente, s.unidadInsumo)})` : '')

/**
 * Solicitudes de insumos (auxiliar): el estudiante o docente pide una cantidad; al aprobarla se descuenta de las
 * existencias y queda en el historial como CONSUMO. Al rechazarla hay que indicar el motivo (lo ve el solicitante).
 */
export default function SolicitudesInsumo({ soloLectura = false, cargar = api.solicitudesInsumoAux }) {
  const toast = useToast()
  const { datos, error, cargando, recargar } = useDatos(cargar, [], { cada: 8000 })
  const [accion, setAccion] = useState(null) // { tipo: 'aprobar' | 'rechazar', s }
  const [texto, setTexto] = useState('')
  const [err, setErr] = useState('')
  const [enviando, setEnviando] = useState(false)

  const pendientes = (datos ?? []).filter((s) => s.estado === 'PENDIENTE')
  const resueltas = (datos ?? []).filter((s) => s.estado !== 'PENDIENTE')

  const abrir = (tipo, s) => { setAccion({ tipo, s }); setTexto(''); setErr('') }
  const confirmar = async () => {
    const { tipo, s } = accion
    if (tipo === 'rechazar' && texto.trim().length < 5) { setErr('Indica el motivo del rechazo (mínimo 5 caracteres)'); return }
    setEnviando(true); setErr('')
    try {
      if (tipo === 'aprobar') {
        const r = await api.aprobarInsumo(s.id, texto.trim() || null)
        toast.exito('Solicitud aprobada', `${s.insumo}: entregado ${pedido(s)}. Quedan ${fmtCantidad(r.existencias, r.unidadInsumo)}.`)
      } else {
        await api.rechazarInsumo(s.id, texto.trim())
        toast.exito('Solicitud rechazada', `${s.codigo} · ${s.solicitanteNombre}`)
      }
      setAccion(null); recargar()
    } catch (e) { setErr(e.message) } finally { setEnviando(false) }
  }

  return (
    <div className="space-y-6">
      <div className="flex flex-col gap-3 sm:flex-row sm:items-end sm:justify-between">
        <div>
          <p className="text-xs font-bold uppercase tracking-widest text-usco">Insumos</p>
          <h1 className="text-2xl font-extrabold sm:text-3xl">Solicitudes de insumos</h1>
          <p className="text-sm text-pizarra">{soloLectura
            ? 'Pedidos de insumos de la sede. Los atiende el auxiliar de ventanilla.'
            : 'Los estudiantes y docentes piden una cantidad. Al aprobar, se descuenta del inventario automáticamente.'}</p>
        </div>
        <button onClick={recargar} disabled={cargando} className="btn-outline"><RefreshCw className={`h-4 w-4 ${cargando ? 'animate-spin' : ''}`} aria-hidden="true" /> Actualizar</button>
      </div>

      <ErrorCaja mensaje={error} />
      {!datos && !error && <Cargando />}

      {datos && (
        <>
          <section aria-labelledby="h-pend">
            <h2 id="h-pend" className="mb-3 flex items-center gap-2 text-lg font-extrabold"><ClipboardList className="h-5 w-5 text-usco" aria-hidden="true" /> Pendientes ({pendientes.length})</h2>
            {pendientes.length === 0 ? <Vacio icono={Beaker} titulo="No hay solicitudes pendientes" detalle="Cuando un estudiante o docente pida un insumo aparecerá aquí." /> : (
              <ul className="grid gap-3 md:grid-cols-2">
                {pendientes.map((s) => {
                  const noAlcanza = Number(s.cantidadEquivalente) > Number(s.existencias)
                  return (
                    <li key={s.id} className="card flex flex-col gap-2 p-4 aparecer">
                      <div className="flex items-start justify-between gap-2">
                        <div className="min-w-0"><p className="font-extrabold">{s.insumo}</p><p className="text-sm text-pizarra"><span className="placa">{s.placa}</span> · {s.laboratorio}</p></div>
                        <span className="rounded bg-slate-100 px-2 py-0.5 font-mono text-xs font-bold text-usco">{s.codigo}</span>
                      </div>
                      <p className="text-2xl font-extrabold text-slate-900">{pedido(s)}</p>
                      <p className={`text-sm ${noAlcanza ? 'font-bold text-alerta-texto' : 'text-pizarra'}`}>
                        {noAlcanza && <TriangleAlert className="mr-1 inline h-4 w-4" aria-hidden="true" />}
                        Existencias: {fmtCantidad(s.existencias, s.unidadInsumo)}{noAlcanza ? ' · no alcanza' : ''}
                      </p>
                      <p className="text-sm"><strong>{s.solicitanteNombre}</strong> <span className="text-pizarra">· doc. {s.solicitanteDocumento}</span></p>
                      <p className="text-sm text-slate-700">«{s.motivo}»{s.asignatura ? <span className="text-pizarra"> · {s.asignatura}</span> : null}</p>
                      <p className="text-xs text-pizarra">{fmtFechaHora(s.creadoEn)}</p>
                      {!soloLectura && (
                        <div className="mt-auto flex gap-2 pt-2">
                          <button className="btn-primary btn-sm flex-1" disabled={noAlcanza} onClick={() => abrir('aprobar', s)}><Check className="h-4 w-4" aria-hidden="true" /> Aprobar y entregar</button>
                          <button className="btn-outline btn-sm flex-1" onClick={() => abrir('rechazar', s)}><X className="h-4 w-4" aria-hidden="true" /> Rechazar</button>
                        </div>
                      )}
                    </li>
                  )
                })}
              </ul>
            )}
          </section>

          <section aria-labelledby="h-res">
            <h2 id="h-res" className="mb-3 text-lg font-extrabold">Atendidas recientemente ({resueltas.length})</h2>
            {resueltas.length === 0 ? <p className="text-sm text-pizarra">Aún no hay solicitudes atendidas.</p> : (
              <div className="card overflow-hidden">
                <div className="relative overflow-x-auto">
                  <table className="w-full min-w-[820px]">
                    <thead><tr><th className="th">Solicitud</th><th className="th">Insumo</th><th className="th">Cantidad</th><th className="th">Solicitante</th><th className="th">Estado</th><th className="th">Atendió</th></tr></thead>
                    <tbody>
                      {resueltas.slice(0, 60).map((s) => (
                        <tr key={s.id}>
                          <td className="td"><span className="font-mono text-xs font-bold">{s.codigo}</span><p className="text-xs text-pizarra">{fmtFechaHora(s.creadoEn)}</p></td>
                          <td className="td"><p className="font-semibold">{s.insumo}</p><p className="placa">{s.placa}</p></td>
                          <td className="td font-semibold">{pedido(s)}</td>
                          <td className="td">{s.solicitanteNombre}</td>
                          <td className="td"><EstadoSolicitud estado={s.estado} />{s.respuesta && <p className="mt-1 max-w-[220px] text-xs text-pizarra">{s.respuesta}</p>}</td>
                          <td className="td text-xs">{s.resueltoPor ?? '—'}<p className="text-pizarra">{s.resueltoEn ? fmtFechaHora(s.resueltoEn) : ''}</p></td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              </div>
            )}
          </section>
        </>
      )}

      <Modal abierto={!!accion} onCerrar={() => setAccion(null)} ancho="max-w-md"
        titulo={accion?.tipo === 'aprobar' ? 'Aprobar y entregar' : 'Rechazar solicitud'}
        subtitulo={accion ? `${accion.s.codigo} · ${accion.s.insumo} · ${pedido(accion.s)}` : undefined}
        acento={accion?.tipo === 'aprobar' ? 'bg-entrada' : 'bg-alerta-borde'}
        pie={<>
          <button className="btn-outline" onClick={() => setAccion(null)}>Cancelar</button>
          <button className={accion?.tipo === 'aprobar' ? 'btn-primary' : 'btn-danger'} onClick={confirmar} disabled={enviando}>
            {enviando ? 'Guardando…' : accion?.tipo === 'aprobar' ? 'Aprobar y descontar' : 'Rechazar'}
          </button>
        </>}>
        {accion && (
          <div className="space-y-3">
            {accion.tipo === 'aprobar'
              ? <p className="text-sm text-slate-700">Se descontará <strong>{fmtCantidad(accion.s.cantidadEquivalente, accion.s.unidadInsumo)}</strong> de las existencias ({fmtCantidad(accion.s.existencias, accion.s.unidadInsumo)}). Entrega el insumo a <strong>{accion.s.solicitanteNombre}</strong>.</p>
              : <p className="text-sm text-slate-700">El solicitante verá el motivo en «Mis reservas».</p>}
            <Campo etiqueta={accion.tipo === 'aprobar' ? 'Nota (opcional)' : 'Motivo del rechazo'} htmlFor="si-texto" error={err}>
              <textarea id="si-texto" rows={3} className={`input ${err ? 'input-error' : ''}`} value={texto} onChange={(e) => setTexto(e.target.value)} maxLength={300}
                placeholder={accion.tipo === 'aprobar' ? 'Ej. Entregado en frasco de 250 mL' : 'Ej. No hay práctica programada para esa asignatura'} />
            </Campo>
          </div>
        )}
      </Modal>
    </div>
  )
}
