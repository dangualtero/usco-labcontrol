import { createContext, useCallback, useContext, useRef, useState } from 'react'
import { CircleCheck, TriangleAlert, Info, X } from 'lucide-react'

const Ctx = createContext(null)
export const useToast = () => useContext(Ctx)

const ESTILOS = {
  exito: { caja: 'border-entrada bg-emerald-50 text-emerald-900', Icono: CircleCheck, color: 'text-entrada' },
  error: { caja: 'border-alerta-borde bg-alerta-fondo text-alerta-texto', Icono: TriangleAlert, color: 'text-alerta-borde' },
  info: { caja: 'border-pizarra bg-slate-50 text-slate-800', Icono: Info, color: 'text-pizarra' },
}

/** Toasts accesibles (PU-12): respuesta inmediata y visible tras cada acción. */
export function ToastProvider({ children }) {
  const [items, setItems] = useState([])
  const id = useRef(0)

  const cerrar = useCallback((k) => setItems((l) => l.filter((x) => x.k !== k)), [])
  const mostrar = useCallback((tipo, titulo, detalle) => {
    const k = ++id.current
    setItems((l) => [...l, { k, tipo, titulo, detalle }])
    setTimeout(() => cerrar(k), tipo === 'error' ? 7000 : 5000)
  }, [cerrar])

  const api = {
    exito: (t, d) => mostrar('exito', t, d),
    error: (t, d) => mostrar('error', t, d),
    info: (t, d) => mostrar('info', t, d),
  }

  return (
    <Ctx.Provider value={api}>
      {children}
      <div className="fixed bottom-4 right-4 left-4 sm:left-auto z-[100] flex flex-col gap-2 sm:w-[380px]" role="status" aria-live="polite">
        {items.map((t) => {
          const { caja, Icono, color } = ESTILOS[t.tipo]
          return (
            <div key={t.k} className={`aparecer flex items-start gap-3 rounded-lg border-l-4 px-4 py-3 shadow-lg ${caja}`}>
              <Icono className={`mt-0.5 h-5 w-5 shrink-0 ${color}`} aria-hidden="true" />
              <div className="flex-1 text-sm">
                <p className="font-bold">{t.titulo}</p>
                {t.detalle && <p className="mt-0.5 opacity-90">{t.detalle}</p>}
              </div>
              <button onClick={() => cerrar(t.k)} aria-label="Cerrar notificación" className="rounded p-1 hover:bg-black/5">
                <X className="h-4 w-4" />
              </button>
            </div>
          )
        })}
      </div>
    </Ctx.Provider>
  )
}
