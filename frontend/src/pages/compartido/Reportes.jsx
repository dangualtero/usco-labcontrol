import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import { BellRing, CircleCheck, CircleDot, Hourglass, MailCheck, MessageSquarePlus, Radio, RefreshCw, Search, Send, Siren } from 'lucide-react'
import { useAuth } from '../../context/AuthContext.jsx'
import { useSede } from '../../context/SedeContext.jsx'
import { useToast } from '../../context/ToastContext.jsx'
import { norm } from '../../lib/constantes.js'
import { fmtFechaHora } from '../../lib/fecha.js'
import { Campo, Cargando, ErrorCaja, Kpi, Modal, Vacio } from '../../components/ui.jsx'
import { AVISO, PRIORIDAD, TIPOS_NOVEDAD } from '../../components/Novedades.jsx'
import { TipoBadge } from '../../components/Existencias.jsx'
import { FiltroTipo } from '../../components/FiltroTipo.jsx'

const ESTADOS = {
  ABIERTO: { texto: 'Abierto', Icono: CircleDot, clase: 'border-blue-600 bg-blue-50 text-blue-900' },
  EN_PROCESO: { texto: 'En proceso', Icono: Hourglass, clase: 'border-amber-600 bg-amber-50 text-amber-900' },
  RESUELTO: { texto: 'Resuelto', Icono: CircleCheck, clase: 'border-emerald-600 bg-emerald-50 text-emerald-800' },
}
const OPCIONES_ESTADO = [
  { valor: 'todos', texto: 'Todos', Icono: BellRing },
  { valor: 'ABIERTO', texto: 'Abiertos', Icono: CircleDot },
  { valor: 'EN_PROCESO', texto: 'En proceso', Icono: Hourglass },
  { valor: 'RESUELTO', texto: 'Resueltos', Icono: CircleCheck },
]
const GRUPO_AVISO = {
  instante: ['ENVIADA_IA', 'ENVIADA_PLANTILLA'],
  resumen: ['PENDIENTE_RESUMEN', 'ENVIADA_RESUMEN'],
  pendiente: ['PENDIENTE'],
  error: ['FALLIDA', 'SIN_CONFIGURAR'],
}
const CADA_MS = 5000

export function EstadoReporte({ estado }) {
  const e = ESTADOS[estado] ?? ESTADOS.ABIERTO
  return <span className={`inline-flex items-center gap-1 rounded-full border px-2 py-0.5 text-[11px] font-extrabold ${e.clase}`}><e.Icono className="h-3 w-3" aria-hidden="true" /> {e.texto}</span>
}

/**
 * Vista «Reportes»: todos los reportes de novedades de la sede, actualizados en tiempo real (se revisa cada 5 s una
 * huella liviana y solo se recarga la lista cuando cambia), con filtros y seguimiento (Abierto → En proceso → Resuelto
 * con comentarios). Política de correo: ALTA al instante; MEDIA y BAJA en el resumen diario.
 */
