import { useEffect, useState } from 'react'
import { Save } from 'lucide-react'
import { api } from '../api/index.js'
import { useToast } from '../context/ToastContext.jsx'
import { Campo, ErrorCaja, Modal } from './ui.jsx'
import { ESTADOS_EQUIPO } from './Estados.jsx'

/** Cambiar el estado de un equipo: Operativo, En mantenimiento o Fuera de servicio (con motivo). */
export default function EstadoEquipoModal({ equipo, onCerrar, onHecho }) {
  const toast = useToast()
  const [estado, setEstado] = useState('DISPONIBLE')
  const [motivo, setMotivo] = useState('')
  const [error, setError] = useState('')
  const [enviando, setEnviando] = useState(false)

  useEffect(() => {
    if (!equipo) return
    setMotivo(''); setError('')
    setEstado(Object.keys(ESTADOS_EQUIPO).find((k) => k !== equipo.estado) ?? 'DISPONIBLE')
  }, [equipo])

  const enviar = async () => {
    if (estado !== 'DISPONIBLE' && motivo.trim().length < 5) { setError('Indica el motivo (mínimo 5 caracteres)'); return }
    setEnviando(true)
    try {
      await api.estadoEquipo(equipo.placa, estado, motivo.trim())
      toast.exito('Estado actualizado', `${equipo.placa} · ${ESTADOS_EQUIPO[estado].texto}`)
      onHecho?.(); onCerrar()
    } catch (e) { setError(e.message) } finally { setEnviando(false) }
  }

  return (
    <Modal abierto={!!equipo} onCerrar={onCerrar} ancho="max-w-md" titulo="Cambiar estado del equipo" subtitulo={equipo ? `${equipo.nombre} · ${equipo.placa}` : undefined}
      pie={<>
        <button className="btn-outline" onClick={onCerrar}>Cancelar</button>
        <button className="btn-primary" onClick={enviar} disabled={enviando}><Save className="h-4 w-4" aria-hidden="true" /> {enviando ? 'Guardando…' : 'Guardar'}</button>
      </>}>
      {equipo && (
        <div className="space-y-4">
          <fieldset>
            <legend className="label">Nuevo estado</legend>
            <div className="grid gap-2">
              {Object.entries(ESTADOS_EQUIPO).filter(([k]) => k !== equipo.estado).map(([k, d]) => (
                <label key={k} className={`flex min-h-[44px] cursor-pointer items-center gap-2 rounded-lg border-2 px-3 text-sm font-semibold ${estado === k ? 'border-usco bg-usco-soft' : 'border-slate-200'}`}>
                  <input type="radio" name="estado-eq" checked={estado === k} onChange={() => setEstado(k)} className="accent-[#8F141B]" />
                  <d.Icono className="h-4 w-4" aria-hidden="true" /> {d.texto}
                </label>
              ))}
            </div>
          </fieldset>
          <Campo etiqueta={estado === 'DISPONIBLE' ? 'Observación (opcional)' : 'Motivo'} htmlFor="ee-motivo">
            <textarea id="ee-motivo" rows={3} className="input" value={motivo} onChange={(e) => setMotivo(e.target.value)} maxLength={250}
              placeholder={estado === 'FUERA_SERVICIO' ? 'Ej. Tarjeta principal quemada; se evaluará reparación o baja' : 'Ej. Calibración anual'} />
          </Campo>
          <p className="text-xs text-pizarra">Para programar el trabajo con fecha y responsable usa «Mantenimientos».</p>
          <ErrorCaja mensaje={error} />
        </div>
      )}
    </Modal>
  )
}
