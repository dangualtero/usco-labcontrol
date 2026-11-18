import {
  Ban, CalendarClock, CircleCheck, CircleDot, CircleX, ClipboardCheck, FileEdit, Hourglass, Lock, Play, PowerOff, Send,
  ShieldQuestion, Undo2, Wrench,
} from 'lucide-react'
import { PRIORIDAD_CLASE } from '../lib/constantes.js'

/** Badge genérico: el estado nunca depende solo del color (PA-07): icono + texto + color. */
function Badge({ def, className = '' }) {
  const Ic = def.Icono
  return (
    <span className={`inline-flex items-center gap-1 whitespace-nowrap rounded-full border px-2 py-0.5 text-[11px] font-extrabold ${def.clase} ${className}`}>
      {Ic && <Ic className="h-3 w-3" aria-hidden="true" />} {def.texto}
    </span>
  )
}
const AZUL = 'border-blue-600 bg-blue-50 text-blue-900'
const AMBAR = 'border-amber-600 bg-amber-50 text-amber-900'
const VERDE = 'border-emerald-600 bg-emerald-50 text-emerald-800'
const ROJO = 'border-alerta-borde bg-alerta-fondo text-alerta-texto'
const GRIS = 'border-slate-400 bg-slate-100 text-slate-700'
const VINO = 'border-usco bg-usco-soft text-usco-darker'

export const ESTADOS_EQUIPO = {
  DISPONIBLE: { texto: 'Operativo', Icono: CircleCheck, clase: VERDE },
  MANTENIMIENTO: { texto: 'En mantenimiento', Icono: Wrench, clase: AMBAR },
  FUERA_SERVICIO: { texto: 'Fuera de servicio', Icono: PowerOff, clase: ROJO },
}
export const EstadoEquipo = ({ estado, className }) => <Badge def={ESTADOS_EQUIPO[estado] ?? ESTADOS_EQUIPO.DISPONIBLE} className={className} />

export const ESTADOS_SOLICITUD = {
  BORRADOR: { texto: 'Borrador', Icono: FileEdit, clase: GRIS },
  EN_REVISION: { texto: 'En revisión del coordinador', Icono: ShieldQuestion, clase: AZUL },
  ENVIADA: { texto: 'Enviada al Administrador', Icono: Send, clase: VINO },
  DEVUELTA: { texto: 'Devuelta para ajuste', Icono: Undo2, clase: AMBAR },
  APROBADA: { texto: 'Aprobada', Icono: CircleCheck, clase: VERDE },
  RECHAZADA: { texto: 'Rechazada', Icono: CircleX, clase: ROJO },
  CERRADA: { texto: 'Cerrada', Icono: Lock, clase: GRIS },
  CANCELADA: { texto: 'Cancelada', Icono: Ban, clase: GRIS },
}
export const EstadoSolicitud = ({ estado, className }) => <Badge def={ESTADOS_SOLICITUD[estado] ?? ESTADOS_SOLICITUD.BORRADOR} className={className} />

export const ESTADOS_MANTENIMIENTO = {
  REQUERIDO: { texto: 'Requerido', Icono: CircleDot, clase: AZUL },
  PROGRAMADO: { texto: 'Programado', Icono: CalendarClock, clase: VINO },
  EN_EJECUCION: { texto: 'En ejecución', Icono: Play, clase: AMBAR },
  REALIZADO: { texto: 'Realizado', Icono: CircleCheck, clase: VERDE },
  CANCELADO: { texto: 'Cancelado', Icono: Ban, clase: GRIS },
}
export const EstadoMantenimiento = ({ estado, vencido, className }) => vencido
  ? <Badge def={{ texto: 'Vencido', Icono: CalendarClock, clase: ROJO }} className={className} />
  : <Badge def={ESTADOS_MANTENIMIENTO[estado] ?? ESTADOS_MANTENIMIENTO.REQUERIDO} className={className} />

export const VALIDACION = {
  PENDIENTE: { texto: 'Por validar', Icono: Hourglass, clase: AMBAR },
  VALIDADA: { texto: 'Validada', Icono: ClipboardCheck, clase: VERDE },
  RECHAZADA: { texto: 'Rechazada', Icono: CircleX, clase: GRIS },
}
export const Validacion = ({ estado, className }) => <Badge def={VALIDACION[estado] ?? VALIDACION.PENDIENTE} className={className} />

export const ESTADOS_NOVEDAD = {
  ABIERTO: { texto: 'Abierta', Icono: CircleDot, clase: AZUL },
  EN_PROCESO: { texto: 'En proceso', Icono: Hourglass, clase: AMBAR },
  RESUELTO: { texto: 'Resuelta', Icono: CircleCheck, clase: VERDE },
}
export const EstadoNovedad = ({ estado, className }) => <Badge def={ESTADOS_NOVEDAD[estado] ?? ESTADOS_NOVEDAD.ABIERTO} className={className} />

export const ESTADOS_COMPROMISO = {
  PENDIENTE: { texto: 'Pendiente', Icono: CircleDot, clase: AZUL },
  EN_CURSO: { texto: 'En curso', Icono: Hourglass, clase: AMBAR },
  CUMPLIDO: { texto: 'Cumplido', Icono: CircleCheck, clase: VERDE },
  CANCELADO: { texto: 'Cancelado', Icono: Ban, clase: GRIS },
}
export const EstadoCompromiso = ({ estado, vencido, className }) => vencido
  ? <Badge def={{ texto: 'Vencido', Icono: CalendarClock, clase: ROJO }} className={className} />
  : <Badge def={ESTADOS_COMPROMISO[estado] ?? ESTADOS_COMPROMISO.PENDIENTE} className={className} />

export const VERIFICACION = {
  PENDIENTE: { texto: 'Por verificar', Icono: Hourglass, clase: AMBAR },
  VERIFICADO: { texto: 'Verificado', Icono: CircleCheck, clase: VERDE },
  OBSERVADO: { texto: 'Con observaciones', Icono: Undo2, clase: ROJO },
}
export const Verificacion = ({ estado, className }) => <Badge def={VERIFICACION[estado] ?? VERIFICACION.PENDIENTE} className={className} />

export const ESTADOS_CONTRATO = {
  SIN_INICIAR: 'Sin iniciar', EN_PROCESO: 'En proceso de contratación', CONTRATADO: 'Contratado', EN_EJECUCION: 'En ejecución', LIQUIDADO: 'Liquidado',
}

export function Prioridad({ valor, prefijo = '', className = '' }) {
  if (!valor) return null
  return <span className={`inline-flex whitespace-nowrap rounded-full border px-2 py-0.5 text-[11px] font-extrabold ${PRIORIDAD_CLASE[valor] ?? GRIS} ${className}`}>{prefijo}{valor}</span>
}

/** Encabezado estándar de cada pantalla. */
export function Encabezado({ modulo, titulo, detalle, children }) {
  return (
    <div className="flex flex-col gap-3 lg:flex-row lg:items-end lg:justify-between">
      <div className="min-w-0">
        <p className="text-xs font-bold uppercase tracking-widest text-usco">{modulo}</p>
        <h1 className="text-2xl font-extrabold sm:text-3xl">{titulo}</h1>
        {detalle && <p className="mt-0.5 max-w-3xl text-sm text-pizarra">{detalle}</p>}
      </div>
      {children && <div className="flex shrink-0 flex-wrap gap-2">{children}</div>}
    </div>
  )
}
