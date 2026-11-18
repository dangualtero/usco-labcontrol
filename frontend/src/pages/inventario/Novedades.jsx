import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import { Link } from 'react-router-dom'
import {
  BellPlus, BellRing, CircleCheck, CircleX, ClipboardCheck, Hourglass, ListFilter, MessageSquarePlus, Radio, RefreshCw, Search, Send,
} from 'lucide-react'
import { api } from '../../api/index.js'
import { useAuth } from '../../context/AuthContext.jsx'
import { useSede } from '../../context/SedeContext.jsx'
import { useToast } from '../../context/ToastContext.jsx'
import { fmtCantidad, norm, puede } from '../../lib/constantes.js'
import { fmtFechaHora } from '../../lib/fecha.js'
import { Campo, Cargando, ErrorCaja, Kpi, Modal, Vacio } from '../../components/ui.jsx'
import { AVISO, ReportarNovedadModal, TIPOS_NOVEDAD } from '../../components/Novedades.jsx'
import { TipoBadge } from '../../components/Existencias.jsx'
import { FiltroTipo } from '../../components/FiltroTipo.jsx'
import { ListaArchivos } from '../../components/Archivos.jsx'
import { Encabezado, ESTADOS_NOVEDAD, EstadoNovedad, Prioridad, Validacion } from '../../components/Estados.jsx'

const OPCIONES = [
  { valor: 'PENDIENTE', texto: 'Por validar', Icono: Hourglass },
  { valor: 'VALIDADA', texto: 'Validadas', Icono: ClipboardCheck },
  { valor: 'RECHAZADA', texto: 'Rechazadas', Icono: CircleX },
  { valor: 'todos', texto: 'Todas', Icono: BellRing },
]
const CADA_MS = 5000

/**
 * Novedades del inventario. El monitor hace el registro preliminar (con fotografías y soportes); el coordinador lo
 * valida o lo rechaza. Al validarse se aplica al inventario y se avisa por correo (n8n). Se actualiza en tiempo real.
 */