export default function Reportes({ cargar, version, seguimiento, seguir, reenviar, enviarResumen }) {
  const { cerrar } = useAuth()
  const { sede } = useSede()
  const toast = useToast()
  const toastRef = useRef(toast)
  toastRef.current = toast
  const [lista, setLista] = useState(null)
  const [error, setError] = useState('')
  const [ultima, setUltima] = useState(null)
  const [nuevos, setNuevos] = useState(() => new Set())
  const conocidos = useRef(null)
  const huella = useRef(null)
  const [estado, setEstado] = useState('todos')
  const [prioridad, setPrioridad] = useState('todas')
  const [tipo, setTipo] = useState('todos')
  const [aviso, setAviso] = useState('todos')
  const [q, setQ] = useState('')
  const [abierto, setAbierto] = useState(null)
  const [enviandoResumen, setEnviandoResumen] = useState(false)

  const traer = useCallback(async () => {
    try {
      // La huella se toma ANTES de la lista: cualquier cambio posterior se detecta en la siguiente revisión
      if (version) { try { huella.current = (await version()).version } catch { /* se reintenta luego */ } }
      const d = await cargar()
      if (conocidos.current) {
        const llegaron = d.filter((x) => !conocidos.current.has(x.id))
        if (llegaron.length) {
          setNuevos((prev) => new Set([...prev, ...llegaron.map((x) => x.id)]))
          const alta = llegaron.find((x) => x.prioridad === 'ALTA')
          toastRef.current.info(llegaron.length === 1 ? 'Nuevo reporte' : `${llegaron.length} reportes nuevos`,
            llegaron.map((x) => `${x.placa} · ${x.tipoTexto}`).join(' · ') + (alta ? ' (prioridad ALTA)' : ''))
        }
      }
      conocidos.current = new Set(d.map((x) => x.id))
      setLista(d); setError(''); setUltima(new Date())
      setAbierto((a) => (a ? d.find((x) => x.id === a.id) ?? a : a))
    } catch (e) {
      if (e.status === 401) cerrar('expirada'); else setError(e.message)
    }
  }, [cargar, version, cerrar])

  // Tiempo real: cada 5 s se consulta la huella; si cambió (reporte nuevo, aviso enviado o seguimiento), se recarga
  useEffect(() => {
    let vivo = true
    traer()
    const revisar = async () => {
      if (!vivo || document.hidden) return
      if (!version) { traer(); return }
      try {
        const v = await version()
        if (v.version !== huella.current) await traer()
        setUltima(new Date())
      } catch { /* el banner de conexión ya informa */ }
    }
    const t = setInterval(revisar, CADA_MS)
    document.addEventListener('visibilitychange', revisar)
    window.addEventListener('labcontrol:reintentar', traer)
    return () => { vivo = false; clearInterval(t); document.removeEventListener('visibilitychange', revisar); window.removeEventListener('labcontrol:reintentar', traer) }
  }, [traer, version])

  const filtrados = useMemo(() => {
    const nq = norm(q)
    return (lista ?? []).filter((n) => (estado === 'todos' || n.estado === estado)
      && (prioridad === 'todas' || (prioridad === 'SIN' ? !n.prioridad : n.prioridad === prioridad))
      && (tipo === 'todos' || n.tipo === tipo)
      && (aviso === 'todos' || GRUPO_AVISO[aviso].includes(n.notificacion))
      && (!nq || norm(`${n.placa} ${n.equipoNombre} ${n.descripcion} ${n.reportadoPor} ${n.laboratorio} #${n.id}`).includes(nq)))
  }, [lista, estado, prioridad, tipo, aviso, q])

  const c = (f) => (lista ?? []).filter(f).length
  const conteo = { todos: c(() => true), ABIERTO: c((n) => n.estado === 'ABIERTO'), EN_PROCESO: c((n) => n.estado === 'EN_PROCESO'), RESUELTO: c((n) => n.estado === 'RESUELTO') }

  const resumenAhora = async () => {
    setEnviandoResumen(true)
    try {
      const r = await enviarResumen()
      if (r.enviado) toast.exito('Resumen diario enviado', `${r.reportes} reporte(s) y ${r.alertasInventario} alerta(s) de inventario → ${r.destinatario}`)
      else toast.info('Resumen no enviado', r.detalle)
      traer()
    } catch (e) { toast.error('No se pudo enviar el resumen', e.message) } finally { setEnviandoResumen(false) }
  }

  return (
    <div className="space-y-5">
      <div className="flex flex-col gap-3 lg:flex-row lg:items-end lg:justify-between">
        <div>
          <p className="text-xs font-bold uppercase tracking-widest text-usco">Reportes · {sede?.nombre}</p>
          <h1 className="text-2xl font-extrabold sm:text-3xl">Reportes de novedades</h1>
          <p className="text-sm text-pizarra">Todos los reportes de la sede en tiempo real. Prioridad <strong>alta</strong>: correo al instante. Media y baja: en el resumen diario (cada 24 horas).</p>
        </div>
        <div className="flex flex-wrap items-center gap-2">
          <span className="inline-flex items-center gap-1.5 rounded-full border border-emerald-600 bg-emerald-50 px-3 py-1 text-xs font-bold text-emerald-800" role="status">
            <Radio className="h-3.5 w-3.5 pulso" aria-hidden="true" /> En vivo{ultima ? ` · ${ultima.toLocaleTimeString('es-CO', { hour: '2-digit', minute: '2-digit', second: '2-digit' })}` : ''}
          </span>
          <button onClick={traer} className="btn-outline btn-sm" aria-label="Actualizar reportes"><RefreshCw className="h-4 w-4" aria-hidden="true" /></button>
          {enviarResumen && <button onClick={resumenAhora} disabled={enviandoResumen} className="btn-outline btn-sm"><MailCheck className="h-4 w-4" aria-hidden="true" /> {enviandoResumen ? 'Enviando…' : 'Enviar resumen diario ahora'}</button>}
        </div>
      </div>

      {lista && (
        <div className="grid grid-cols-2 gap-3 lg:grid-cols-4">
          <Kpi etiqueta="Reportes" valor={lista.length} detalle={`${c((n) => nuevos.has(n.id))} nuevos en esta sesión`} icono={BellRing} />
          <Kpi etiqueta="Alta sin resolver" valor={c((n) => n.prioridad === 'ALTA' && n.estado !== 'RESUELTO')} icono={Siren} tono="text-alerta-borde" fondo="bg-alerta-fondo" />
          <Kpi etiqueta="En proceso" valor={conteo.EN_PROCESO} icono={Hourglass} tono="text-amber-700" fondo="bg-amber-50" />
          <Kpi etiqueta="Para el resumen diario" valor={c((n) => n.notificacion === 'PENDIENTE_RESUMEN')} icono={MailCheck} tono="text-salida" fondo="bg-blue-50" />
        </div>
      )}

      <FiltroTipo valor={estado} onCambio={setEstado} conteo={conteo} opciones={OPCIONES_ESTADO} etiqueta="Filtrar por estado del seguimiento" />

      <section className="card grid gap-3 p-4 md:grid-cols-[1fr_170px_200px_210px]" aria-label="Filtros de reportes">
        <div className="relative"><label htmlFor="r-q" className="sr-only">Buscar</label><Search className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-pizarra" aria-hidden="true" />
          <input id="r-q" className="input pl-9" placeholder="Placa, equipo, descripción, quién reportó o #número" value={q} onChange={(e) => setQ(e.target.value)} /></div>
        <div><label htmlFor="r-p" className="sr-only">Prioridad</label>
          <select id="r-p" className="input" value={prioridad} onChange={(e) => setPrioridad(e.target.value)}>
            <option value="todas">Toda prioridad</option><option value="ALTA">Alta</option><option value="MEDIA">Media</option><option value="BAJA">Baja</option><option value="SIN">Sin clasificar</option>
          </select></div>
        <div><label htmlFor="r-t" className="sr-only">Tipo de novedad</label>
          <select id="r-t" className="input" value={tipo} onChange={(e) => setTipo(e.target.value)}>
            <option value="todos">Todo tipo de novedad</option>{TIPOS_NOVEDAD.map((t) => <option key={t.valor} value={t.valor}>{t.texto}</option>)}
          </select></div>
        <div><label htmlFor="r-a" className="sr-only">Aviso por correo</label>
          <select id="r-a" className="input" value={aviso} onChange={(e) => setAviso(e.target.value)}>
            <option value="todos">Todo aviso</option><option value="instante">Enviado al instante</option><option value="resumen">Resumen diario</option><option value="pendiente">Analizándose</option><option value="error">Con error / sin correo</option>
          </select></div>
      </section>

      <ErrorCaja mensaje={error} />
      {!lista && !error && <Cargando texto="Cargando reportes…" />}
      {lista && lista.length === 0 && <Vacio icono={BellRing} titulo="Aún no hay reportes" detalle="Cuando un auxiliar reporte una novedad aparecerá aquí al instante." />}
      {lista && lista.length > 0 && filtrados.length === 0 && <Vacio titulo="Ningún reporte con esos filtros" detalle="Cambia el estado, la prioridad o la búsqueda." />}

      {filtrados.length > 0 && (
        <ul className="card divide-y divide-slate-100 overflow-hidden" aria-live="polite">
          {filtrados.map((n) => {
            const a = AVISO[n.notificacion] ?? AVISO.PENDIENTE
            const Ic = a.icono
            const nuevo = nuevos.has(n.id)
            return (
              <li key={n.id} className={nuevo ? 'resaltar' : ''}>
                <button type="button" onClick={() => setAbierto(n)} className="flex w-full flex-col gap-2 px-4 py-3 text-left hover:bg-slate-50 sm:flex-row sm:items-center sm:justify-between">
                  <span className="min-w-0">
                    <span className="flex flex-wrap items-center gap-1.5">
                      <span className="font-mono text-xs font-bold text-pizarra">#{n.id}</span>
                      {nuevo && <span className="rounded-full bg-oro px-2 py-0.5 text-[11px] font-extrabold text-white">Nuevo</span>}
                      {n.prioridad ? <span className={`rounded-full border px-2 py-0.5 text-[11px] font-extrabold ${PRIORIDAD[n.prioridad]}`}>{n.prioridad}</span>
                        : <span className="rounded-full border border-slate-300 px-2 py-0.5 text-[11px] font-bold text-pizarra">Clasificando…</span>}
                      <EstadoReporte estado={n.estado} />
                      <span className="placa">{n.placa}</span>
                      <span className="text-sm font-bold text-slate-900">{n.tipoTexto}</span>
                    </span>
                    <span className="mt-0.5 block truncate text-sm text-slate-700">{n.equipoNombre} · {n.laboratorio}</span>
                    <span className="block truncate text-xs text-pizarra">{n.descripcion}</span>
                  </span>
                  <span className="flex shrink-0 flex-row flex-wrap items-center gap-x-3 gap-y-1 sm:flex-col sm:items-end">
                    <span className={`inline-flex items-center gap-1 text-xs font-bold ${a.clase}`}><Ic className={`h-3.5 w-3.5 ${n.notificacion === 'PENDIENTE' ? 'animate-pulse' : ''}`} aria-hidden="true" /> {a.texto}</span>
                    <span className="text-xs text-pizarra">{n.reportadoPor} · {fmtFechaHora(n.creadoEn)}</span>
                  </span>
                </button>
              </li>
            )
          })}
        </ul>
      )}

      <DetalleReporte reporte={abierto} onCerrar={() => setAbierto(null)} seguimiento={seguimiento} seguir={seguir} reenviar={reenviar}
        onCambio={(r) => { setLista((l) => l?.map((x) => (x.id === r.id ? r : x))); setAbierto(r); traer() }} />
    </div>
  )
}

