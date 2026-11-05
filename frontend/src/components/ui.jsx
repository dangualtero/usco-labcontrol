import { useEffect, useId, useRef, useState } from 'react'
import {
  Activity, CircleCheck, Clock, Compass, CloudSun, FlaskConical, Flame, Gauge, Hammer, Leaf, Orbit,
  Scale, Sprout, TriangleAlert, Wrench, X, Zap, Boxes, SearchX, Loader2, ImageOff,
} from 'lucide-react'
import { fmtVencimiento } from '../lib/fecha.js'

/* ---------- Badges de estado: NUNCA dependen solo del color (PA-07): icono + texto + color ---------- */
const ESTADOS = {
  DISPONIBLE: { texto: 'Disponible', Icono: CircleCheck, clase: 'bg-emerald-50 text-emerald-800 border-emerald-600' },
  PRESTADO: { texto: 'Prestado', Icono: Clock, clase: 'bg-amber-50 text-amber-900 border-amber-600' },
  MANTENIMIENTO: { texto: 'En mantenimiento', Icono: Wrench, clase: 'bg-slate-100 text-slate-800 border-slate-500' },
}
export function EstadoBadge({ estado, className = '' }) {
  const e = ESTADOS[estado] ?? ESTADOS.MANTENIMIENTO
  return (
    <span className={`inline-flex items-center gap-1.5 rounded-full border px-2.5 py-1 text-xs font-bold ${e.clase} ${className}`}>
      <e.Icono className="h-3.5 w-3.5" aria-hidden="true" />
      {e.texto}
    </span>
  )
}

const ESTADOS_PRESTAMO = {
  RESERVADO: ['Reservado', 'bg-blue-50 text-blue-900 border-blue-600'],
  ACTIVO: ['En curso', 'bg-amber-50 text-amber-900 border-amber-600'],
  DEVUELTO: ['Devuelto', 'bg-emerald-50 text-emerald-800 border-emerald-600'],
  CANCELADO: ['Cancelado', 'bg-slate-100 text-slate-700 border-slate-400'],
  RECHAZADO: ['Rechazada', 'bg-alerta-fondo text-alerta-texto border-alerta-borde'],
}
export function PrestamoBadge({ estado, vencido }) {
  if (vencido) {
    return (
      <span className="inline-flex items-center gap-1.5 rounded-full border border-alerta-borde bg-alerta-fondo px-2.5 py-1 text-xs font-bold text-alerta-texto">
        <TriangleAlert className="h-3.5 w-3.5" aria-hidden="true" /> Vencido
      </span>
    )
  }
  const [t, c] = ESTADOS_PRESTAMO[estado] ?? ESTADOS_PRESTAMO.CANCELADO
  return <span className={`inline-flex items-center rounded-full border px-2.5 py-1 text-xs font-bold ${c}`}>{t}</span>
}

/* ---------- Ilustración por categoría (íconos de equipos de laboratorio) ---------- */
const ICONOS = {
  Osciloscopios: Activity, Multímetros: Gauge, 'Fuentes y generadores': Zap, Soldadura: Flame,
  Topografía: Compass, 'Análisis de fluidos': FlaskConical, Balanzas: Scale, Separación: Orbit,
  'Ensayos de materiales': Hammer, 'Medición de suelos': Sprout, Climatología: CloudSun, 'Análisis de cultivos': Leaf,
}
const TONOS = {
  Electrónica: 'from-[#8F141B] to-[#5C0D12]', Petróleos: 'from-[#334155] to-[#1E293B]',
  Civil: 'from-[#4D626C] to-[#334155]', Agrícola: 'from-[#423C16] to-[#2B2710]',
}
export function EquipoIcono({ categoria, laboratorio, className = 'h-24' }) {
  const Icono = ICONOS[categoria] ?? Boxes
  return (
    <div className={`relative flex items-center justify-center overflow-hidden bg-gradient-to-br ${TONOS[laboratorio] ?? TONOS.Electrónica} ${className}`} aria-hidden="true">
      <div className="absolute inset-0 opacity-[0.07]" style={{ backgroundImage: 'radial-gradient(#DFD4A6 1px, transparent 1px)', backgroundSize: '14px 14px' }} />
      <div className="relative flex h-14 w-14 items-center justify-center rounded-2xl border border-oro-claro/40 bg-white/10 backdrop-blur-sm">
        <Icono className="h-8 w-8 text-oro-claro" strokeWidth={1.6} />
      </div>
    </div>
  )
}

