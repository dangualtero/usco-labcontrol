import { useEffect, useState } from 'react'
import { ArrowDownLeft, Gavel, Info } from 'lucide-react'
import { api } from '../../api/index.js'
import { useToast } from '../../context/ToastContext.jsx'
import { fmtFechaHora, fmtVencimiento } from '../../lib/fecha.js'
import { Campo, Confirmar, ErrorCaja, Modal } from '../../components/ui.jsx'

const VACIO = { buenEstado: false, danos: false, accesoriosIncompletos: false, danosMenoresPreexistentes: false }

function Casilla({ id, checked, onChange, titulo, detalle, tono = '' }) {
  return (
    <label htmlFor={id} className={`flex min-h-[52px] cursor-pointer items-start gap-3 rounded-lg border-2 px-3 py-2.5 transition-colors ${checked ? `${tono || 'border-entrada bg-emerald-50'}` : 'border-slate-200 bg-white hover:border-slate-300'}`}>
      <input id={id} type="checkbox" checked={checked} onChange={(e) => onChange(e.target.checked)} className="mt-1 h-5 w-5 shrink-0 accent-[#059669]" />
      <span><span className="block text-sm font-bold text-slate-900">{titulo}</span><span className="text-xs text-pizarra">{detalle}</span></span>
    </label>
  )
}

