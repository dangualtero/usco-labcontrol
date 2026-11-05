import { History, NotebookPen } from 'lucide-react'
import { api } from '../../api/index.js'
import { useDatos } from '../../lib/hooks.js'
import { fmtFechaHora, fmtDuracion, minutosEntre } from '../../lib/fecha.js'
import { Cargando, ErrorCaja, PrestamoBadge, Vacio } from '../../components/ui.jsx'

/** Mi Historial (PF-11): préstamos pasados con sus observaciones. */
export default function MiHistorial() {
  const { datos, error } = useDatos(() => api.misPrestamos(), [], { cada: 10000 })
  const pasados = (datos ?? []).filter((p) => ['DEVUELTO', 'CANCELADO', 'RECHAZADO'].includes(p.estado))

  return (
    <div className="space-y-5">
      <div>
        <h1 className="flex items-center gap-2 text-2xl font-extrabold"><History className="h-6 w-6 text-usco" aria-hidden="true" /> Mi historial</h1>
        <p className="text-sm text-pizarra">Tus préstamos anteriores con el estado en que se devolvieron los equipos.</p>
      </div>
      <ErrorCaja mensaje={error} />
      {!datos && !error && <Cargando />}
      {datos && pasados.length === 0 && <Vacio icono={History} titulo="Aún no tienes préstamos finalizados" detalle="Tus devoluciones aparecerán aquí junto con las observaciones del auxiliar." />}
      {pasados.length > 0 && (
        <>
          {/* Tabla en escritorio */}
          <div className="card hidden overflow-hidden md:block">
            <table className="w-full">
              <thead><tr><th className="th">Equipo</th><th className="th">Salida</th><th className="th">Entrada</th><th className="th">Duración</th><th className="th">Estado</th><th className="th">Observaciones</th></tr></thead>
              <tbody>
                {pasados.map((p) => (
                  <tr key={p.id}>
                    <td className="td"><p className="font-bold text-slate-900">{p.equipoNombre}</p><p className="placa">{p.placa} · {p.laboratorio}</p></td>
                    <td className="td whitespace-nowrap">{fmtFechaHora(p.fechaSalida ?? p.franjaInicio)}</td>
                    <td className="td whitespace-nowrap">{fmtFechaHora(p.fechaEntrada)}</td>
                    <td className="td whitespace-nowrap">{p.fechaEntrada ? fmtDuracion(minutosEntre(p.fechaSalida, p.fechaEntrada)) : '—'}</td>
                    <td className="td"><PrestamoBadge estado={p.estado} /></td>
                    <td className="td max-w-xs text-[13px]">{p.observaciones ?? <span className="text-slate-400">Sin observaciones</span>}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
          {/* Tarjetas en móvil */}
          <ul className="space-y-3 md:hidden">
            {pasados.map((p) => (
              <li key={p.id} className="card p-4">
                <div className="flex items-start justify-between gap-2"><div><p className="font-extrabold">{p.equipoNombre}</p><p className="placa">{p.placa}</p></div><PrestamoBadge estado={p.estado} /></div>
                <p className="mt-2 text-sm">Salida: {fmtFechaHora(p.fechaSalida ?? p.franjaInicio)}</p>
                <p className="text-sm">Entrada: {fmtFechaHora(p.fechaEntrada)}</p>
                {p.observaciones && <p className="mt-2 flex gap-1.5 rounded bg-slate-50 p-2 text-[13px]"><NotebookPen className="mt-0.5 h-3.5 w-3.5 shrink-0 text-pizarra" aria-hidden="true" />{p.observaciones}</p>}
              </li>
            ))}
          </ul>
        </>
      )}
    </div>
  )
}
