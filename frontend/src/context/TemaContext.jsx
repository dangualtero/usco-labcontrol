import { createContext, useCallback, useContext, useEffect, useMemo, useState } from 'react'

const CLAVE = 'usco.labcontrol.tema'
const Ctx = createContext({ oscuro: false, alternar: () => {} })
export const useTema = () => useContext(Ctx)

function inicial() {
  try {
    const g = localStorage.getItem(CLAVE)
    if (g === 'oscuro' || g === 'claro') return g === 'oscuro'
  } catch { /* almacenamiento no disponible */ }
  return window.matchMedia?.('(prefers-color-scheme: dark)').matches ?? false
}

/** Modo claro / oscuro: respeta la preferencia del sistema la primera vez y recuerda la elección del usuario. */
export function TemaProvider({ children }) {
  const [oscuro, setOscuro] = useState(inicial)

  useEffect(() => {
    document.documentElement.classList.toggle('dark', oscuro)
    document.querySelector('meta[name="theme-color"]')?.setAttribute('content', oscuro ? '#5C0D12' : '#8F141B')
  }, [oscuro])

  const alternar = useCallback(() => {
    setOscuro((v) => {
      const n = !v
      try { localStorage.setItem(CLAVE, n ? 'oscuro' : 'claro') } catch { /* sin almacenamiento */ }
      return n
    })
  }, [])

  const valor = useMemo(() => ({ oscuro, alternar }), [oscuro, alternar])
  return <Ctx.Provider value={valor}>{children}</Ctx.Provider>
}