/** Registrar Entrada / devolución (PF-09, PF-10, PS-07). Botón primario en VERDE ESMERALDA (PA-07). */
export default function EntradaModal({ abierto, activos, inicial, onCerrar, onHecho }) {
  const toast = useToast()
  const [id, setId] = useState('')
  const [f, setF] = useState(VACIO)
  const [obs, setObs] = useState('')
  const [errores, setErrores] = useState({})
  const [enviando, setEnviando] = useState(false)
  const [descartar, setDescartar] = useState(false)

  useEffect(() => {
    if (!abierto) return
    setId(inicial ? String(inicial.id) : ''); setF(VACIO); setObs(''); setErrores({}); setDescartar(false)
  }, [abierto, inicial])

  const p = (activos ?? []).find((x) => String(x.id) === id)
  const problema = f.danos || f.accesoriosIncompletos
  const sucio = !!(f.buenEstado || problema || f.danosMenoresPreexistentes || obs.trim())
  const cerrar = () => (sucio && !enviando ? setDescartar(true) : onCerrar())

  const marcar = (k, v) => setF((x) => {
    const n = { ...x, [k]: v }
    if (k === 'buenEstado' && v) { n.danos = false; n.accesoriosIncompletos = false }
    if ((k === 'danos' || k === 'accesoriosIncompletos') && v) n.buenEstado = false
    return n
  })

  const enviar = async () => {
    const e = {}
    if (!p) e.prestamo = 'Selecciona el préstamo activo que se devuelve'
    else if (!f.buenEstado && !problema && !f.danosMenoresPreexistentes) e.estado = 'Selecciona el estado físico del equipo'
    else if ((problema || f.danosMenoresPreexistentes) && !obs.trim()) e.obs = 'Describe la novedad en las observaciones' // PF-10: redactar nota
    setErrores(e)
    if (Object.keys(e).length) return
    setEnviando(true)
    try {
      const r = await api.entrada({ prestamoId: p.id, ...f, observaciones: obs })
      if (r.equipo.estado === 'MANTENIMIENTO') toast.error(`En mantenimiento: ${r.incidencia} caso abierto`, `${p.placa} · ${p.equipoNombre}`)
      else if (r.incidencia) toast.info(`Devolución registrada con novedad ${r.incidencia}`, `${p.placa} · ${p.equipoNombre}`)
      else toast.exito('Devolución registrada · Equipo Disponible', `${p.placa} · ${p.equipoNombre} · custodia liberada`)
      onHecho?.(); onCerrar()
    } catch (err) { setErrores({ global: err.message }) } finally { setEnviando(false) }
  }

  return (
    <>
      <Modal abierto={abierto} onCerrar={cerrar} titulo="Registrar Entrada" subtitulo="Recepción y verificación del equipo devuelto" acento="bg-entrada" ancho="max-w-xl"
        pie={<>
          <button className="btn-outline" onClick={cerrar}>Cancelar</button>
          <button className="btn-entrada" onClick={enviar} disabled={enviando}><ArrowDownLeft className="h-4 w-4" aria-hidden="true" /> {enviando ? 'Registrando…' : 'Registrar Entrada'}</button>
        </>}>
        <div className="space-y-4">
          <Campo etiqueta="Préstamo que se devuelve" htmlFor="e-prestamo" error={errores.prestamo}>
            <select id="e-prestamo" className={`input ${errores.prestamo ? 'input-error' : ''}`} value={id} onChange={(e) => setId(e.target.value)}>
              <option value="">Selecciona un equipo en préstamo…</option>
              {(activos ?? []).map((x) => <option key={x.id} value={x.id}>{x.placa} · {x.equipoNombre} — {x.solicitanteNombre}{x.vencido ? ' (VENCIDO)' : ''}</option>)}
            </select>
          </Campo>

          {p && (
            <div className={`rounded-lg px-3 py-2.5 text-sm ring-1 aparecer ${p.vencido ? 'bg-alerta-fondo ring-alerta-borde' : 'bg-slate-50 ring-slate-200'}`}>
              <p className="font-bold">{p.equipoNombre} <span className="font-mono text-[13px] font-semibold text-pizarra">({p.placa})</span></p>
              <p className="text-pizarra">Responsable: <strong className="text-slate-800">{p.solicitanteNombre}</strong> · Salida {fmtFechaHora(p.fechaSalida)}</p>
              <p className={`font-semibold ${p.vencido ? 'text-alerta-texto' : 'text-slate-700'}`}>Límite {fmtFechaHora(p.fechaLimite)} · {fmtVencimiento(p.fechaLimite)}</p>
            </div>
          )}

          <fieldset>
            <legend className="label">Estado físico del equipo</legend>
            <div className="grid gap-2 sm:grid-cols-2">
              <Casilla id="c-ok" checked={f.buenEstado} onChange={(v) => marcar('buenEstado', v)} titulo="Buen estado" detalle="Sin novedades al recibirlo" />
              <Casilla id="c-dan" checked={f.danos} onChange={(v) => marcar('danos', v)} titulo="Presenta daños" detalle="Pasa a mantenimiento y abre incidencia" tono="border-alerta-borde bg-alerta-fondo" />
              <Casilla id="c-acc" checked={f.accesoriosIncompletos} onChange={(v) => marcar('accesoriosIncompletos', v)} titulo="Accesorios incompletos" detalle="Faltan cables, puntas, estuche, etc." tono="border-amber-500 bg-amber-50" />
              <Casilla id="c-pre" checked={f.danosMenoresPreexistentes} onChange={(v) => marcar('danosMenoresPreexistentes', v)} titulo="Daños menores preexistentes" detalle="Ya existían; no se atribuyen al usuario" tono="border-pizarra bg-slate-50" />
            </div>
            {errores.estado && <p role="alert" className="mt-1.5 text-sm font-semibold text-alerta-borde">{errores.estado}</p>}
          </fieldset>

          {/* PS-07: advertencia disciplinaria cuando se registra responsabilidad del usuario */}
          {problema && (
            <div role="alert" className="flex gap-3 rounded-lg border border-alerta-borde bg-alerta-fondo p-3 aparecer">
              <Gavel className="mt-0.5 h-5 w-5 shrink-0 text-alerta-borde" aria-hidden="true" />
              <div className="text-sm text-alerta-texto">
                <p className="font-extrabold">Advertencia disciplinaria</p>
                <p>Registrar daños o accesorios faltantes lo asocia al usuario responsable ({p?.solicitanteNombre ?? 'seleccionado'}) y puede dar lugar a un proceso disciplinario según el reglamento estudiantil. Verifica el estado del equipo antes de confirmar.</p>
              </div>
            </div>
          )}
          {f.danosMenoresPreexistentes && !problema && (
            <p className="flex items-start gap-2 rounded-lg bg-slate-50 px-3 py-2 text-xs font-semibold text-pizarra-oscuro ring-1 ring-slate-200"><Info className="mt-0.5 h-4 w-4 shrink-0" aria-hidden="true" /> Se deja constancia del daño menor sin atribuirlo a este usuario.</p>
          )}

          <Campo etiqueta={`Observaciones${problema || f.danosMenoresPreexistentes ? ' (obligatorio)' : ' (opcional)'}`} htmlFor="e-obs" error={errores.obs}>
            <textarea id="e-obs" rows={3} className={`input ${errores.obs ? 'input-error' : ''}`} value={obs} onChange={(e) => setObs(e.target.value)} maxLength={400} placeholder="Describe el daño, los accesorios faltantes o cualquier novedad…" />
          </Campo>
          <ErrorCaja mensaje={errores.global} />
        </div>
      </Modal>
      <Confirmar abierto={descartar} titulo="¿Cancelar la devolución?" mensaje="Los datos no guardados se perderán" textoOk="Sí, cancelar" textoCancelar="Seguir editando"
        onCancelar={() => setDescartar(false)} onOk={() => { setDescartar(false); onCerrar() }} />
    </>
  )
}