export default function Novedades() {
  const { usuario, cerrar } = useAuth()
  const { sede } = useSede()
  const toast = useToast()
  const toastRef = useRef(toast)
  toastRef.current = toast
  const valida = puede(usuario, 'NOVEDAD_VALIDAR')
  const reporta = puede(usuario, 'NOVEDAD_REPORTAR')
  const [lista, setLista] = useState(null)
  const [error, setError] = useState('')
  const [ultima, setUltima] = useState(null)
  const [nuevos, setNuevos] = useState(() => new Set())
  const conocidos = useRef(null)
  const huella = useRef(null)
  const [validacion, setValidacion] = useState(valida ? 'PENDIENTE' : 'todos')
  const [estado, setEstado] = useState('todos')
  const [tipo, setTipo] = useState('todos')
  const [q, setQ] = useState('')
  const [abierta, setAbierta] = useState(null)
  const [elegir, setElegir] = useState(false)
  const [reportar, setReportar] = useState(null)

  const traer = useCallback(async () => {
    try {
      try { huella.current = (await api.novedadesVersion()).version } catch { /* se reintenta luego */ }
      const d = await api.novedades()
      if (conocidos.current) {
        const llegaron = d.filter((x) => !conocidos.current.has(x.id))
        if (llegaron.length) {
          setNuevos((prev) => new Set([...prev, ...llegaron.map((x) => x.id)]))
          toastRef.current.info(llegaron.length === 1 ? 'Nueva novedad' : `${llegaron.length} novedades nuevas`, llegaron.map((x) => `${x.placa} · ${x.tipoTexto}`).join(' · '))
        }
      }
      conocidos.current = new Set(d.map((x) => x.id))
      setLista(d); setError(''); setUltima(new Date())
      setAbierta((a) => (a ? d.find((x) => x.id === a.id) ?? a : a))
    } catch (e) {
      if (e.status === 401) cerrar('expirada'); else setError(e.message)
    }
  }, [cerrar])

  useEffect(() => {
    let vivo = true
    traer()
    const revisar = async () => {
      if (!vivo || document.hidden) return
      try {
        const v = await api.novedadesVersion()
        if (v.version !== huella.current) await traer()
        setUltima(new Date())
      } catch { /* el banner de conexión ya informa */ }
    }
    const t = setInterval(revisar, CADA_MS)
    document.addEventListener('visibilitychange', revisar)
    window.addEventListener('labcontrol:reintentar', traer)
    return () => { vivo = false; clearInterval(t); document.removeEventListener('visibilitychange', revisar); window.removeEventListener('labcontrol:reintentar', traer) }
  }, [traer])

  const filtradas = useMemo(() => {
    const nq = norm(q)
    return (lista ?? []).filter((n) => (validacion === 'todos' || n.validacion === validacion)
      && (estado === 'todos' || (n.validacion === 'VALIDADA' && n.estado === estado))
      && (tipo === 'todos' || n.tipo === tipo)
      && (!nq || norm(`${n.placa} ${n.equipoNombre} ${n.descripcion} ${n.reportadoPor} ${n.laboratorio} #${n.id}`).includes(nq)))
  }, [lista, validacion, estado, tipo, q])
  const c = (f) => (lista ?? []).filter(f).length
  const conteo = { PENDIENTE: c((n) => n.validacion === 'PENDIENTE'), VALIDADA: c((n) => n.validacion === 'VALIDADA'), RECHAZADA: c((n) => n.validacion === 'RECHAZADA'), todos: c(() => true) }

  return (
    <div className="space-y-5">
      <Encabezado modulo={`Inventarios y mantenimientos · ${sede?.nombre}`} titulo="Novedades"
        detalle={valida ? 'Valida los registros preliminares de los monitores: al validarlos se aplican al inventario y se avisa por correo.' : 'Reporta fallas, daños, pérdidas, existencias y consumos con fotografías. El coordinador los valida.'}>
        <span className="inline-flex items-center gap-1.5 self-center rounded-full border border-emerald-600 bg-emerald-50 px-3 py-1 text-xs font-bold text-emerald-800" role="status">
          <Radio className="h-3.5 w-3.5 pulso" aria-hidden="true" /> En vivo{ultima ? ` · ${ultima.toLocaleTimeString('es-CO', { hour: '2-digit', minute: '2-digit', second: '2-digit' })}` : ''}
        </span>
        <button onClick={traer} className="btn-outline" aria-label="Actualizar novedades"><RefreshCw className="h-4 w-4" aria-hidden="true" /></button>
        {reporta && <button onClick={() => setElegir(true)} className="btn-primary"><BellPlus className="h-4 w-4" aria-hidden="true" /> Registrar novedad</button>}
      </Encabezado>

      {lista && (
        <div className="grid grid-cols-2 gap-3 lg:grid-cols-4">
          <Kpi etiqueta="Por validar" valor={conteo.PENDIENTE} icono={Hourglass} tono="text-amber-700" fondo="bg-amber-50" />
          <Kpi etiqueta="Validadas abiertas" valor={c((n) => n.validacion === 'VALIDADA' && n.estado !== 'RESUELTO')} icono={ClipboardCheck} />
          <Kpi etiqueta="Prioridad alta sin resolver" valor={c((n) => n.prioridad === 'ALTA' && n.estado !== 'RESUELTO')} icono={BellRing} tono="text-alerta-borde" fondo="bg-alerta-fondo" />
          <Kpi etiqueta="Resueltas" valor={c((n) => n.validacion === 'VALIDADA' && n.estado === 'RESUELTO')} icono={CircleCheck} tono="text-entrada" fondo="bg-emerald-50" />
        </div>
      )}

      <FiltroTipo valor={validacion} onCambio={setValidacion} conteo={conteo} opciones={OPCIONES} etiqueta="Filtrar por validación" />

      <section className="card grid gap-3 p-4 md:grid-cols-[1fr_200px_220px]" aria-label="Filtros de novedades">
        <div className="relative"><label htmlFor="nv-q" className="sr-only">Buscar</label><Search className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-pizarra" aria-hidden="true" />
          <input id="nv-q" className="input pl-9" placeholder="Placa, elemento, descripción, quién reportó o #número" value={q} onChange={(e) => setQ(e.target.value)} /></div>
        <div><label htmlFor="nv-e" className="sr-only">Seguimiento</label>
          <select id="nv-e" className="input" value={estado} onChange={(e) => setEstado(e.target.value)}>
            <option value="todos">Todo seguimiento</option>{Object.entries(ESTADOS_NOVEDAD).map(([k, v]) => <option key={k} value={k}>{v.texto}</option>)}
          </select></div>
        <div><label htmlFor="nv-t" className="sr-only">Tipo</label>
          <select id="nv-t" className="input" value={tipo} onChange={(e) => setTipo(e.target.value)}>
            <option value="todos">Todo tipo</option>{TIPOS_NOVEDAD.map((t) => <option key={t.valor} value={t.valor}>{t.texto}</option>)}
          </select></div>
      </section>

      <ErrorCaja mensaje={error} />
      {!lista && !error && <Cargando texto="Cargando novedades…" />}
      {lista && filtradas.length === 0 && <Vacio icono={ListFilter} titulo={validacion === 'PENDIENTE' ? 'No hay novedades por validar' : 'Ninguna novedad con esos filtros'} />}

      {filtradas.length > 0 && (
        <ul className="card divide-y divide-slate-100 overflow-hidden" aria-live="polite">
          {filtradas.map((n) => {
            const a = AVISO[n.notificacion] ?? AVISO.EN_ESPERA
            return (
              <li key={n.id} className={nuevos.has(n.id) ? 'resaltar' : ''}>
                <button type="button" onClick={() => setAbierta(n)} className="flex w-full flex-col gap-2 px-4 py-3 text-left hover:bg-slate-50 sm:flex-row sm:items-center sm:justify-between">
                  <span className="min-w-0">
                    <span className="flex flex-wrap items-center gap-1.5">
                      <span className="font-mono text-xs font-bold text-pizarra">#{n.id}</span>
                      <Validacion estado={n.validacion} />
                      {n.validacion === 'VALIDADA' && <EstadoNovedad estado={n.estado} />}
                      <Prioridad valor={n.prioridad} />
                      <span className="placa">{n.placa}</span>
                      <span className="text-sm font-bold text-slate-900">{n.tipoTexto}{n.cantidad != null ? ` · ${fmtCantidad(n.cantidad, n.unidad)}` : ''}</span>
                    </span>
                    <span className="mt-0.5 block truncate text-sm text-slate-700">{n.equipoNombre} · {n.laboratorio}</span>
                    <span className="block truncate text-xs text-pizarra">{n.descripcion}</span>
                  </span>
                  <span className="flex shrink-0 flex-row flex-wrap items-center gap-x-3 gap-y-1 sm:flex-col sm:items-end">
                    <span className={`inline-flex items-center gap-1 text-xs font-bold ${a.clase}`}><a.icono className="h-3.5 w-3.5" aria-hidden="true" /> {a.texto}</span>
                    <span className="text-xs text-pizarra">{n.reportadoPor} · {fmtFechaHora(n.creadoEn)}{n.archivos.length ? ` · 📎 ${n.archivos.length}` : ''}</span>
                  </span>
                </button>
              </li>
            )
          })}
        </ul>
      )}

      <DetalleNovedad n={abierta} valida={valida} onCerrar={() => setAbierta(null)} onCambio={(r) => { setLista((l) => l?.map((x) => (x.id === r.id ? r : x))); setAbierta(r); traer() }} />
      <ElegirElemento abierto={elegir} onCerrar={() => setElegir(false)} onElegir={(e) => { setElegir(false); setReportar(e) }} />
      <ReportarNovedadModal equipo={reportar} onCerrar={() => setReportar(null)} onHecho={traer} />
    </div>
  )
}