/** Fotografía real del equipo (object-cover, zoom sutil al pasar el cursor, badge de placa). Si no hay foto o falla la carga, muestra un marcador. */
export function EquipoFoto({ equipo, className = 'h-44', zoom = true, placa = true, children }) {
  const [fallo, setFallo] = useState(false)
  useEffect(() => { setFallo(false) }, [equipo.fotoUrl])
  const hay = equipo.fotoUrl && !fallo
  return (
    <div className={`relative overflow-hidden bg-slate-100 ${className}`}>
      {hay ? (
        <img src={equipo.fotoUrl} alt={`Fotografía de ${equipo.nombre}`} loading="lazy" onError={() => setFallo(true)}
          className={`h-full w-full object-cover ${zoom ? 'transition-transform duration-500 group-hover:scale-105' : ''}`} />
      ) : (
        <div className="relative h-full w-full">
          <EquipoIcono categoria={equipo.categoria} laboratorio={equipo.laboratorio} className="h-full w-full" />
          <span className="absolute bottom-2.5 left-1/2 inline-flex -translate-x-1/2 items-center gap-1 whitespace-nowrap rounded-full bg-black/45 px-2.5 py-1 text-[11px] font-semibold text-white backdrop-blur-sm"><ImageOff className="h-3 w-3" aria-hidden="true" /> Fotografía no disponible</span>
        </div>
      )}
      {placa && <span className="absolute left-2.5 top-2.5 rounded-md bg-white/95 px-2 py-1 font-mono text-[12.5px] font-bold text-slate-800 shadow ring-1 ring-black/5">{equipo.placa}</span>}
      {children}
    </div>
  )
}

/* ---------- Alerta de retraso (PA-03 / RF-04): caja roja suave, triángulo y texto descriptivo ---------- */
export function AlertaRetraso({ prestamos, ahora, titulo }) {
  if (!prestamos?.length) return null
  return (
    <div role="alert" className="rounded-xl border border-alerta-borde bg-alerta-fondo p-4 aparecer">
      <div className="flex items-start gap-3">
        <TriangleAlert className="mt-0.5 h-6 w-6 shrink-0 text-alerta-borde" aria-hidden="true" />
        <div className="min-w-0 flex-1">
          <p className="font-bold text-alerta-texto">
            {titulo ?? `${prestamos.length} ${prestamos.length === 1 ? 'préstamo vencido' : 'préstamos vencidos'}: la devolución superó el tiempo límite`}
          </p>
          <ul className="mt-2 space-y-1.5 text-sm text-alerta-texto">
            {prestamos.map((p) => (
              <li key={p.id} className="flex flex-wrap items-center gap-x-2">
                <span className="font-mono text-[13px] font-bold">{p.placa}</span>
                <span>{p.equipoNombre}</span>
                <span className="opacity-80">· {p.solicitanteNombre}</span>
                <span className="font-semibold">· {fmtVencimiento(p.fechaLimite, ahora)}</span>
              </li>
            ))}
          </ul>
        </div>
      </div>
    </div>
  )
}

/* ---------- Estados vacíos y carga ---------- */
export function Vacio({ titulo, detalle, icono: Icono = SearchX, children }) {
  return (
    <div className="card flex flex-col items-center px-6 py-14 text-center aparecer">
      <div className="mb-3 flex h-14 w-14 items-center justify-center rounded-full bg-slate-100"><Icono className="h-7 w-7 text-pizarra" aria-hidden="true" /></div>
      <p className="text-lg font-bold text-slate-900">{titulo}</p>
      {detalle && <p className="mt-1 max-w-md text-sm text-pizarra">{detalle}</p>}
      {children && <div className="mt-4">{children}</div>}
    </div>
  )
}

export function Cargando({ texto = 'Cargando…' }) {
  return (
    <div className="flex items-center justify-center gap-2 py-16 text-pizarra" role="status">
      <Loader2 className="h-5 w-5 animate-spin" aria-hidden="true" /> <span className="text-sm font-medium">{texto}</span>
    </div>
  )
}

export function ErrorCaja({ mensaje, className = '' }) {
  if (!mensaje) return null
  return (
    <div role="alert" className={`flex items-start gap-2 rounded-lg border border-alerta-borde bg-alerta-fondo px-3 py-2.5 text-sm font-medium text-alerta-texto ${className}`}>
      <TriangleAlert className="mt-0.5 h-4 w-4 shrink-0" aria-hidden="true" /> <span>{mensaje}</span>
    </div>
  )
}

/** Campo con etiqueta + mensaje de error resaltado en rojo sobre el campo exacto (PU-07). */
export function Campo({ etiqueta, error, ayuda, children, htmlFor }) {
  return (
    <div>
      <label htmlFor={htmlFor} className="label">{etiqueta}</label>
      {children}
      {error ? (
        <p id={`${htmlFor}-error`} role="alert" className="mt-1.5 flex items-center gap-1.5 text-sm font-semibold text-alerta-borde">
          <TriangleAlert className="h-4 w-4" aria-hidden="true" /> {error}
        </p>
      ) : ayuda ? <p className="mt-1.5 text-xs text-pizarra">{ayuda}</p> : null}
    </div>
  )
}

