import { useEffect, useState } from 'react'
import { BellRing, Bot, CalendarClock, CircleAlert, CircleCheck, Clock, Mail, MailCheck, MailWarning, RefreshCw, Send } from 'lucide-react'
import { api } from '../api/index.js'
import { useToast } from '../context/ToastContext.jsx'
import { fmtFechaHora } from '../lib/fecha.js'
import { Campo, ErrorCaja, Modal } from './ui.jsx'

export const TIPOS_NOVEDAD = [
  { valor: 'DANADO', texto: 'Dañado' },
  { valor: 'FALLA', texto: 'Falla de funcionamiento' },
  { valor: 'AGOTADO', texto: 'Agotado / sin existencias' },
  { valor: 'PERDIDO', texto: 'Perdido o extraviado' },
  { valor: 'OTRO', texto: 'Otra novedad' },
]

export const PRIORIDAD = {
  ALTA: 'border-red-600 bg-red-50 text-red-900',
  MEDIA: 'border-amber-600 bg-amber-50 text-amber-900',
  BAJA: 'border-emerald-600 bg-emerald-50 text-emerald-900',
}

export const AVISO = {
  PENDIENTE: { icono: Clock, texto: 'Analizando y enviando…', clase: 'text-pizarra' },
  PENDIENTE_RESUMEN: { icono: CalendarClock, texto: 'Irá en el resumen diario', clase: 'text-blue-900' },
  ENVIADA_RESUMEN: { icono: MailCheck, texto: 'Enviado en el resumen diario', clase: 'text-emerald-800' },
  ENVIADA_IA: { icono: Bot, texto: 'Aviso enviado · redactado por IA', clase: 'text-emerald-800' },
  ENVIADA_PLANTILLA: { icono: Mail, texto: 'Aviso enviado con plantilla', clase: 'text-emerald-800' },
  SIN_CONFIGURAR: { icono: MailWarning, texto: 'Correo sin configurar', clase: 'text-amber-700' },
  FALLIDA: { icono: CircleAlert, texto: 'No se pudo enviar el aviso', clase: 'text-alerta-borde' },
}

/** Modal del auxiliar: reportar que un equipo se agotó, se dañó, se perdió o falla. */
export function ReportarNovedadModal({ equipo, onCerrar, onHecho }) {
  const toast = useToast()
  const [tipo, setTipo] = useState('DANADO')
  const [descripcion, setDescripcion] = useState('')
  const [mantenimiento, setMantenimiento] = useState(true)
  const [error, setError] = useState('')
  const [enviando, setEnviando] = useState(false)

  useEffect(() => {
    if (!equipo) return
    setTipo('DANADO'); setDescripcion(''); setError(''); setMantenimiento(equipo.estado === 'DISPONIBLE')
  }, [equipo])

  const enviar = async (e) => {
    e.preventDefault()
    if (descripcion.trim().length < 10) { setError('Describe qué pasó (mínimo 10 caracteres)'); return }
    setEnviando(true); setError('')
    try {
      await api.reportarNovedad(equipo.placa, { tipo, descripcion: descripcion.trim(), mantenimiento })
      toast.exito('Novedad reportada', 'Si es de prioridad alta se avisará de inmediato a la coordinación; si no, irá en el resumen diario.')
      onHecho?.(); onCerrar()
    } catch (err) { setError(err.message) } finally { setEnviando(false) }
  }

  return (
    <Modal abierto={!!equipo} onCerrar={onCerrar} titulo="Reportar novedad" subtitulo={equipo ? `${equipo.placa} · ${equipo.nombre}` : undefined} acento="bg-alerta-borde" ancho="max-w-lg"
      pie={<>
        <button type="button" className="btn-outline" onClick={onCerrar}>Cancelar</button>
        <button type="submit" form="form-novedad" className="btn-primary" disabled={enviando}><Send className="h-4 w-4" aria-hidden="true" /> {enviando ? 'Enviando…' : 'Reportar y avisar'}</button>
      </>}>
      {equipo && (
        <form id="form-novedad" onSubmit={enviar} noValidate className="space-y-4">
          <fieldset>
            <legend className="label">¿Qué pasó?</legend>
            <div className="grid gap-2 sm:grid-cols-2">
              {TIPOS_NOVEDAD.map((t) => (
                <label key={t.valor} className={`flex min-h-[44px] cursor-pointer items-center gap-2 rounded-lg border-2 px-3 text-sm font-semibold ${tipo === t.valor ? 'border-usco bg-usco-soft text-usco-darker' : 'border-slate-200 text-pizarra'}`}>
                  <input type="radio" name="tipo-novedad" value={t.valor} checked={tipo === t.valor} onChange={() => setTipo(t.valor)} className="accent-[#8F141B]" /> {t.texto}
                </label>
              ))}
            </div>
          </fieldset>
          <Campo etiqueta="Descripción" htmlFor="nv-desc" ayuda="Qué se observó, desde cuándo y cualquier dato útil para la coordinación">
            <textarea id="nv-desc" rows={4} className="input" value={descripcion} onChange={(e) => setDescripcion(e.target.value)} maxLength={1000}
              placeholder="Ej. Al encenderlo la pantalla no muestra imagen y huele a quemado." />
          </Campo>
          {equipo.estado === 'DISPONIBLE' && (
            <label className="flex cursor-pointer items-start gap-2.5 text-sm text-slate-700">
              <input type="checkbox" checked={mantenimiento} onChange={(e) => setMantenimiento(e.target.checked)} className="mt-0.5 h-4 w-4 accent-[#8F141B]" />
              <span><strong>Enviar a mantenimiento</strong>: el equipo deja de aparecer disponible para préstamo hasta que se revise.</span>
            </label>
          )}
          <p className="flex items-start gap-2 rounded-lg bg-slate-100 px-3 py-2.5 text-xs text-pizarra">
            <Bot className="mt-0.5 h-4 w-4 shrink-0 text-usco" aria-hidden="true" />
            Un agente de IA analiza el reporte y le asigna prioridad. Los de prioridad <strong>alta</strong> se envían de inmediato al correo de la coordinación; los de prioridad media y baja llegan en el resumen diario.
          </p>
          <ErrorCaja mensaje={error} />
        </form>
      )}
    </Modal>
  )
}