function ElegirElemento({ abierto, onCerrar, onElegir }) {
  const [lista, setLista] = useState(null)
  const [q, setQ] = useState('')
  useEffect(() => {
    if (!abierto) return
    setQ(''); api.inventario().then((l) => setLista(l.filter((x) => x.activo).map((x) => x.equipo))).catch(() => setLista([]))
  }, [abierto])
  const nq = norm(q)
  const filtrados = (lista ?? []).filter((e) => !nq || norm(`${e.placa} ${e.nombre} ${e.laboratorio}`).includes(nq)).slice(0, 60)
  return (
    <Modal abierto={abierto} onCerrar={onCerrar} titulo="¿Sobre qué elemento?" subtitulo="Elige el equipo o insumo de tus laboratorios">
      <div className="space-y-3">
        <div className="relative"><label htmlFor="el-q" className="sr-only">Buscar</label><Search className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-pizarra" aria-hidden="true" />
          <input id="el-q" className="input pl-9" placeholder="Placa o nombre" value={q} onChange={(e) => setQ(e.target.value)} /></div>
        {!lista && <Cargando />}
        <ul className="max-h-[50vh] divide-y divide-slate-100 overflow-y-auto rounded-lg border border-slate-200">
          {filtrados.map((e) => (
            <li key={e.placa}><button type="button" onClick={() => onElegir(e)} className="flex w-full items-center justify-between gap-2 px-3 py-2.5 text-left text-sm hover:bg-slate-50">
              <span className="min-w-0"><span className="placa">{e.placa}</span> <strong>{e.nombre}</strong><span className="block text-xs text-pizarra">{e.laboratorio}</span></span>
              <TipoBadge tipo={e.tipo} />
            </button></li>
          ))}
          {lista && filtrados.length === 0 && <li className="p-4 text-center text-sm text-pizarra">Sin resultados</li>}
        </ul>
      </div>
    </Modal>
  )
}

