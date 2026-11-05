import { useEffect, useState } from 'react'
import { Ban } from 'lucide-react'
import { api } from '../../api/index.js'
import { useToast } from '../../context/ToastContext.jsx'
import { fmtFranja } from '../../lib/fecha.js'
import { Campo, Modal } from '../../components/ui.jsx'

/** Rechazo de una solicitud pendiente: el motivo queda en el historial y lo ve el estudiante en «Mis reservas». */
export default function RechazoModal({ solicitud, onCerrar, onHecho }) {
  const toast = useToast()
  const [motivo, setMotivo] = useState('')
  const [error, setError] = useState('')
  const [enviando, setEnviando] = useState(false)

  useEffect(() => { if (solicitud) { setMotivo(''); setError('') } }, [solicitud])

  const enviar = async () => {
    if (motivo.trim().length < 5) { setError('Indica el motivo del rechazo (mínimo 5 caracteres)'); return }
    setEnviando(true)
    try {
      await api.rechazarReserva(solicitud.id, motivo.trim())
      toast.info('Solicitud rechazada', `${solicitud.codigo} · ${solicitud.solicitanteNombre}`)
      onHecho?.(); onCerrar()
    } catch (e) { setError(e.message) } finally { setEnviando(false) }
  }

  return (
    <Modal abierto={!!solicitud} onCerrar={onCerrar} titulo="Rechazar solicitud" acento="bg-alerta-borde" ancho="max-w-md"
      subtitulo={solicitud ? `${solicitud.equipoNombre} · ${solicitud.placa}` : undefined}
      pie={<>
        <button className="btn-outline" onClick={onCerrar}>Cancelar</button>
        <button className="btn-danger" onClick={enviar} disabled={enviando}><Ban className="h-4 w-4" aria-hidden="true" /> {enviando ? 'Rechazando…' : 'Rechazar solicitud'}</button>
      </>}>
      {solicitud && (
        <div className="space-y-4">
          <p className="rounded-lg bg-slate-50 px-3 py-2 text-sm ring-1 ring-slate-200"><strong>{solicitud.solicitanteNombre}</strong> · {fmtFranja(solicitud.franjaInicio, solicitud.franjaFin)}</p>
          <Campo etiqueta="Motivo del rechazo" htmlFor="r-motivo" error={error}>
            <textarea id="r-motivo" rows={3} className={`input ${error ? 'input-error' : ''}`} value={motivo} onChange={(e) => setMotivo(e.target.value)} maxLength={250} placeholder="Ej. El equipo estará en campo esa fecha." />
          </Campo>
        </div>
      )}
    </Modal>
  )
}