/** Lista de novedades de la sede con la prioridad y el resultado del aviso. */
export function NovedadesLista({ cargar, reenviar, refrescar = 0, titulo = 'Novedades reportadas' }) {
  const toast = useToast()
  const [datos, setDatos] = useState(null)
  const [error, setError] = useState('')
  const [abierta, setAbierta] = useState(null)

  const recargar = async () => {
    try { setDatos(await cargar()); setError('') } catch (e) { setError(e.message) }
  }
  useEffect(() => { recargar() /* eslint-disable-next-line react-hooks/exhaustive-deps */ }, [refrescar])
  // Mientras haya avisos en camino, se consulta cada 4 s para mostrar la prioridad que asignó la IA.
  const pendientes = (datos ?? []).some((n) => n.notificacion === 'PENDIENTE')
  useEffect(() => {
    const t = setInterval(recargar, pendientes ? 4000 : 30000)
    return () => clearInterval(t)
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [pendientes])

  const volverAEnviar = async (n) => {
    try { await reenviar(n.id); toast.exito('Reenviando aviso', `${n.placa} · ${n.tipoTexto}`); recargar() }
    catch (e) { toast.error('No se pudo reenviar', e.message) }
  }

  return (
    <section className="card overflow-hidden" aria-labelledby="t-novedades">
      <div className="flex items-center justify-between gap-2 border-b border-slate-200 px-4 py-3">
        <h2 id="t-novedades" className="flex items-center gap-2 font-extrabold"><BellRing className="h-4 w-4 text-usco" aria-hidden="true" /> {titulo} {datos && <span className="text-sm font-semibold text-pizarra">({datos.length})</span>}</h2>
        <button className="btn-outline btn-sm" onClick={recargar} aria-label="Actualizar novedades"><RefreshCw className="h-4 w-4" aria-hidden="true" /></button>
      </div>
      <ErrorCaja mensaje={error} className="m-4" />
      {datos && datos.length === 0 && <p className="px-4 py-6 text-center text-sm text-pizarra">No hay novedades reportadas en esta sede.</p>}
      {datos && datos.length > 0 && (
        <ul className="max-h-[420px] divide-y divide-slate-100 overflow-y-auto">
          {datos.map((n) => {
            const a = AVISO[n.notificacion] ?? AVISO.PENDIENTE
            const Ic = a.icono
            const ver = abierta === n.id
            return (
              <li key={n.id} className="px-4 py-3">
                <button type="button" className="flex w-full flex-wrap items-start justify-between gap-2 text-left" onClick={() => setAbierta(ver ? null : n.id)} aria-expanded={ver}>
                  <span className="min-w-0">
                    <span className="flex flex-wrap items-center gap-2">
                      {n.prioridad && <span className={`rounded-full border px-2 py-0.5 text-[11px] font-extrabold ${PRIORIDAD[n.prioridad]}`}>{n.prioridad}</span>}
                      <span className="placa">{n.placa}</span>
                      <span className="text-sm font-bold text-slate-900">{n.tipoTexto}</span>
                    </span>
                    <span className="mt-0.5 block truncate text-sm text-pizarra">{n.equipoNombre} · {n.reportadoPor} · {fmtFechaHora(n.creadoEn)}</span>
                  </span>
                  <span className={`inline-flex items-center gap-1 text-xs font-bold ${a.clase}`}>
                    <Ic className={`h-3.5 w-3.5 ${n.notificacion === 'PENDIENTE' ? 'animate-pulse' : ''}`} aria-hidden="true" /> {a.texto}
                  </span>
                </button>
                {ver && (
                  <div className="mt-3 space-y-2 rounded-lg bg-slate-50 p-3 text-sm aparecer">
                    <p className="text-slate-700"><span className="font-bold">Reporte:</span> {n.descripcion}</p>
                    {n.enMantenimiento && <p className="flex items-center gap-1.5 text-xs font-semibold text-amber-800"><CircleCheck className="h-3.5 w-3.5" aria-hidden="true" /> Se envió a mantenimiento al reportar</p>}
                    {n.resumenIa && <p className="text-slate-700"><span className="font-bold">Resumen de la IA:</span> {n.resumenIa}</p>}
                    {n.accionSugerida && <p className="text-slate-700"><span className="font-bold">Acción sugerida:</span> {n.accionSugerida}</p>}
                    {n.asunto && <p className="text-xs text-pizarra">Asunto del correo: «{n.asunto}»{n.destinatario ? ` → ${n.destinatario}` : ''}</p>}
                    {n.notificacionDetalle && <p className="text-xs text-pizarra">{n.notificacionDetalle}</p>}
                    {reenviar && n.notificacion !== 'PENDIENTE' && (
                      <button className="btn-outline btn-sm" onClick={() => volverAEnviar(n)}><Send className="h-4 w-4" aria-hidden="true" /> Volver a enviar el aviso</button>
                    )}
                  </div>
                )}
              </li>
            )
          })}
        </ul>
      )}
    </section>
  )
}
