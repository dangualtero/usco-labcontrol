import { Component } from 'react'

/**
 * Evita la «pantalla en blanco»: si una pantalla falla al dibujarse, muestra el error y opciones para continuar
 * en lugar de dejar la página vacía. El detalle técnico ayuda a encontrar la causa (F12 → Consola también lo muestra).
 */
export default class ErrorBoundary extends Component {
  constructor(props) {
    super(props)
    this.state = { error: null }
  }

  static getDerivedStateFromError(error) {
    return { error }
  }

  componentDidCatch(error, info) {
    console.error('[LabControl] Error al mostrar la pantalla:', error, info?.componentStack)
  }

  render() {
    const { error } = this.state
    if (!error) return this.props.children
    const sede = window.location.pathname.split('/')[1]
    return (
      <div className="flex min-h-screen items-center justify-center bg-fondo p-6">
        <div className="card w-full max-w-lg p-8 text-center" role="alert">
          <img src="/escudo-usco.png" alt="" width="64" height="63" className="mx-auto mb-4 h-16 w-auto" />
          <h1 className="text-xl font-extrabold">Esta pantalla no se pudo mostrar</h1>
          <p className="mt-2 text-sm text-pizarra">Recarga la página. Si el problema continúa, vuelve al inicio o informa a la Coordinación de Laboratorios.</p>
          <pre className="mt-4 max-h-32 overflow-auto rounded-lg bg-slate-100 p-3 text-left text-xs text-slate-700">{String(error?.message ?? error)}</pre>
          <div className="mt-6 flex flex-wrap justify-center gap-2">
            <button type="button" className="btn-primary" onClick={() => window.location.reload()}>Recargar</button>
            <a className="btn-outline" href={sede ? `/${sede}` : '/'}>Volver al inicio</a>
          </div>
        </div>
      </div>
    )
  }
}
