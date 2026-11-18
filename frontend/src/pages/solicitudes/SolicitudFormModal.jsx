import { useEffect, useState } from 'react'
import { Save } from 'lucide-react'
import { api } from '../../api/index.js'
import { useToast } from '../../context/ToastContext.jsx'
import { useCatalogos, useLabsRef } from '../../lib/hooks.js'
import { PRIORIDADES } from '../../lib/constantes.js'
import { Campo, ErrorCaja, Modal } from '../../components/ui.jsx'

const VACIO = { laboratorioId: '', tipo: '', titulo: '', justificacion: '', especificaciones: '', cantidad: '', unidadMedida: '', prioridadPropuesta: 'MEDIA', valorEstimado: '' }

/**
 * Crear o editar una solicitud: justificación, especificaciones técnicas, prioridad propuesta y valor estimado.
 * Las cotizaciones, fotografías y soportes se adjuntan en el detalle. Se puede guardar incompleta como borrador.
 */
export default function SolicitudFormModal({ abierto, solicitud, onCerrar, onHecho }) {
  const toast = useToast()
  const labs = useLabsRef([abierto])
  const catalogos = useCatalogos()
  const tipos = catalogos.TIPO_SOLICITUD ?? []
  const nueva = !solicitud
  const [f, setF] = useState(VACIO)
  const [err, setErr] = useState({})
  const [errSrv, setErrSrv] = useState('')
  const [enviando, setEnviando] = useState(false)

  useEffect(() => {
    if (!abierto) return
    setErr({}); setErrSrv('')
    if (solicitud) {
      const r = solicitud.resumen
      setF({ laboratorioId: r.laboratorioId, tipo: r.tipo ?? '', titulo: r.titulo ?? '', justificacion: solicitud.justificacion ?? '',
        especificaciones: solicitud.especificaciones ?? '', cantidad: solicitud.cantidad ?? '', unidadMedida: solicitud.unidadMedida ?? '',
        prioridadPropuesta: r.prioridadPropuesta ?? 'MEDIA', valorEstimado: r.valorEstimado ?? '' })
    } else setF(VACIO)
  }, [abierto, solicitud])
  useEffect(() => { if (abierto && !f.laboratorioId && labs.length === 1) setF((x) => ({ ...x, laboratorioId: labs[0].id })) }, [abierto, labs, f.laboratorioId])
  useEffect(() => { if (abierto && !f.tipo && tipos.length) setF((x) => ({ ...x, tipo: tipos[0] })) }, [abierto, tipos, f.tipo])

  const set = (k) => (e) => setF((x) => ({ ...x, [k]: e.target.value }))
  const guardar = async (e) => {
    e.preventDefault()
    const er = {}
    if (!f.laboratorioId) er.laboratorioId = 'Selecciona el laboratorio'
    if (f.titulo.trim().length < 5) er.titulo = 'Escribe un título (mínimo 5 caracteres)'
    setErr(er)
    if (Object.keys(er).length) return
    setEnviando(true); setErrSrv('')
    const num = (v) => (v === '' || v === null ? null : Number(String(v).replace(',', '.')))
    const cuerpo = { ...f, laboratorioId: Number(f.laboratorioId), titulo: f.titulo.trim(), cantidad: num(f.cantidad), valorEstimado: num(f.valorEstimado) }
    try {
      const r = nueva ? await api.crearSolicitud(cuerpo) : await api.actualizarSolicitud(solicitud.resumen.id, cuerpo)
      toast.exito(nueva ? 'Solicitud creada como borrador' : 'Solicitud actualizada', `${r.resumen.codigo} · ${r.resumen.titulo}`)
      onHecho?.(r); onCerrar()
    } catch (er2) { setErrSrv(er2.message) } finally { setEnviando(false) }
  }

  return (
    <Modal abierto={abierto} onCerrar={onCerrar} ancho="max-w-3xl" titulo={nueva ? 'Nueva solicitud o requerimiento' : `Editar ${solicitud?.resumen.codigo}`}
      subtitulo="Puedes guardarla incompleta: queda como borrador hasta que la envíes."
      pie={<>
        <button type="button" className="btn-outline" onClick={onCerrar}>Cancelar</button>
        <button type="submit" form="form-sol" className="btn-primary" disabled={enviando}><Save className="h-4 w-4" aria-hidden="true" /> {enviando ? 'Guardando…' : 'Guardar'}</button>
      </>}>
      <form id="form-sol" onSubmit={guardar} noValidate className="grid gap-4 md:grid-cols-2">
        <Campo etiqueta="Laboratorio" htmlFor="s-lab" error={err.laboratorioId}>
          <select id="s-lab" className={`input ${err.laboratorioId ? 'input-error' : ''}`} value={f.laboratorioId} onChange={set('laboratorioId')}>
            <option value="">Selecciona…</option>{labs.map((l) => <option key={l.id} value={l.id}>{l.nombre}</option>)}
          </select>
        </Campo>
        <Campo etiqueta="Tipo de solicitud" htmlFor="s-tipo">
          <select id="s-tipo" className="input" value={f.tipo} onChange={set('tipo')}>{(f.tipo && !tipos.includes(f.tipo) ? [f.tipo, ...tipos] : tipos).map((t) => <option key={t}>{t}</option>)}</select>
        </Campo>
        <div className="md:col-span-2"><Campo etiqueta="Título" htmlFor="s-tit" error={err.titulo}><input id="s-tit" className={`input ${err.titulo ? 'input-error' : ''}`} value={f.titulo} onChange={set('titulo')} maxLength={160} placeholder="Ej. Dos osciloscopios de 100 MHz para Circuitos II" /></Campo></div>
        <div className="md:col-span-2"><Campo etiqueta="Justificación" htmlFor="s-just" ayuda="Necesidad, población beneficiada y consecuencias de no atenderla (mínimo 20 caracteres para enviarla al Administrador)">
          <textarea id="s-just" rows={4} className="input" value={f.justificacion} onChange={set('justificacion')} maxLength={4000} /></Campo></div>
        <div className="md:col-span-2"><Campo etiqueta="Especificaciones técnicas" htmlFor="s-esp" ayuda="Características mínimas, normas, garantía, capacitación…">
          <textarea id="s-esp" rows={4} className="input" value={f.especificaciones} onChange={set('especificaciones')} maxLength={4000} /></Campo></div>
        <div className="grid gap-4 sm:grid-cols-2 md:col-span-2 md:grid-cols-4">
          <Campo etiqueta="Cantidad" htmlFor="s-cant"><input id="s-cant" type="number" min="0" step="any" className="input" value={f.cantidad} onChange={set('cantidad')} /></Campo>
          <Campo etiqueta="Unidad" htmlFor="s-uni"><input id="s-uni" className="input" value={f.unidadMedida} onChange={set('unidadMedida')} maxLength={30} placeholder="unidades, kg, L…" /></Campo>
          <Campo etiqueta="Prioridad propuesta" htmlFor="s-pri"><select id="s-pri" className="input" value={f.prioridadPropuesta} onChange={set('prioridadPropuesta')}>{PRIORIDADES.map((p) => <option key={p}>{p}</option>)}</select></Campo>
          <Campo etiqueta="Valor estimado (COP)" htmlFor="s-val"><input id="s-val" type="number" min="0" step="1000" className="input" value={f.valorEstimado} onChange={set('valorEstimado')} /></Campo>
        </div>
        <div className="md:col-span-2"><ErrorCaja mensaje={errSrv} /></div>
      </form>
    </Modal>
  )
}