/** Detalle: datos, fotografías y soportes, validación (coordinador), aviso por correo y seguimiento. */
function DetalleNovedad({ n, valida, onCerrar, onCambio }) {
  const toast = useToast()
  const { ruta } = useSede()
  const [historia, setHistoria] = useState(null)
  const [obs, setObs] = useState('')
  const [nuevoEstado, setNuevoEstado] = useState('')
  const [comentario, setComentario] = useState('')
  const [err, setErr] = useState('')
  const [enviando, setEnviando] = useState(false)
  const id = n?.id

  const cargarHistoria = useCallback(async () => {
    if (!id) return
    try { setHistoria(await api.seguimientoNovedad(id)) } catch (e) { setErr(e.message) }
  }, [id])
  useEffect(() => {
    setHistoria(null); setComentario(''); setObs(''); setErr('')
    setNuevoEstado(n?.estado === 'ABIERTO' ? 'EN_PROCESO' : n?.estado === 'EN_PROCESO' ? 'RESUELTO' : n?.estado ?? '')
    cargarHistoria()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [id])

  const decidir = async (validar) => {
    if (!validar && obs.trim().length < 5) { setErr('Indica el motivo del rechazo (mínimo 5 caracteres)'); return }
    setEnviando(true); setErr('')
    try {
      const r = validar ? await api.validarNovedad(n.id, obs.trim() || null) : await api.rechazarNovedad(n.id, obs.trim())
      toast.exito(validar ? 'Novedad validada' : 'Novedad rechazada', validar ? 'Se aplicó al inventario y se dispara el aviso.' : `#${r.id}`)
      onCambio(r); cargarHistoria()
    } catch (e) { setErr(e.message) } finally { setEnviando(false) }
  }
  const guardar = async (e) => {
    e.preventDefault()
    if (comentario.trim().length < 3) { setErr('Escribe un comentario (mínimo 3 caracteres)'); return }
    setEnviando(true); setErr('')
    try {
      const r = await api.seguirNovedad(n.id, { estado: valida && n.validacion === 'VALIDADA' ? nuevoEstado : null, comentario: comentario.trim() })
      toast.exito('Seguimiento guardado', `Novedad #${r.id}`)
      setComentario(''); onCambio(r); cargarHistoria()
    } catch (er) { setErr(er.message) } finally { setEnviando(false) }
  }
  const reenviar = async (ahora) => {
    try { onCambio(await api.reenviarNovedad(n.id, ahora)); toast.exito('Aviso en camino', `${n.placa} · ${n.tipoTexto}`) }
    catch (e) { toast.error('No se pudo enviar', e.message) }
  }

  const a = n ? AVISO[n.notificacion] ?? AVISO.EN_ESPERA : null
  return (
    <Modal abierto={!!n} onCerrar={onCerrar} ancho="max-w-2xl" titulo={n ? `Novedad #${n.id} · ${n.tipoTexto}` : ''} subtitulo={n ? `${n.placa} · ${n.equipoNombre} · ${n.laboratorio}` : undefined}
      acento={n?.validacion === 'PENDIENTE' ? 'bg-amber-500' : n?.prioridad === 'ALTA' ? 'bg-alerta-borde' : 'bg-usco'}>
      {n && (
        <div className="space-y-4 text-sm">
          <div className="flex flex-wrap items-center gap-2">
            <Validacion estado={n.validacion} />{n.validacion === 'VALIDADA' && <EstadoNovedad estado={n.estado} />}<Prioridad valor={n.prioridad} prefijo="Prioridad " />
            <TipoBadge tipo={n.equipoTipo} />
            <Link to={ruta(`/inventario/${n.placa}`)} className="text-xs font-bold text-usco hover:underline">Ver hoja de vida</Link>
          </div>
          <p className="text-xs text-pizarra">Reportó {n.reportadoPor} · {fmtFechaHora(n.creadoEn)}{n.validadoPor ? ` · ${n.validacion === 'RECHAZADA' ? 'Rechazó' : 'Validó'} ${n.validadoPor} · ${fmtFechaHora(n.validadoEn)}` : ''}</p>
          {n.cantidad != null && <p className="font-bold">{n.tipo === 'CONSUMO' ? 'Consumo' : 'Existencias contadas'}: {fmtCantidad(n.cantidad, n.unidad)}</p>}
          <p className="rounded-lg bg-slate-50 p-3 text-slate-800">{n.descripcion}</p>
          {n.enMantenimiento && <p className="text-xs font-semibold text-amber-800">Se solicitó mantenimiento: al validarse, el equipo pasa a mantenimiento y se crea el requerimiento correctivo.</p>}
          {n.observacionValidacion && <p className="rounded-lg border border-slate-200 p-3"><strong>Observación del coordinador:</strong> {n.observacionValidacion}</p>}
          <div><p className="mb-1 font-extrabold">Fotografías y soportes</p><ListaArchivos archivos={n.archivos} vacio="Sin archivos adjuntos" /></div>

          {valida && n.validacion === 'PENDIENTE' && (
            <section className="space-y-2 rounded-lg border-2 border-amber-500 bg-amber-50 p-3" aria-label="Validación">
              <p className="font-extrabold text-amber-900">Validación del coordinador</p>
              <Campo etiqueta="Observación (obligatoria para rechazar)" htmlFor="nv-obs">
                <textarea id="nv-obs" rows={2} className="input" value={obs} onChange={(e) => setObs(e.target.value)} maxLength={600} />
              </Campo>
              <div className="flex flex-wrap gap-2">
                <button className="btn-entrada btn-sm" disabled={enviando} onClick={() => decidir(true)}><CircleCheck className="h-4 w-4" aria-hidden="true" /> Validar y aplicar</button>
                <button className="btn-outline btn-sm" disabled={enviando} onClick={() => decidir(false)}><CircleX className="h-4 w-4" aria-hidden="true" /> Rechazar</button>
              </div>
            </section>
          )}

          {n.validacion === 'VALIDADA' && (
            <div className="rounded-lg border border-slate-200 p-3">
              <p className={`flex items-center gap-1.5 font-bold ${a.clase}`}><a.icono className="h-4 w-4" aria-hidden="true" /> {a.texto}</p>
              {n.resumenIa && <p className="mt-1"><strong>Resumen de la IA:</strong> {n.resumenIa}</p>}
              {n.accionSugerida && <p><strong>Acción sugerida:</strong> {n.accionSugerida}</p>}
              {n.asunto && <p className="mt-1 text-xs text-pizarra">Asunto: «{n.asunto}»{n.destinatario ? ` → ${n.destinatario}` : ''}</p>}
              {n.notificacionDetalle && <p className="text-xs text-pizarra">{n.notificacionDetalle}{n.notificadoEn ? ` · ${fmtFechaHora(n.notificadoEn)}` : ''}</p>}
              {valida && !['PENDIENTE', 'NO_APLICA'].includes(n.notificacion) && (
                <div className="mt-2 flex flex-wrap gap-2">
                  <button className="btn-outline btn-sm" onClick={() => reenviar(false)}><RefreshCw className="h-4 w-4" aria-hidden="true" /> Volver a analizar y avisar</button>
                  {n.prioridad !== 'ALTA' && <button className="btn-outline btn-sm" onClick={() => reenviar(true)}><Send className="h-4 w-4" aria-hidden="true" /> Enviar correo ahora</button>}
                </div>
              )}
            </div>
          )}

          <section aria-labelledby="h-seg">
            <h3 id="h-seg" className="mb-2 font-extrabold">Seguimiento</h3>
            {!historia && <p className="text-pizarra">Cargando…</p>}
            {historia && historia.length === 0 && <p className="text-pizarra">Sin comentarios todavía.</p>}
            {historia && historia.length > 0 && (
              <ol className="relative space-y-3 border-l-2 border-slate-200 pl-4">
                {historia.map((h) => (
                  <li key={h.id} className="relative">
                    <span className="absolute -left-[23px] top-1 h-3 w-3 rounded-full border-2 border-white bg-usco" aria-hidden="true" />
                    <p className="flex flex-wrap items-center gap-1.5 text-xs text-pizarra"><strong className="text-slate-800">{h.autor}</strong> · {fmtFechaHora(h.fecha)}
                      {h.estadoAnterior !== h.estadoNuevo && <> · <EstadoNovedad estado={h.estadoAnterior} /> → <EstadoNovedad estado={h.estadoNuevo} /></>}</p>
                    <p className="text-slate-800">{h.comentario}</p>
                  </li>
                ))}
              </ol>
            )}
            {n.validacion !== 'RECHAZADA' && (
              <form onSubmit={guardar} noValidate className="mt-3 space-y-2 rounded-lg bg-slate-50 p-3">
                <div className={`grid gap-2 ${valida && n.validacion === 'VALIDADA' ? 'sm:grid-cols-[180px_1fr]' : ''}`}>
                  {valida && n.validacion === 'VALIDADA' && (
                    <Campo etiqueta="Estado" htmlFor="seg-estado">
                      <select id="seg-estado" className="input" value={nuevoEstado} onChange={(e) => setNuevoEstado(e.target.value)}>
                        {Object.entries(ESTADOS_NOVEDAD).map(([k, v]) => <option key={k} value={k}>{v.texto}</option>)}
                      </select>
                    </Campo>
                  )}
                  <Campo etiqueta="Comentario" htmlFor="seg-com">
                    <textarea id="seg-com" rows={2} className="input" value={comentario} onChange={(e) => setComentario(e.target.value)} maxLength={600} placeholder="Ej. Se solicitó cotización del repuesto" />
                  </Campo>
                </div>
                <button type="submit" className="btn-primary btn-sm" disabled={enviando}><MessageSquarePlus className="h-4 w-4" aria-hidden="true" /> {enviando ? 'Guardando…' : 'Guardar seguimiento'}</button>
              </form>
            )}
          </section>
          <ErrorCaja mensaje={err} />
        </div>
      )}
    </Modal>
  )
}
