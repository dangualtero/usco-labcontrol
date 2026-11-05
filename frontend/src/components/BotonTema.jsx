import { Moon, Sun } from 'lucide-react'
import { useTema } from '../context/TemaContext.jsx'

/** Interruptor de tema. `variante="encabezado"` para la barra roja; `variante="claro"` para fondos neutros (login). */
export default function BotonTema({ variante = 'encabezado', conTexto = false, className = '' }) {
  const { oscuro, alternar } = useTema()
  const estilo = variante === 'encabezado'
    ? 'border border-oro-claro/50 text-white hover:bg-white/10'
    : 'border border-slate-300 bg-superficie text-pizarra-oscuro hover:bg-slate-50 shadow-sm'
  return (
    <button type="button" onClick={alternar} role="switch" aria-checked={oscuro}
      aria-label={oscuro ? 'Cambiar a modo claro' : 'Cambiar a modo oscuro'} title={oscuro ? 'Modo claro' : 'Modo oscuro'}
      className={`inline-flex min-h-[44px] min-w-[44px] items-center justify-center gap-2 rounded-lg px-3 text-sm font-semibold transition-colors ${estilo} ${className}`}>
      {oscuro ? <Sun className="h-[18px] w-[18px]" aria-hidden="true" /> : <Moon className="h-[18px] w-[18px]" aria-hidden="true" />}
      {conTexto && <span>{oscuro ? 'Modo claro' : 'Modo oscuro'}</span>}
    </button>
  )
}
