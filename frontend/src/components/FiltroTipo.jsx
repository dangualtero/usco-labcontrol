import { Beaker, Boxes, Microscope } from 'lucide-react'

const OPCIONES = [
  { valor: 'todos', texto: 'Todos', Icono: Boxes },
  { valor: 'EQUIPO', texto: 'Equipos', Icono: Microscope },
  { valor: 'INSUMO', texto: 'Insumos', Icono: Beaker },
]

/** Pestañas para filtrar el inventario por categoría: Todos · Equipos · Insumos (con conteos). */
export function FiltroTipo({ valor, onCambio, conteo = {}, opciones = OPCIONES, etiqueta = 'Filtrar por categoría' }) {
  return (
    <div role="group" aria-label={etiqueta} className="inline-flex w-full flex-wrap gap-1 rounded-xl border border-slate-200 bg-white p-1 sm:w-auto">
      {opciones.map(({ valor: v, texto, Icono }) => (
        <button key={v} type="button" onClick={() => onCambio(v)} aria-pressed={valor === v}
          className={`flex min-h-[44px] flex-1 items-center justify-center gap-2 rounded-lg px-4 text-sm font-bold transition-colors sm:flex-none ${valor === v ? 'bg-usco text-white shadow' : 'text-pizarra-oscuro hover:bg-slate-100'}`}>
          <Icono className="h-4 w-4" aria-hidden="true" /> {texto}
          {conteo[v] !== undefined && <span className={`rounded-full px-1.5 text-xs ${valor === v ? 'bg-white/20' : 'bg-slate-100'}`}>{conteo[v]}</span>}
        </button>
      ))}
    </div>
  )
}