/** Detalle del reporte: datos, análisis de la IA, resultado del aviso y seguimiento con comentarios. */
function DetalleReporte({ reporte: n, onCerrar, seguimiento, seguir, reenviar, onCambio }) {
  const toast = useToast()
  const [historia, setHistoria] = useState(null)
  const [nuevoEstado, setNuevoEstado] = useState('')
  const [comentario, setComentario] = useState('')
  const [err, setErr] = useState('')
  const [enviando, setEnviando] = useState(false)
  const id = n?.id

  const cargarHistoria = useCallback(async () => {
    if (!id) return
    try { setHistoria(await seguimiento(id)) } catch (e) { setErr(e.message) }
  }, [id, seguimiento])
  useEffect(() => {
    setHistoria(null); setComentario(''); setErr('')
    setNuevoEstado(n?.estado === 'ABIERTO' ? 'EN_PROCESO' : n?.estado === 'EN_PROCESO' ? 'RESUELTO' : n?.estado ?? '')
    cargarHistoria()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [id])

  const guardar = async (e) => {
    e.preventDefault()
    if (comentario.trim().length < 3) { setErr('Escribe un comentario para el seguimiento (mínimo 3 caracteres)'); return }
    setEnviando(true); setErr('')
    try {
      const r = await seguir(n.id, { estado: nuevoEstado || null, comentario: comentario.trim() })
      toast.exito('Seguimiento guardado', `Reporte #${r.id} · ${r.estadoTexto}`)
      setComentario(''); onCambio(r); cargarHistoria()
    } catch (er) { setErr(er.message) } finally { setEnviando(false) }
  }

  const volverAEnviar = async (ahora) => {
    try {
      const r = await reenviar(n.id, ahora)
      toast.exito(ahora ? 'Enviando correo ahora' : 'Reenviando aviso', `${n.placa} · ${n.tipoTexto}`)
      onCambio(r)
    } catch (e) { toast.error('No se pudo enviar', e.message) }
  }

  const a = n ? AVISO[n.notificacion] ?? AVISO.PENDIENTE : null
  return (
    <Modal abierto={!!n} onCerrar={onCerrar} ancho="max-w-2xl" titulo={n ? `Reporte #${n.id} · ${n.tipoTexto}` : ''} subtitulo={n ? `${n.placa} · ${n.equipoNombre} · ${n.laboratorio}` : undefined}
      acento={n?.prioridad === 'ALTA' ? 'bg-alerta-borde' : 'bg-usco'}>
      {n && (
        <div className="space-y-4 text-sm">
          <div className="flex flex-wrap items-center gap-2">
            {n.prioridad && <span className={`rounded-full border px-2 py-0.5 text-[11px] font-extrabold ${PRIORIDAD[n.prioridad]}`}>Prioridad {n.prioridad}</span>}
            <EstadoReporte estado={n.estado} />
            <TipoBadge tipo={n.equipoTipo} />
            <span className="text-xs text-pizarra">Reportó {n.reportadoPor} · {fmtFechaHora(n.creadoEn)}</span>
          </div>
          <p className="rounded-lg bg-slate-50 p-3 text-slate-800">{n.descripcion}</p>
          {n.enMantenimiento && <p className="text-xs font-semibold text-amber-800">El equipo se envió a mantenimiento al reportar.</p>}
          {(n.resumenIa || n.accionSugerida) && (
            <div className="space-y-1 rounded-lg border border-slate-200 p-3">
              {n.resumenIa && <p><span className="font-bold">Resumen de la IA:</span> {n.resumenIa}</p>}
              {n.accionSugerida && <p><span className="font-bold">Acción sugerida:</span> {n.accionSugerida}</p>}
            </div>
          )}
          <div className="rounded-lg border border-slate-200 p-3">
            <p className={`flex items-center gap-1.5 font-bold ${a.clase}`}><a.icono className="h-4 w-4" aria-hidden="true" /> {a.texto}</p>
            {n.asunto && <p className="mt-1 text-xs text-pizarra">Asunto: «{n.asunto}»{n.destinatario ? ` → ${n.destinatario}` : ''}</p>}
            {n.notificacionDetalle && <p className="text-xs text-pizarra">{n.notificacionDetalle}{n.notificadoEn ? ` · ${fmtFechaHora(n.notificadoEn)}` : ''}</p>}
            {reenviar && n.notificacion !== 'PENDIENTE' && (
              <div className="mt-2 flex flex-wrap gap-2">
                <button className="btn-outline btn-sm" onClick={() => volverAEnviar(false)}><RefreshCw className="h-4 w-4" aria-hidden="true" /> Volver a analizar y avisar</button>
                {n.prioridad !== 'ALTA' && <button className="btn-outline btn-sm" onClick={() => volverAEnviar(true)}><Send className="h-4 w-4" aria-hidden="true" /> Enviar correo ahora</button>}
              </div>
            )}
          </div>

          <section aria-labelledby="h-seg">
            <h3 id="h-seg" className="mb-2 font-extrabold">Seguimiento</h3>
            {!historia && <p className="text-pizarra">Cargando…</p>}
            {historia && historia.length === 0 && <p className="text-pizarra">Sin seguimiento todavía. Agrega el primer comentario.</p>}
            {historia && historia.length > 0 && (
              <ol className="relative space-y-3 border-l-2 border-slate-200 pl-4">
                {historia.map((h) => (
                  <li key={h.id} className="relative">
                    <span className="absolute -left-[23px] top-1 h-3 w-3 rounded-full border-2 border-white bg-usco" aria-hidden="true" />
                    <p className="flex flex-wrap items-center gap-1.5 text-xs text-pizarra"><strong className="text-slate-800">{h.autor}</strong> · {fmtFechaHora(h.fecha)}
                      {h.estadoAnterior !== h.estadoNuevo && <> · <EstadoReporte estado={h.estadoAnterior} /> → <EstadoReporte estado={h.estadoNuevo} /></>}
                    </p>
                    <p className="text-slate-800">{h.comentario}</p>
                  </li>
                ))}
              </ol>
            )}
            <form onSubmit={guardar} noValidate className="mt-3 space-y-2 rounded-lg bg-slate-50 p-3">
              <div className="grid gap-2 sm:grid-cols-[180px_1fr]">
                <Campo etiqueta="Estado" htmlFor="seg-estado">
                  <select id="seg-estado" className="input" value={nuevoEstado} onChange={(e) => setNuevoEstado(e.target.value)}>
                    {Object.entries(ESTADOS).map(([k, v]) => <option key={k} value={k}>{v.texto}</option>)}
                  </select>
                </Campo>
                <Campo etiqueta="Comentario" htmlFor="seg-com" error={err}>
                  <textarea id="seg-com" rows={2} className={`input ${err ? 'input-error' : ''}`} value={comentario} onChange={(e) => setComentario(e.target.value)} maxLength={600}
                    placeholder="Ej. Se solicitó cotización del repuesto al proveedor" />
                </Campo>
              </div>
              <button type="submit" className="btn-primary btn-sm" disabled={enviando}><MessageSquarePlus className="h-4 w-4" aria-hidden="true" /> {enviando ? 'Guardando…' : 'Guardar seguimiento'}</button>
            </form>
          </section>
        </div>
      )}
    </Modal>
  )
}
