import { useEffect, useState } from 'react'
import { Save } from 'lucide-react'
import { api } from '../../api/index.js'
import { useToast } from '../../context/ToastContext.jsx'
import { useCatalogos } from '../../lib/hooks.js'
import { Campo, ErrorCaja, Modal } from '../../components/ui.jsx'

/** Información operativa de un bien (coordinador y monitor): ubicación, estado físico y observaciones. Queda en la hoja de vida. */
export default function OperativoModal({ equipo, onCerrar, onHecho }) {
  const toast = useToast()
  const catalogos = useCatalogos()
  const [f, setF] = useState({ ubicacion: '', estadoFisico: '', observaciones: '' })
  const [err, setErr] = useState('')
  const [enviando, setEnviando] = useState(false)
  useEffect(() => {
    if (!equipo) return
    setErr('')
    setF({ ubicacion: equipo.ubicacion ?? '', estadoFisico: equipo.estadoFisico ?? '', observaciones: equipo.observaciones ?? '' })
  }, [equipo])
  const fisicos = catalogos.ESTADO_FISICO ?? []
  const opciones = f.estadoFisico && !fisicos.includes(f.estadoFisico) ? [f.estadoFisico, ...fisicos] : fisicos
  const set = (k) => (e) => setF((x) => ({ ...x, [k]: e.target.value }))
  const guardar = async (e) => {
    e.preventDefault()
    setEnviando(true); setErr('')
    try {
      await api.operativo(equipo.placa, f)
      toast.exito('Información operativa actualizada', `${equipo.placa} · queda en la hoja de vida`)
      onHecho?.(); onCerrar()
    } catch (er) { setErr(er.message) } finally { setEnviando(false) }
  }
  return (
    <Modal abierto={!!equipo} onCerrar={onCerrar} titulo="Actualizar información operativa" subtitulo={equipo ? `${equipo.placa} · ${equipo.nombre}` : undefined}
      pie={<>
        <button type="button" className="btn-outline" onClick={onCerrar}>Cancelar</button>
        <button type="submit" form="form-op" className="btn-primary" disabled={enviando}><Save className="h-4 w-4" aria-hidden="true" /> {enviando ? 'Guardando…' : 'Guardar'}</button>
      </>}>
      <form id="form-op" onSubmit={guardar} noValidate className="space-y-4">
        <Campo etiqueta="Ubicación física" htmlFor="op-ubi"><input id="op-ubi" className="input" value={f.ubicacion} onChange={set('ubicacion')} maxLength={160} placeholder="Bloque · Laboratorio · Mesa/estante" /></Campo>
        <Campo etiqueta="Estado físico" htmlFor="op-fis">
          <select id="op-fis" className="input" value={f.estadoFisico} onChange={set('estadoFisico')}>{opciones.map((x) => <option key={x}>{x}</option>)}</select>
        </Campo>
        <Campo etiqueta="Observaciones" htmlFor="op-obs" ayuda="Para fallas o daños usa «Novedad»: así el coordinador la valida y queda la trazabilidad.">
          <textarea id="op-obs" rows={3} className="input" value={f.observaciones} onChange={set('observaciones')} maxLength={1000} />
        </Campo>
        <ErrorCaja mensaje={err} />
      </form>
    </Modal>
  )
}
