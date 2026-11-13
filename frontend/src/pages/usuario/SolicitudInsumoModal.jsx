import { useEffect, useState } from 'react'
import { Send } from 'lucide-react'
import { api } from '../../api/index.js'
import { useToast } from '../../context/ToastContext.jsx'
import { convertir, fmtCantidad, unidad as uni, unidadesCompatibles } from '../../lib/constantes.js'
import { Campo, ErrorCaja, Modal } from '../../components/ui.jsx'
import { Existencias } from '../../components/Existencias.jsx'

/**
 * Pedir una cantidad de un insumo. Se puede pedir en cualquier unidad compatible (mL de un insumo guardado en litros,
 * gramos de uno en kilos…). El auxiliar aprueba o rechaza la solicitud en ventanilla.
 */
export default function SolicitudInsumoModal({ insumo, onCerrar, onCreada }) {
  const toast = useToast()
  const [cantidad, setCantidad] = useState('')
  const [unidad, setUnidad] = useState('UNIDAD')
  const [motivo, setMotivo] = useState('')
  const [asignatura, setAsignatura] = useState('')
  const [errores, setErrores] = useState({})
  const [errorSrv, setErrorSrv] = useState('')
  const [enviando, setEnviando] = useState(false)

  useEffect(() => {
    if (!insumo) return
    setCantidad(''); setUnidad(insumo.unidad); setMotivo(''); setAsignatura(''); setErrores({}); setErrorSrv('')
  }, [insumo])

  const n = Number(String(cantidad).replace(',', '.'))
  const valido = cantidad !== '' && !Number.isNaN(n) && n > 0
  const equivalente = insumo && valido ? convertir(n, unidad, insumo.unidad) : null

  const enviar = async (e) => {
    e.preventDefault()
    const er = {}
    if (!valido) er.cantidad = 'Indica cuánto necesitas (mayor que cero)'
    else if (unidad === 'UNIDAD' && !Number.isInteger(n)) er.cantidad = 'En unidades usa un número entero (1, 2, 3…)'
    else if (equivalente > Number(insumo.cantidad)) er.cantidad = `No hay suficiente: quedan ${fmtCantidad(insumo.cantidad, insumo.unidad)}`
    if (motivo.trim().length < 5) er.motivo = 'Cuéntale al auxiliar para qué lo necesitas (mínimo 5 caracteres)'
    setErrores(er)
    if (Object.keys(er).length) return
    setEnviando(true); setErrorSrv('')
    try {
      const s = await api.solicitarInsumo(insumo.placa, { cantidad: n, unidad, motivo: motivo.trim(), asignatura: asignatura.trim() || null })
      toast.exito('Solicitud enviada', `${s.codigo} · ${fmtCantidad(n, unidad)} de ${insumo.nombre}. Te avisamos en «Mis reservas» cuando la atiendan.`)
      onCreada?.(); onCerrar()
    } catch (err) { setErrorSrv(err.message) } finally { setEnviando(false) }
  }

  return (
    <Modal abierto={!!insumo} onCerrar={onCerrar} titulo="Solicitar insumo" subtitulo={insumo ? `${insumo.placa} · ${insumo.nombre}` : undefined} ancho="max-w-lg"
      pie={<>
        <button type="button" className="btn-outline" onClick={onCerrar}>Cancelar</button>
        <button type="submit" form="form-insumo" className="btn-primary" disabled={enviando}><Send className="h-4 w-4" aria-hidden="true" /> {enviando ? 'Enviando…' : 'Enviar solicitud'}</button>
      </>}>
      {insumo && (
        <form id="form-insumo" onSubmit={enviar} noValidate className="space-y-4">
          <div className="rounded-lg bg-slate-50 p-3">
            <p className="mb-1 text-xs font-bold uppercase tracking-wide text-pizarra">Disponible ahora · {insumo.laboratorio}</p>
            <Existencias equipo={insumo} />
          </div>
          <div className="grid gap-3 sm:grid-cols-[1fr_180px]">
            <Campo etiqueta="Cantidad" htmlFor="si-cant" error={errores.cantidad}
              ayuda={equivalente !== null && unidad !== insumo.unidad ? `Equivale a ${fmtCantidad(equivalente, insumo.unidad)}` : undefined}>
              <input id="si-cant" type="number" inputMode="decimal" min="0" step={unidad === 'UNIDAD' ? 1 : 'any'} className={`input ${errores.cantidad ? 'input-error' : ''}`}
                value={cantidad} onChange={(e) => setCantidad(e.target.value)} placeholder={unidad === 'UNIDAD' ? 'Ej. 2' : 'Ej. 250'} />
            </Campo>
            <Campo etiqueta="Unidad" htmlFor="si-uni">
              <select id="si-uni" className="input" value={unidad} onChange={(e) => setUnidad(e.target.value)}>
                {unidadesCompatibles(insumo.unidad).map((u) => <option key={u.valor} value={u.valor}>{u.texto}</option>)}
              </select>
            </Campo>
          </div>
          <Campo etiqueta="¿Para qué lo necesitas?" htmlFor="si-motivo" error={errores.motivo}>
            <textarea id="si-motivo" rows={3} className={`input ${errores.motivo ? 'input-error' : ''}`} value={motivo} onChange={(e) => setMotivo(e.target.value)} maxLength={300}
              placeholder="Ej. Práctica 4: limpieza de tarjetas de circuito" />
          </Campo>
          <Campo etiqueta="Asignatura (opcional)" htmlFor="si-asig"><input id="si-asig" className="input" value={asignatura} onChange={(e) => setAsignatura(e.target.value)} maxLength={120} /></Campo>
          <p className="text-xs text-pizarra">El insumo se mide en {uni(insumo.unidad).texto.toLowerCase()}. El auxiliar revisará tu solicitud en ventanilla.</p>
          <ErrorCaja mensaje={errorSrv} />
        </form>
      )}
    </Modal>
  )
}