/* ---------- Modal accesible ---------- */
export function Modal({ abierto, onCerrar, titulo, subtitulo, children, pie, ancho = 'max-w-lg', acento = 'bg-usco' }) {
  const ref = useRef(null)
  const id = useId()
  const cerrarRef = useRef(onCerrar)
  cerrarRef.current = onCerrar
  useEffect(() => {
    if (!abierto) return undefined
    const previo = document.activeElement
    const onKey = (e) => {
      if (e.key === 'Escape') cerrarRef.current()
      if (e.key === 'Tab' && ref.current) {
        const f = ref.current.querySelectorAll('button:not([disabled]), [href], input:not([disabled]), select:not([disabled]), textarea:not([disabled]), [tabindex]:not([tabindex="-1"])')
        if (!f.length) return
        const a = f[0], z = f[f.length - 1]
        if (e.shiftKey && document.activeElement === a) { e.preventDefault(); z.focus() }
        else if (!e.shiftKey && document.activeElement === z) { e.preventDefault(); a.focus() }
      }
    }
    document.addEventListener('keydown', onKey)
    document.body.style.overflow = 'hidden'
    setTimeout(() => ref.current?.querySelector('input:not([disabled]), select, textarea')?.focus(), 30)
    return () => { document.removeEventListener('keydown', onKey); document.body.style.overflow = ''; previo?.focus?.() }
  }, [abierto])

  if (!abierto) return null
  return (
    <div className="fixed inset-0 z-50 flex items-end justify-center bg-slate-900/60 p-0 sm:items-center sm:p-4" onMouseDown={(e) => { if (e.target === e.currentTarget) onCerrar() }}>
      <div ref={ref} role="dialog" aria-modal="true" aria-labelledby={id}
        className={`aparecer flex max-h-[94vh] w-full ${ancho} flex-col overflow-hidden rounded-t-2xl bg-white shadow-2xl sm:rounded-2xl`}>
        <div className={`h-1.5 ${acento}`} />
        <div className="flex items-start justify-between gap-4 border-b border-slate-200 px-5 py-4">
          <div>
            <h2 id={id} className="text-lg font-extrabold">{titulo}</h2>
            {subtitulo && <p className="mt-0.5 text-sm text-pizarra">{subtitulo}</p>}
          </div>
          <button onClick={onCerrar} aria-label="Cerrar ventana" className="-mr-1 rounded-lg p-2 text-pizarra hover:bg-slate-100"><X className="h-5 w-5" /></button>
        </div>
        <div className="flex-1 overflow-y-auto px-5 py-4">{children}</div>
        {pie && <div className="flex flex-col-reverse gap-2 border-t border-slate-200 bg-slate-50 px-5 py-3 sm:flex-row sm:justify-end">{pie}</div>}
      </div>
    </div>
  )
}

/** Confirmación (PV-06: «Los datos no guardados se perderán», PV-08: acción irreversible). */
export function Confirmar({ abierto, titulo, mensaje, textoOk = 'Confirmar', textoCancelar = 'Cancelar', peligro, onOk, onCancelar, children }) {
  return (
    <Modal abierto={abierto} onCerrar={onCancelar} titulo={titulo} ancho="max-w-md" acento={peligro ? 'bg-alerta-borde' : 'bg-oro'}
      pie={<>
        <button className="btn-outline" onClick={onCancelar}>{textoCancelar}</button>
        <button className={peligro ? 'btn-danger' : 'btn-primary'} onClick={onOk}>{textoOk}</button>
      </>}>
      <div className="flex gap-3">
        <TriangleAlert className={`mt-0.5 h-6 w-6 shrink-0 ${peligro ? 'text-alerta-borde' : 'text-oro'}`} aria-hidden="true" />
        <div className="text-sm text-slate-700"><p className="font-semibold">{mensaje}</p>{children}</div>
      </div>
    </Modal>
  )
}

export const Chip = ({ children, className = '' }) => (
  <span className={`inline-flex items-center gap-1 rounded-md bg-slate-100 px-2 py-0.5 text-xs font-semibold text-pizarra-oscuro ${className}`}>{children}</span>
)

export function Kpi({ etiqueta, valor, detalle, icono: Icono, tono = 'text-usco', fondo = 'bg-usco-soft' }) {
  return (
    <div className="card flex items-start gap-3 p-4">
      <div className={`flex h-11 w-11 shrink-0 items-center justify-center rounded-xl ${fondo}`}>{Icono && <Icono className={`h-5 w-5 ${tono}`} aria-hidden="true" />}</div>
      <div className="min-w-0">
        <p className="text-xs font-bold uppercase tracking-wide text-pizarra">{etiqueta}</p>
        <p className="font-titulo text-2xl font-extrabold leading-tight text-slate-900">{valor}</p>
        {detalle && <p className="mt-0.5 text-xs text-pizarra">{detalle}</p>}
      </div>
    </div>
  )
}
