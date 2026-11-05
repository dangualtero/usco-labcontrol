import { useEffect, useState } from 'react'
import { Wrench, CircleCheck } from 'lucide-react'
import { useToast } from '../context/ToastContext.jsx'
import { Campo, Modal } from './ui.jsx'

/**
 * Enviar un equipo a mantenimiento (con motivo) o devolverlo a disponible.
 * `accion` recibe (placa, estado, motivo) y llama a la API correspondiente al rol (auxiliar o coordinador).
 */
export default function EstadoEquipoModal({ equipo, accion, onCerrar, onHecho }) {
  const toast = useToast()
  const [motivo, setMotivo] = useState('')
  const [error, setError] = useState('')
  const [enviando, setEnviando] = useState(false)
  const aMantenimiento = equipo?.estado !== 'MANTENIMIENTO'

  useEffect(() => { if (equipo) { setMotivo(''); setError('') } }, [equipo])

  const enviar = async () => {
    if (aMantenimiento && motivo.trim().length < 5) { setError('Indica el motivo del mantenimiento (mínimo 5 caracteres)'); return }
    setEnviando(true)
    try {
      await accion(equipo.placa, aMantenimiento ? 'MANTENIMIENTO' : 'DISPONIBLE', motivo.trim())
      toast.exito(aMantenimiento ? 'Equipo enviado a mantenimiento' : 'Equipo disponible', `${equipo.placa} · ${equipo.nombre}`)
      onHecho?.(); onCerrar()
    } catch (e) { setError(e.message) } finally { setEnviando(false) }
  }

  return (
    <Modal abierto={!!equipo} onCerrar={onCerrar} ancho="max-w-md" acento={aMantenimiento ? 'bg-slate-600' : 'bg-entrada'}
      titulo={aMantenimiento ? 'Enviar a mantenimiento' : 'Marcar como disponible'} subtitulo={equipo ? `${equipo.nombre} · ${equipo.placa}` : undefined}
      pie={<>
        <button className="btn-outline" onClick={onCerrar}>Cancelar</button>
        <button className={aMantenimiento ? 'btn-primary' : 'btn-entrada'} onClick={enviar} disabled={enviando}>
          {aMantenimiento ? <Wrench className="h-4 w-4" aria-hidden="true" /> : <CircleCheck className="h-4 w-4" aria-hidden="true" />} {enviando ? 'Guardando…' : aMantenimiento ? 'Enviar a mantenimiento' : 'Marcar disponible'}
        </button>
      </>}>
      {aMantenimiento ? (
        <Campo etiqueta="Motivo del mantenimiento" htmlFor="m-motivo" error={error}>
          <textarea id="m-motivo" rows={3} className={`input ${error ? 'input-error' : ''}`} value={motivo} onChange={(e) => setMotivo(e.target.value)} maxLength={250} placeholder="Ej. Calibración anual, falla en la pantalla…" />
        </Campo>
      ) : (
        <>
          <p className="text-sm text-slate-700">Confirma que el mantenimiento terminó y el equipo puede volver a prestarse.</p>
          {error && <p role="alert" className="mt-2 text-sm font-semibold text-alerta-borde">{error}</p>}
        </>
      )}
    </Modal>
  )
}
