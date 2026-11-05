import { useState } from 'react'
import { Link } from 'react-router-dom'
import { ArrowRightLeft, CircleCheck, LogOut, PackageOpen } from 'lucide-react'
import { api } from '../../api/index.js'
import { useSede } from '../../context/SedeContext.jsx'
import { useAuth } from '../../context/AuthContext.jsx'
import { useToast } from '../../context/ToastContext.jsx'
import { useAhora, useDatos } from '../../lib/hooks.js'
import { fmtFechaHora, fmtVencimiento } from '../../lib/fecha.js'
import { Campo, Cargando, Confirmar, ErrorCaja, PrestamoBadge, Vacio } from '../../components/ui.jsx'

/** Cierre de Turno (PF-12): resumen de equipos pendientes y transferencia de custodia. */
export default function CierreTurno() {
  const { ruta } = useSede()
  const { cerrar } = useAuth()
  const toast = useToast()
  const ahora = useAhora(30000)
  const { datos, error, recargar } = useDatos(() => api.cierreTurno(), [], { cada: 0 })
  const [destino, setDestino] = useState('')
  const [err, setErr] = useState('')
  const [confirmar, setConfirmar] = useState(false)
  const [resultado, setResultado] = useState(null)

  const transferir = async () => {
    setConfirmar(false); setErr('')
    try {
      const r = await api.transferirCustodia(Number(destino))
      setResultado(r)
      toast.exito('Custodia transferida', `${r.transferidos} ${r.transferidos === 1 ? 'equipo' : 'equipos'} → ${r.destino}`)
      recargar()
    } catch (e) { setErr(e.message) }
  }

  const n = datos?.pendientes.length ?? 0

  return (
    <div className="mx-auto max-w-3xl space-y-5">
      <div>
        <p className="text-xs font-bold uppercase tracking-widest text-usco">Cambio de turno</p>
        <h1 className="text-2xl font-extrabold sm:text-3xl">Cierre de Turno</h1>
        <p className="text-sm text-pizarra">Verifica los equipos que siguen en préstamo y transfiere su custodia al auxiliar que recibe.</p>
      </div>
      <ErrorCaja mensaje={error} />
      {!datos && !error && <Cargando />}

      {datos && !resultado && (
        <>
          <div className={`card flex items-center gap-3 p-4 ${n ? 'border-amber-400 bg-amber-50' : 'border-entrada bg-emerald-50'}`}>
            {n ? <PackageOpen className="h-8 w-8 text-amber-700" aria-hidden="true" /> : <CircleCheck className="h-8 w-8 text-entrada" aria-hidden="true" />}
            <div>
              <p className="text-lg font-extrabold">{n ? `${n} ${n === 1 ? 'equipo pendiente' : 'equipos pendientes'} · Transferir custodia` : 'Sin equipos pendientes'}</p>
              <p className="text-sm text-slate-700">{n ? 'Estos equipos siguen en manos de usuarios bajo tu custodia.' : 'Puedes cerrar tu turno sin transferir nada.'}</p>
            </div>
          </div>

          {n > 0 && (
            <ul className="space-y-2">
              {datos.pendientes.map((p) => (
                <li key={p.id} className={`card flex flex-wrap items-center justify-between gap-3 p-4 ${p.vencido ? 'border-alerta-borde bg-alerta-fondo' : ''}`}>
                  <div className="min-w-0"><p className="font-extrabold">{p.equipoNombre}</p><p className="text-sm text-pizarra"><span className="font-mono font-semibold">{p.placa}</span> · {p.solicitanteNombre}</p></div>
                  <div className="text-right"><PrestamoBadge estado={p.estado} vencido={p.vencido} /><p className="mt-1 text-xs font-semibold text-pizarra">Límite {fmtFechaHora(p.fechaLimite)} · {fmtVencimiento(p.fechaLimite, ahora)}</p></div>
                </li>
              ))}
            </ul>
          )}

          <section className="card space-y-4 p-5">
            <Campo etiqueta="Auxiliar que recibe el turno" htmlFor="destino" ayuda="Quedará registrado en el historial como responsable de la custodia.">
              <select id="destino" className="input" value={destino} onChange={(e) => setDestino(e.target.value)}>
                <option value="">Selecciona un auxiliar…</option>
                {datos.auxiliares.map((a) => <option key={a.id} value={a.id}>{a.nombre}</option>)}
              </select>
            </Campo>
            <ErrorCaja mensaje={err} />
            <div className="flex flex-col gap-2 sm:flex-row sm:justify-end">
              <Link to={ruta('/auxiliar')} className="btn-outline">Volver a ventanilla</Link>
              <button className="btn-primary" disabled={!destino} onClick={() => setConfirmar(true)}><ArrowRightLeft className="h-4 w-4" aria-hidden="true" /> {n ? 'Transferir custodia y cerrar turno' : 'Cerrar turno'}</button>
            </div>
          </section>
        </>
      )}

      {resultado && (
        <Vacio icono={CircleCheck} titulo={`Turno cerrado · ${resultado.transferidos} ${resultado.transferidos === 1 ? 'equipo transferido' : 'equipos transferidos'}`} detalle={`La custodia quedó a cargo de ${resultado.destino}. El movimiento quedó registrado en el historial inmutable.`}>
          <div className="flex flex-col gap-2 sm:flex-row">
            <Link to={ruta('/auxiliar')} className="btn-outline">Volver a ventanilla</Link>
            <button className="btn-primary" onClick={() => cerrar('manual')}><LogOut className="h-4 w-4" aria-hidden="true" /> Cerrar sesión</button>
          </div>
        </Vacio>
      )}

      <Confirmar abierto={confirmar} titulo="Confirmar cierre de turno" mensaje={`¿Transferir ${n} ${n === 1 ? 'equipo' : 'equipos'} a ${datos?.auxiliares.find((a) => String(a.id) === destino)?.nombre ?? ''}?`}
        textoOk="Sí, transferir" onCancelar={() => setConfirmar(false)} onOk={transferir} />
    </div>
  )
}
