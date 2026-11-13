import { Beaker, Microscope, TriangleAlert } from 'lucide-react'
import { UMBRAL_ALERTA, fmtCantidad } from '../lib/constantes.js'

/** Distintivo Equipo / Insumo (icono + texto, no solo color). */
export function TipoBadge({ tipo, className = '' }) {
  const insumo = tipo === 'INSUMO'
  const Ic = insumo ? Beaker : Microscope
  return (
    <span className={`inline-flex items-center gap-1 rounded-md border px-2 py-0.5 text-[11px] font-bold ${insumo ? 'border-sky-600 bg-sky-50 text-sky-900' : 'border-slate-400 bg-slate-50 text-slate-700'} ${className}`}>
      <Ic className="h-3 w-3" aria-hidden="true" /> {insumo ? 'Insumo' : 'Equipo'}
    </span>
  )
}

/** ¿El insumo está agotado o en 25 % o menos? */
export const enAlerta = (e) => e?.tipo === 'INSUMO' && (Number(e.cantidad) === 0 || (e.porcentaje !== null && e.porcentaje !== undefined && e.porcentaje <= UMBRAL_ALERTA))

/**
 * Existencias de un equipo o insumo. Insumos: cantidad, referencia y barra de nivel; con 25 % o menos se marca en
 * rojo con el texto «En alerta» (el color nunca es la única señal).
 */
export function Existencias({ equipo: e, compacto = false }) {
  if (!e) return null
  if (e.tipo !== 'INSUMO') {
    return <span className="text-sm font-semibold text-slate-800">{fmtCantidad(e.cantidad ?? 1, 'UNIDAD')}</span>
  }
  const pct = e.porcentaje ?? null
  const agotado = Number(e.cantidad) === 0
  const alerta = enAlerta(e)
  const color = agotado || (pct !== null && pct <= UMBRAL_ALERTA) ? 'bg-alerta-borde' : pct !== null && pct <= 50 ? 'bg-amber-500' : 'bg-emerald-600'
  return (
    <div className={compacto ? 'min-w-[130px]' : 'w-full'}>
      <p className="flex flex-wrap items-baseline gap-x-1 text-sm">
        <strong className={alerta ? 'text-alerta-texto' : 'text-slate-900'}>{fmtCantidad(e.cantidad, e.unidad)}</strong>
        {e.cantidadReferencia != null && <span className="text-xs text-pizarra">de {fmtCantidad(e.cantidadReferencia, e.unidad)}</span>}
      </p>
      {pct !== null && (
        <div className="mt-1 h-2 w-full overflow-hidden rounded-full bg-slate-200" role="meter" aria-valuemin={0} aria-valuemax={100} aria-valuenow={Math.min(pct, 100)} aria-label={`Existencias al ${pct} %`}>
          <div className={`h-full rounded-full ${color}`} style={{ width: `${Math.max(Math.min(pct, 100), agotado ? 0 : 3)}%` }} />
        </div>
      )}
      <p className={`mt-0.5 flex items-center gap-1 text-[11px] font-bold ${alerta ? 'text-alerta-texto' : 'text-pizarra'}`}>
        {alerta && <TriangleAlert className="h-3 w-3" aria-hidden="true" />}
        {agotado ? 'Agotado' : alerta ? `En alerta · ${pct} %` : pct !== null ? `${pct} %` : 'Sin referencia'}
      </p>
    </div>
  )
}
