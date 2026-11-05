import { useState } from 'react'
import { Link } from 'react-router-dom'
import { ArrowDownLeft, ArrowUpRight, Ban, CalendarClock, CircleCheck, Clock, RefreshCw, Repeat2, Search, Wrench, TriangleAlert, ClipboardList, UserRound } from 'lucide-react'
import { api } from '../../api/index.js'
import { useAuth } from '../../context/AuthContext.jsx'
import { useAhora, useDatos } from '../../lib/hooks.js'
import { fmtFechaHora, fmtFranja, fmtHora, fmtVencimiento } from '../../lib/fecha.js'
import { AlertaRetraso, Cargando, ErrorCaja, Kpi, PrestamoBadge, Vacio } from '../../components/ui.jsx'
import SalidaModal from './SalidaModal.jsx'
import EntradaModal from './EntradaModal.jsx'
import RechazoModal from './RechazoModal.jsx'

export default function Ventanilla() {
  const { usuario } = useAuth()
  const ahora = useAhora(30000)
  const { datos, error, cargando, recargar } = useDatos(() => api.panel(), [], { cada: 10000 })
  const [salida, setSalida] = useState({ abierto: false, inicial: null })
  const [entrada, setEntrada] = useState({ abierto: false, inicial: null })
  const [rechazo, setRechazo] = useState(null)
  const [doc, setDoc] = useState('')
  const [buscado, setBuscado] = useState(null)
  const [errBusqueda, setErrBusqueda] = useState('')

  const vencidos = (datos?.activos ?? []).filter((p) => p.vencido)

  const buscar = async (e) => {
    e?.preventDefault()
    setErrBusqueda(''); setBuscado(null)
    if (!doc.trim()) { setErrBusqueda('Ingresa el código estudiantil o el documento'); return }
    try { setBuscado(await api.solicitudesUsuario(doc.trim())) } catch (err) { setErrBusqueda(err.message) }
  }

  const abrirSalida = (inicial = null) => setSalida({ abierto: true, inicial })
  const abrirEntrada = (inicial = null) => setEntrada({ abierto: true, inicial })
  const refrescar = () => { recargar(); if (buscado) buscar() }

  return (
    <div className="space-y-6">
      <div className="flex flex-col gap-4 lg:flex-row lg:items-end lg:justify-between">
        <div>
          <p className="text-xs font-bold uppercase tracking-widest text-usco">Panel de Ventanilla</p>
          <h1 className="text-2xl font-extrabold sm:text-3xl">Gestión de entradas y salidas</h1>
          <p className="mt-0.5 flex flex-wrap items-center gap-x-3 gap-y-1 text-sm text-pizarra"><span className="flex items-center gap-1.5"><UserRound className="h-4 w-4" aria-hidden="true" /> Turno de {usuario.nombre}</span>
            <button onClick={recargar} disabled={cargando} className="inline-flex items-center gap-1.5 rounded-md px-2 py-1 font-semibold text-usco hover:bg-usco-soft" aria-label="Actualizar panel"><RefreshCw className={`h-3.5 w-3.5 ${cargando ? 'animate-spin' : ''}`} aria-hidden="true" /> Actualizar</button></p>
        </div>
        {/* PA-07 / PU-05: acciones con ícono + texto y color funcional: AZUL salida, VERDE entrada */}
        <div className="grid grid-cols-1 gap-2 sm:grid-cols-3 lg:min-w-[640px]">
          <button className="btn-salida !min-h-[52px] text-[15px]" onClick={() => abrirSalida()}><ArrowUpRight className="h-5 w-5" aria-hidden="true" /> Registrar Salida</button>
          <button className="btn-entrada !min-h-[52px] text-[15px]" onClick={() => abrirEntrada()}><ArrowDownLeft className="h-5 w-5" aria-hidden="true" /> Registrar Entrada</button>
          <Link to="/auxiliar/turno" className="btn-outline !min-h-[52px] text-[15px]"><Repeat2 className="h-5 w-5" aria-hidden="true" /> Cierre de Turno</Link>
        </div>
      </div>

      <ErrorCaja mensaje={error} />
      {!datos && !error && <Cargando texto="Cargando panel de ventanilla…" />}

      {datos && (
        <>
          <div className="grid grid-cols-2 gap-3 lg:grid-cols-4">
            <Kpi etiqueta="Disponibles" valor={datos.resumen.disponibles} icono={CircleCheck} tono="text-entrada" fondo="bg-emerald-50" />
            <Kpi etiqueta="Prestados" valor={datos.resumen.prestados} icono={Clock} tono="text-amber-700" fondo="bg-amber-50" />
            <Kpi etiqueta="En mantenimiento" valor={datos.resumen.mantenimiento} icono={Wrench} tono="text-pizarra" fondo="bg-slate-100" />
            <Kpi etiqueta="Vencidos" valor={datos.resumen.vencidos} icono={TriangleAlert} tono="text-alerta-borde" fondo="bg-alerta-fondo" />
          </div>

          <AlertaRetraso prestamos={vencidos} ahora={ahora} />

          {/* Búsqueda por documento (PF-07) */}
          <section className="card p-4 sm:p-5" aria-labelledby="h-buscar">
            <h2 id="h-buscar" className="mb-3 text-base font-extrabold">Buscar solicitudes por código o documento</h2>
            <form onSubmit={buscar} className="flex flex-col gap-2 sm:flex-row" role="search">
              <label htmlFor="doc" className="sr-only">Código estudiantil o documento</label>
              <input id="doc" value={doc} onChange={(e) => setDoc(e.target.value)} placeholder="Código estudiantil o documento (ej. 20211100456)" inputMode="numeric" className={`input flex-1 ${errBusqueda ? 'input-error' : ''}`} />
              <button className="btn-primary" type="submit"><Search className="h-4 w-4" aria-hidden="true" /> Buscar</button>
            </form>
            {errBusqueda && <p role="alert" className="mt-2 flex items-center gap-1.5 text-sm font-semibold text-alerta-borde"><TriangleAlert className="h-4 w-4" aria-hidden="true" /> {errBusqueda}</p>}
            {buscado && (
              <div className="mt-4 rounded-xl border border-slate-200 bg-slate-50 p-4 aparecer">
                <div className="flex flex-wrap items-center justify-between gap-2">
                  <div><p className="font-extrabold">{buscado.nombre}</p><p className="text-sm text-pizarra">{buscado.documento} · {buscado.programa}</p></div>
                  <span className={`rounded-full px-3 py-1 text-xs font-bold ${buscado.activos >= buscado.limite ? 'bg-alerta-fondo text-alerta-texto ring-1 ring-alerta-borde' : 'bg-white text-pizarra-oscuro ring-1 ring-slate-300'}`}>{buscado.activos} de {buscado.limite} equipos en préstamo</span>
                </div>
                {buscado.reservas.length === 0 ? <p className="mt-3 text-sm text-pizarra">No tiene solicitudes activas. Puedes registrar una salida directa.</p> : (
                  <ul className="mt-3 divide-y divide-slate-200 rounded-lg bg-white ring-1 ring-slate-200">
                    {buscado.reservas.map((r) => (
                      <li key={r.id} className="flex flex-wrap items-center justify-between gap-2 px-3 py-2.5">
                        <div className="text-sm"><p className="font-bold">{r.equipoNombre} <span className="font-mono text-[13px] font-semibold text-pizarra">({r.placa})</span></p><p className="text-pizarra">{r.laboratorio} · {fmtFranja(r.franjaInicio, r.franjaFin)} · {r.codigo}</p></div>
                        <div className="flex gap-2">
                          <button className="btn-outline btn-sm" onClick={() => setRechazo(r)}><Ban className="h-4 w-4" aria-hidden="true" /> Rechazar</button>
                          <button className="btn-salida btn-sm" onClick={() => abrirSalida({ placa: r.placa, documento: buscado.documento })}><ArrowUpRight className="h-4 w-4" aria-hidden="true" /> Registrar Salida</button>
                        </div>
                      </li>
                    ))}
                  </ul>
                )}
              </div>
            )}
          </section>

          <div className="grid gap-6 xl:grid-cols-[1.7fr_1fr]">
            <section aria-labelledby="h-activos">
              <h2 id="h-activos" className="mb-3 flex items-center gap-2 text-lg font-extrabold"><ClipboardList className="h-5 w-5 text-usco" aria-hidden="true" /> Préstamos activos ({datos.activos.length})</h2>
              {datos.activos.length === 0 ? <Vacio icono={ClipboardList} titulo="No hay equipos en préstamo" detalle="Cuando registres una salida aparecerá aquí." /> : (
                <>
                  <div className="card hidden overflow-hidden md:block">
                    <table className="w-full">
                      <thead><tr><th className="th">Equipo</th><th className="th">Usuario</th><th className="th">Salida</th><th className="th">Devolución</th><th className="th"><span className="sr-only">Acción</span></th></tr></thead>
                      <tbody>
                        {datos.activos.map((p) => (
                          <tr key={p.id} className={p.vencido ? 'bg-alerta-fondo/60' : ''}>
                            <td className="td"><p className="font-bold text-slate-900">{p.equipoNombre}</p><p className="placa">{p.placa} · {p.laboratorio}</p></td>
                            <td className="td"><p className="font-semibold">{p.solicitanteNombre}</p><p className="text-xs text-pizarra">{p.solicitanteDocumento}</p></td>
                            <td className="td whitespace-nowrap">{fmtHora(p.fechaSalida)}<span className="block text-xs text-pizarra">{fmtFechaHora(p.fechaSalida).split(' · ')[0]}</span></td>
                            <td className="td"><PrestamoBadge estado={p.estado} vencido={p.vencido} /><span className={`mt-1 block text-xs font-semibold ${p.vencido ? 'text-alerta-texto' : 'text-pizarra'}`}>{fmtVencimiento(p.fechaLimite, ahora)}</span></td>
                            <td className="td text-right"><button className="btn-entrada btn-sm" onClick={() => abrirEntrada(p)}><ArrowDownLeft className="h-4 w-4" aria-hidden="true" /> Entrada</button></td>
                          </tr>
                        ))}
                      </tbody>
                    </table>
                  </div>
                  <ul className="space-y-3 md:hidden">
                    {datos.activos.map((p) => (
                      <li key={p.id} className={`card p-4 ${p.vencido ? 'border-alerta-borde bg-alerta-fondo' : ''}`}>
                        <div className="flex items-start justify-between gap-2"><div><p className="font-extrabold">{p.equipoNombre}</p><p className="placa">{p.placa}</p></div><PrestamoBadge estado={p.estado} vencido={p.vencido} /></div>
                        <p className="mt-1 text-sm">{p.solicitanteNombre} · {fmtVencimiento(p.fechaLimite, ahora)}</p>
                        <button className="btn-entrada mt-3 w-full" onClick={() => abrirEntrada(p)}><ArrowDownLeft className="h-4 w-4" aria-hidden="true" /> Registrar Entrada</button>
                      </li>
                    ))}
                  </ul>
                </>
              )}
            </section>

            <section aria-labelledby="h-reservas">
              <h2 id="h-reservas" className="mb-3 flex items-center gap-2 text-lg font-extrabold"><CalendarClock className="h-5 w-5 text-usco" aria-hidden="true" /> Solicitudes por entregar ({datos.reservas.length})</h2>
              {datos.reservas.length === 0 ? <Vacio icono={CalendarClock} titulo="Sin solicitudes pendientes" /> : (
                <ul className="space-y-3">
                  {datos.reservas.map((r) => (
                    <li key={r.id} className="card p-4">
                      <p className="font-extrabold leading-snug">{r.equipoNombre}</p>
                      <p className="text-sm text-pizarra"><span className="font-mono font-semibold">{r.placa}</span> · {r.laboratorio}</p>
                      <p className="mt-2 text-sm"><strong>{r.solicitanteNombre}</strong> · {r.solicitanteDocumento}</p>
                      <p className="text-sm text-pizarra">{fmtFranja(r.franjaInicio, r.franjaFin)}</p>
                      {(r.asignatura || r.docente) && <p className="text-sm text-pizarra">{r.asignatura}{r.docente ? ` · ${r.docente}` : ''}</p>}
                      <div className="mt-3 grid grid-cols-[auto_1fr] gap-2">
                        <button className="btn-outline btn-sm" onClick={() => setRechazo(r)}><Ban className="h-4 w-4" aria-hidden="true" /> Rechazar</button>
                        <button className="btn-salida btn-sm" onClick={() => abrirSalida({ placa: r.placa, documento: r.solicitanteDocumento })}><ArrowUpRight className="h-4 w-4" aria-hidden="true" /> Aprobar y registrar salida</button>
                      </div>
                    </li>
                  ))}
                </ul>
              )}
            </section>
          </div>
        </>
      )}

      <SalidaModal abierto={salida.abierto} inicial={salida.inicial} onCerrar={() => setSalida({ abierto: false, inicial: null })} onHecho={refrescar} />
      <RechazoModal solicitud={rechazo} onCerrar={() => setRechazo(null)} onHecho={refrescar} />
      <EntradaModal abierto={entrada.abierto} activos={datos?.activos} inicial={entrada.inicial} onCerrar={() => setEntrada({ abierto: false, inicial: null })} onHecho={refrescar} />
    </div>
  )
}
