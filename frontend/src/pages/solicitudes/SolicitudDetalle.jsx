import { Fragment, useEffect, useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import {
  ArrowLeft, Ban, CircleCheck, CircleX, FileSignature, ListOrdered, Lock, Paperclip, Pencil, Send, TrendingUp, Undo2, Wallet,
} from 'lucide-react'
import { api } from '../../api/index.js'
import { useSede } from '../../context/SedeContext.jsx'
import { useToast } from '../../context/ToastContext.jsx'
import { useCatalogos, useDatos } from '../../lib/hooks.js'
import { CATEGORIAS_PRESUPUESTO, PRIORIDADES, fmtCantidad, pesos } from '../../lib/constantes.js'
import { fmtFecha, fmtFechaHora, toDia } from '../../lib/fecha.js'
import { Campo, Cargando, ErrorCaja, Modal } from '../../components/ui.jsx'
import { ACEPTA, ArchivoChip, ListaArchivos, SelectorArchivos } from '../../components/Archivos.jsx'
import { ESTADOS_CONTRATO, Encabezado, EstadoSolicitud, Prioridad, Verificacion } from '../../components/Estados.jsx'
import SolicitudFormModal from './SolicitudFormModal.jsx'

const TIPOS_ADJUNTO = [['COTIZACION', 'Cotización'], ['FOTOGRAFIA', 'Fotografía'], ['SOPORTE', 'Soporte'], ['CONTRATO', 'Contrato / documento contractual']]
const ACCION = {
  CREADA: 'Creó la solicitud', EDITADA: 'Editó el contenido', ADJUNTO: 'Adjuntó', ADJUNTO_RETIRADO: 'Retiró un adjunto', EN_REVISION: 'La envió al coordinador',
  ENVIADA: 'La envió al Administrador', DEVUELTA: 'La devolvió para ajuste', PRIORIZADA: 'Priorizó', APROBADA: 'Aprobó', RECHAZADA: 'Rechazó', CERRADA: 'Cerró',
  CANCELADA: 'Canceló', PRESUPUESTO: 'Registró información presupuestal', CONTRATACION: 'Registró información contractual', GASTO: 'Registró el gasto',
}

/** Detalle de una solicitud con su flujo: acciones según el rol y el estado, adjuntos, presupuesto, avances y trazabilidad. */
export default function SolicitudDetalle() {
  const { id } = useParams()
  const { ruta } = useSede()
  const toast = useToast()
  const { datos: s, error, recargar } = useDatos(() => api.solicitud(id), [id], { cada: 0 })
  const [modal, setModal] = useState(null)
  const cerrar = () => setModal(null)
  const hecho = () => { cerrar(); recargar() }

  if (error) return <div className="space-y-4"><Link to={ruta('/solicitudes')} className="btn-outline"><ArrowLeft className="h-4 w-4" aria-hidden="true" /> Solicitudes</Link><ErrorCaja mensaje={error} /></div>
  if (!s) return <Cargando />
  const r = s.resumen
  const a = s.acciones
  const quitarAdjunto = async (adj) => {
    try { await api.quitarAdjunto(r.id, adj.id); toast.exito('Adjunto retirado', adj.archivo.nombre); recargar() } catch (e) { toast.error('No se pudo retirar', e.message) }
  }

  return (
    <div className="space-y-6">
      <Link to={ruta('/solicitudes')} className="inline-flex items-center gap-1 text-sm font-bold text-usco hover:underline"><ArrowLeft className="h-4 w-4" aria-hidden="true" /> Solicitudes</Link>
      <Encabezado modulo={`${r.codigo} · ${r.laboratorio}`} titulo={r.titulo} detalle={`${r.tipo ?? 'Sin tipo'} · Creó ${r.creadoPor} (${r.creadoPorRol}) el ${fmtFechaHora(r.creadoEn)}`} />

      <div className="flex flex-wrap items-center gap-2">
        <EstadoSolicitud estado={r.estado} />
        <Prioridad valor={r.prioridadPropuesta} prefijo="Propuesta " />
        {r.prioridadAsignada && <Prioridad valor={r.prioridadAsignada} prefijo="Asignada " />}
        {r.ordenPrioridad && <span className="rounded-full border border-slate-300 px-2 py-0.5 text-[11px] font-extrabold">Orden #{r.ordenPrioridad}</span>}
      </div>

      {r.estado === 'DEVUELTA' && s.observacionDevolucion && <p role="alert" className="rounded-lg border border-amber-500 bg-amber-50 p-3 text-sm text-amber-900"><strong>Devuelta para ajuste:</strong> {s.observacionDevolucion}</p>}
      {s.decisionMotivo && <p className="rounded-lg border border-slate-200 bg-slate-50 p-3 text-sm"><strong>Decisión ({s.decididoPor} · {fmtFechaHora(s.decididoEn)}):</strong> {s.decisionMotivo}</p>}
      {s.cierreNota && <p className="rounded-lg border border-slate-200 bg-slate-50 p-3 text-sm"><strong>Cierre ({fmtFechaHora(s.cerradoEn)}):</strong> {s.cierreNota}</p>}

      {/* Barra de acciones: solo lo que esta cuenta puede hacer en este estado */}
      {Object.values(a).some((v) => v === true) && (
        <section className="card flex flex-wrap gap-2 p-3" aria-label="Acciones">
          {a.editar && <button className="btn-outline btn-sm" onClick={() => setModal('editar')}><Pencil className="h-4 w-4" aria-hidden="true" /> Editar</button>}
          {a.adjuntar && <button className="btn-outline btn-sm" onClick={() => setModal('adjuntar')}><Paperclip className="h-4 w-4" aria-hidden="true" /> Adjuntar</button>}
          {a.enviar && <button className="btn-primary btn-sm" onClick={() => setModal('enviar')}><Send className="h-4 w-4" aria-hidden="true" /> {a.enviarTexto}</button>}
          {a.devolver && <button className="btn-outline btn-sm" onClick={() => setModal('devolver')}><Undo2 className="h-4 w-4" aria-hidden="true" /> Devolver para ajuste</button>}
          {a.priorizar && <button className="btn-outline btn-sm" onClick={() => setModal('priorizar')}><ListOrdered className="h-4 w-4" aria-hidden="true" /> Priorizar</button>}
          {a.decidir && <button className="btn-entrada btn-sm" onClick={() => setModal('aprobar')}><CircleCheck className="h-4 w-4" aria-hidden="true" /> Aprobar</button>}
          {a.decidir && <button className="btn-outline btn-sm" onClick={() => setModal('rechazar')}><CircleX className="h-4 w-4" aria-hidden="true" /> Rechazar</button>}
          {a.presupuesto && <button className="btn-outline btn-sm" onClick={() => setModal('presupuesto')}><Wallet className="h-4 w-4" aria-hidden="true" /> Presupuesto</button>}
          {a.presupuesto && <button className="btn-outline btn-sm" onClick={() => setModal('contrato')}><FileSignature className="h-4 w-4" aria-hidden="true" /> Contratación</button>}
          {a.registrarAvance && <button className="btn-outline btn-sm" onClick={() => setModal('avance')}><TrendingUp className="h-4 w-4" aria-hidden="true" /> Registrar avance</button>}
          {a.cerrar && <button className="btn-outline btn-sm" onClick={() => setModal('cerrar')}><Lock className="h-4 w-4" aria-hidden="true" /> Cerrar</button>}
          {a.cancelar && <button className="btn-outline btn-sm !text-alerta-texto" onClick={() => setModal('cancelar')}><Ban className="h-4 w-4" aria-hidden="true" /> Cancelar</button>}
        </section>
      )}

      <div className="grid gap-5 xl:grid-cols-[1fr_380px]">
        <div className="space-y-5">
          <section className="card space-y-4 p-5" aria-label="Contenido">
            <div className="grid gap-3 sm:grid-cols-3">
              <div><p className="text-xs font-bold uppercase tracking-wide text-pizarra">Cantidad</p><p className="font-semibold">{s.cantidad != null ? `${fmtCantidad(s.cantidad, 'UNIDAD').replace(' und', '')} ${s.unidadMedida ?? ''}` : '—'}</p></div>
              <div><p className="text-xs font-bold uppercase tracking-wide text-pizarra">Valor estimado</p><p className="font-semibold">{r.valorEstimado != null ? pesos(r.valorEstimado) : '—'}</p></div>
              <div><p className="text-xs font-bold uppercase tracking-wide text-pizarra">Enviada</p><p className="font-semibold">{r.enviadoEn ? fmtFechaHora(r.enviadoEn) : '—'}</p></div>
            </div>
            <div><p className="text-xs font-bold uppercase tracking-wide text-pizarra">Justificación</p><p className="whitespace-pre-line text-sm text-slate-800">{s.justificacion || <span className="text-slate-400">Sin justificación</span>}</p></div>
            <div><p className="text-xs font-bold uppercase tracking-wide text-pizarra">Especificaciones técnicas</p><p className="whitespace-pre-line text-sm text-slate-800">{s.especificaciones || <span className="text-slate-400">Sin especificaciones</span>}</p></div>
          </section>

          <section className="card p-5" aria-labelledby="h-adj">
            <h2 id="h-adj" className="mb-3 font-extrabold">Cotizaciones, fotografías y soportes ({s.adjuntos.length})</h2>
            {s.adjuntos.length === 0 ? <p className="text-sm text-pizarra">Sin adjuntos.</p> : (
              <ul className="space-y-1.5">
                {s.adjuntos.map((x) => (
                  <ArchivoChip key={x.id} archivo={x.archivo} etiqueta={x.tipoTexto}
                    onQuitar={(x.tipo === 'CONTRATO' ? a.presupuesto : a.editar) ? () => quitarAdjunto(x) : undefined} />
                ))}
              </ul>
            )}
          </section>

          <section className="card p-5" aria-labelledby="h-av">
            <h2 id="h-av" className="mb-3 font-extrabold">Avances y evidencias ({s.avances.length})</h2>
            {s.avances.length === 0 ? <p className="text-sm text-pizarra">Sin avances registrados.</p> : (
              <ul className="space-y-3">
                {s.avances.map((v) => (
                  <li key={v.id} className="rounded-lg border border-slate-200 p-3 text-sm">
                    <div className="flex flex-wrap items-center justify-between gap-2"><span><strong>{v.autor}</strong> · {fmtFechaHora(v.creadoEn)}{v.porcentaje != null ? ` · ${v.porcentaje} %` : ''}</span><Verificacion estado={v.verificacion} /></div>
                    <p className="mt-1">{v.descripcion}</p>
                    {v.observacionVerificacion && <p className="mt-1 text-xs text-pizarra">Verificación: {v.observacionVerificacion}</p>}
                    {v.evidencias.length > 0 && <div className="mt-2"><ListaArchivos archivos={v.evidencias} /></div>}
                  </li>
                ))}
              </ul>
            )}
          </section>
        </div>

        <aside className="space-y-5">
          {s.presupuesto && (
            <section className="card p-4 text-sm" aria-labelledby="h-pres">
              <h2 id="h-pres" className="mb-2 flex items-center gap-2 font-extrabold"><Wallet className="h-4 w-4 text-usco" aria-hidden="true" /> Presupuesto y contratación</h2>
              <dl className="grid grid-cols-[auto_1fr] gap-x-3 gap-y-1">
                {[['Rubro', s.presupuesto.rubro], ['Fuente', s.presupuesto.fuenteFinanciacion], ['CDP', s.presupuesto.cdpNumero && `${s.presupuesto.cdpNumero}${s.presupuesto.cdpFecha ? ` · ${fmtFecha(s.presupuesto.cdpFecha)}` : ''}`],
                  ['Valor aprobado', s.presupuesto.valorAprobado != null && pesos(s.presupuesto.valorAprobado)], ['Estado', s.presupuesto.estadoContractualTexto],
                  ['Modalidad', s.presupuesto.modalidadContratacion], ['Contrato', s.presupuesto.contratoNumero], ['Contratista', s.presupuesto.contratista],
                  ['Valor contratado', s.presupuesto.valorContratado != null && pesos(s.presupuesto.valorContratado)], ['Fecha', s.presupuesto.fechaContrato && fmtFecha(s.presupuesto.fechaContrato)]].map(([t, v]) => (
                  <Fragment key={t}><dt className="font-semibold text-pizarra">{t}</dt><dd className="font-semibold">{v || '—'}</dd></Fragment>
                ))}
              </dl>
              {s.presupuesto.observacionContractual && <p className="mt-2 text-xs text-pizarra">{s.presupuesto.observacionContractual}</p>}
              {s.presupuesto.movimientos.length > 0 && <p className="mt-2 text-xs font-semibold text-entrada-dark">Gasto en el presupuesto de la sede: {s.presupuesto.movimientos.filter((m) => !m.anulado).map((m) => pesos(m.monto)).join(', ')}</p>}
            </section>
          )}
          <section className="card p-4" aria-labelledby="h-traz">
            <h2 id="h-traz" className="mb-3 font-extrabold">Trazabilidad</h2>
            <ol className="relative space-y-3 border-l-2 border-slate-200 pl-4">
              {s.eventos.map((e) => (
                <li key={e.id} className="relative text-sm">
                  <span className="absolute -left-[23px] top-1 h-3 w-3 rounded-full border-2 border-white bg-usco" aria-hidden="true" />
                  <p className="text-xs text-pizarra">{fmtFechaHora(e.fecha)}</p>
                  <p><strong>{e.autor}</strong> · {ACCION[e.accion] ?? e.accion}{e.estadoNuevo && e.estadoAnterior ? ` (${e.estadoAnterior} → ${e.estadoNuevo})` : ''}</p>
                  {e.comentario && <p className="text-xs text-slate-700">{e.comentario}</p>}
                </li>
              ))}
            </ol>
          </section>
        </aside>
      </div>

      <SolicitudFormModal abierto={modal === 'editar'} solicitud={s} onCerrar={cerrar} onHecho={recargar} />
      <TextoModal abierto={modal === 'enviar'} titulo={a.enviarTexto} etiqueta="Comentario (opcional)" minimo={0} boton="Enviar" onCerrar={cerrar} onHecho={hecho}
        accion={(t) => api.enviarSolicitud(r.id, t)} exito={(x) => x.resumen.estado === 'EN_REVISION' ? 'Enviada al coordinador para validación' : 'Enviada al Administrador'} />
      <TextoModal abierto={modal === 'devolver'} titulo="Devolver para ajuste" etiqueta="¿Qué se debe ajustar?" minimo={5} boton="Devolver" onCerrar={cerrar} onHecho={hecho}
        accion={(t) => api.devolverSolicitud(r.id, t)} exito={() => 'Solicitud devuelta'} />
      <TextoModal abierto={modal === 'aprobar'} titulo="Aprobar solicitud" etiqueta="Observación (opcional)" minimo={0} boton="Aprobar" onCerrar={cerrar} onHecho={hecho}
        accion={(t) => api.decidir(r.id, 'APROBADA', t)} exito={() => 'Solicitud aprobada'} />
      <TextoModal abierto={modal === 'rechazar'} titulo="Rechazar solicitud" etiqueta="Motivo del rechazo" minimo={5} boton="Rechazar" peligro onCerrar={cerrar} onHecho={hecho}
        accion={(t) => api.decidir(r.id, 'RECHAZADA', t)} exito={() => 'Solicitud rechazada'} />
      <TextoModal abierto={modal === 'cerrar'} titulo="Cerrar solicitud" etiqueta="Nota de cierre" minimo={5} boton="Cerrar solicitud" onCerrar={cerrar} onHecho={hecho}
        accion={(t) => api.cerrarSolicitud(r.id, t)} exito={() => 'Solicitud cerrada'} />
      <TextoModal abierto={modal === 'cancelar'} titulo="Cancelar solicitud" etiqueta="Motivo" minimo={5} boton="Cancelar solicitud" peligro onCerrar={cerrar} onHecho={hecho}
        accion={(t) => api.cancelarSolicitud(r.id, t)} exito={() => 'Solicitud cancelada'} />
      <PriorizarModal abierto={modal === 'priorizar'} s={s} onCerrar={cerrar} onHecho={hecho} />
      <AdjuntarModal abierto={modal === 'adjuntar'} s={s} onCerrar={cerrar} onHecho={hecho} />
      <PresupuestoModal abierto={modal === 'presupuesto'} s={s} onCerrar={cerrar} onHecho={hecho} />
      <ContratoModal abierto={modal === 'contrato'} s={s} onCerrar={cerrar} onHecho={hecho} />
      <AvanceModal abierto={modal === 'avance'} solicitud={r} onCerrar={cerrar} onHecho={hecho} />
    </div>
  )
}

function TextoModal({ abierto, titulo, etiqueta, minimo, boton, peligro, accion, exito, onCerrar, onHecho }) {
  const toast = useToast()
  const [t, setT] = useState('')
  const [err, setErr] = useState('')
  const [enviando, setEnviando] = useState(false)
  useEffect(() => { if (abierto) { setT(''); setErr('') } }, [abierto])
  const ok = async () => {
    if (t.trim().length < minimo) { setErr(`Escribe al menos ${minimo} caracteres`); return }
    setEnviando(true); setErr('')
    try { const x = await accion(t.trim() || null); toast.exito(exito(x), x.resumen?.codigo); onHecho() } catch (e) { setErr(e.message) } finally { setEnviando(false) }
  }
  return (
    <Modal abierto={abierto} onCerrar={onCerrar} titulo={titulo} ancho="max-w-md" acento={peligro ? 'bg-alerta-borde' : 'bg-usco'}
      pie={<><button className="btn-outline" onClick={onCerrar}>Volver</button><button className={peligro ? 'btn-danger' : 'btn-primary'} onClick={ok} disabled={enviando}>{enviando ? 'Procesando…' : boton}</button></>}>
      <Campo etiqueta={etiqueta} htmlFor="tm-t"><textarea id="tm-t" rows={4} className="input" value={t} onChange={(e) => setT(e.target.value)} maxLength={1000} /></Campo>
      <ErrorCaja mensaje={err} className="mt-3" />
    </Modal>
  )
}

function PriorizarModal({ abierto, s, onCerrar, onHecho }) {
  const toast = useToast()
  const [f, setF] = useState({ prioridad: 'MEDIA', orden: '', comentario: '' })
  const [err, setErr] = useState('')
  useEffect(() => { if (abierto) { setErr(''); setF({ prioridad: s.resumen.prioridadAsignada ?? s.resumen.prioridadPropuesta ?? 'MEDIA', orden: s.resumen.ordenPrioridad ?? '', comentario: '' }) } }, [abierto, s])
  const ok = async () => {
    try { await api.priorizar(s.resumen.id, { prioridad: f.prioridad, orden: f.orden === '' ? null : Number(f.orden), comentario: f.comentario || null }); toast.exito('Priorización registrada', s.resumen.codigo); onHecho() }
    catch (e) { setErr(e.message) }
  }
  return (
    <Modal abierto={abierto} onCerrar={onCerrar} titulo="Priorizar solicitud" ancho="max-w-md"
      pie={<><button className="btn-outline" onClick={onCerrar}>Volver</button><button className="btn-primary" onClick={ok}>Guardar</button></>}>
      <div className="space-y-3">
        <div className="grid grid-cols-2 gap-3">
          <Campo etiqueta="Prioridad asignada" htmlFor="pz-p"><select id="pz-p" className="input" value={f.prioridad} onChange={(e) => setF((x) => ({ ...x, prioridad: e.target.value }))}>{PRIORIDADES.map((p) => <option key={p}>{p}</option>)}</select></Campo>
          <Campo etiqueta="Orden (1 = primera)" htmlFor="pz-o"><input id="pz-o" type="number" min="1" className="input" value={f.orden} onChange={(e) => setF((x) => ({ ...x, orden: e.target.value }))} /></Campo>
        </div>
        <Campo etiqueta="Comentario (opcional)" htmlFor="pz-c"><textarea id="pz-c" rows={2} className="input" value={f.comentario} onChange={(e) => setF((x) => ({ ...x, comentario: e.target.value }))} maxLength={400} /></Campo>
        <ErrorCaja mensaje={err} />
      </div>
    </Modal>
  )
}

function AdjuntarModal({ abierto, s, onCerrar, onHecho }) {
  const toast = useToast()
  const [tipo, setTipo] = useState('COTIZACION')
  const [archivo, setArchivo] = useState(null)
  const [err, setErr] = useState('')
  const [enviando, setEnviando] = useState(false)
  const tipos = TIPOS_ADJUNTO.filter(([k]) => (k === 'CONTRATO' ? s.acciones.presupuesto : s.acciones.editar))
  useEffect(() => { if (abierto) { setErr(''); setArchivo(null); setTipo(tipos[0]?.[0] ?? 'SOPORTE') } /* eslint-disable-next-line react-hooks/exhaustive-deps */ }, [abierto])
  const ok = async () => {
    if (!archivo) { setErr('Selecciona el archivo'); return }
    setEnviando(true); setErr('')
    try { await api.adjuntar(s.resumen.id, tipo, archivo); toast.exito('Archivo adjuntado', archivo.name); onHecho() } catch (e) { setErr(e.message) } finally { setEnviando(false) }
  }
  return (
    <Modal abierto={abierto} onCerrar={onCerrar} titulo="Adjuntar archivo" ancho="max-w-md"
      pie={<><button className="btn-outline" onClick={onCerrar}>Volver</button><button className="btn-primary" onClick={ok} disabled={enviando}><Paperclip className="h-4 w-4" aria-hidden="true" /> {enviando ? 'Subiendo…' : 'Adjuntar'}</button></>}>
      <div className="space-y-3">
        <Campo etiqueta="Tipo" htmlFor="ad-t"><select id="ad-t" className="input" value={tipo} onChange={(e) => setTipo(e.target.value)}>{tipos.map(([k, t]) => <option key={k} value={k}>{t}</option>)}</select></Campo>
        <Campo etiqueta="Archivo" htmlFor="ad-a" ayuda="PDF, JPG, PNG, WEBP, Word o Excel · máximo 10 MB"><input id="ad-a" type="file" accept={ACEPTA} className="input py-2" onChange={(e) => setArchivo(e.target.files?.[0] ?? null)} /></Campo>
        <ErrorCaja mensaje={err} />
      </div>
    </Modal>
  )
}

function PresupuestoModal({ abierto, s, onCerrar, onHecho }) {
  const toast = useToast()
  const cat = useCatalogos()
  const [f, setF] = useState({})
  const [err, setErr] = useState('')
  useEffect(() => {
    if (!abierto) return
    const p = s.presupuesto ?? {}
    setErr(''); setF({ rubro: p.rubro ?? '', fuenteFinanciacion: p.fuenteFinanciacion ?? '', cdpNumero: p.cdpNumero ?? '', cdpFecha: p.cdpFecha ?? '', valorAprobado: p.valorAprobado ?? s.resumen.valorEstimado ?? '' })
  }, [abierto, s])
  const set = (k) => (e) => setF((x) => ({ ...x, [k]: e.target.value }))
  const ok = async () => {
    try {
      await api.presupuestoSolicitud(s.resumen.id, { ...f, cdpFecha: f.cdpFecha || null, valorAprobado: f.valorAprobado === '' ? null : Number(f.valorAprobado), fuenteFinanciacion: f.fuenteFinanciacion || null })
      toast.exito('Información presupuestal guardada', s.resumen.codigo); onHecho()
    } catch (e) { setErr(e.message) }
  }
  return (
    <Modal abierto={abierto} onCerrar={onCerrar} titulo="Información presupuestal" subtitulo={s.resumen.codigo}
      pie={<><button className="btn-outline" onClick={onCerrar}>Volver</button><button className="btn-primary" onClick={ok}>Guardar</button></>}>
      <div className="grid gap-3 sm:grid-cols-2">
        <Campo etiqueta="Rubro presupuestal" htmlFor="pr-r"><input id="pr-r" className="input" value={f.rubro ?? ''} onChange={set('rubro')} maxLength={120} /></Campo>
        <Campo etiqueta="Fuente de financiación" htmlFor="pr-f"><select id="pr-f" className="input" value={f.fuenteFinanciacion ?? ''} onChange={set('fuenteFinanciacion')}><option value="">—</option>{(cat.FUENTE_FINANCIACION ?? []).map((x) => <option key={x}>{x}</option>)}</select></Campo>
        <Campo etiqueta="Número de CDP" htmlFor="pr-c"><input id="pr-c" className="input" value={f.cdpNumero ?? ''} onChange={set('cdpNumero')} maxLength={40} /></Campo>
        <Campo etiqueta="Fecha del CDP" htmlFor="pr-fc"><input id="pr-fc" type="date" className="input" value={f.cdpFecha ?? ''} onChange={set('cdpFecha')} /></Campo>
        <Campo etiqueta="Valor aprobado (COP)" htmlFor="pr-v"><input id="pr-v" type="number" min="0" step="1000" className="input" value={f.valorAprobado ?? ''} onChange={set('valorAprobado')} /></Campo>
        <div className="sm:col-span-2"><ErrorCaja mensaje={err} /></div>
      </div>
    </Modal>
  )
}

function ContratoModal({ abierto, s, onCerrar, onHecho }) {
  const toast = useToast()
  const cat = useCatalogos()
  const [f, setF] = useState({})
  const [err, setErr] = useState('')
  const yaGasto = (s.presupuesto?.movimientos ?? []).some((m) => !m.anulado && m.tipo === 'GASTO')
  useEffect(() => {
    if (!abierto) return
    const p = s.presupuesto ?? {}
    setErr(''); setF({ modalidad: p.modalidadContratacion ?? '', contratoNumero: p.contratoNumero ?? '', contratista: p.contratista ?? '', valorContratado: p.valorContratado ?? '',
      fechaContrato: p.fechaContrato ?? toDia(new Date()), estadoContractual: p.estadoContractual ?? 'SIN_INICIAR', observacion: p.observacionContractual ?? '', registrarGasto: false, categoriaGasto: 'Compra de equipos' })
  }, [abierto, s])
  const set = (k) => (e) => setF((x) => ({ ...x, [k]: e.target.type === 'checkbox' ? e.target.checked : e.target.value }))
  const ok = async () => {
    try {
      await api.contratacion(s.resumen.id, { ...f, modalidad: f.modalidad || null, fechaContrato: f.fechaContrato || null, valorContratado: f.valorContratado === '' ? null : Number(f.valorContratado) })
      toast.exito('Información contractual guardada', f.registrarGasto ? 'Se registró el gasto en el presupuesto de la sede' : s.resumen.codigo); onHecho()
    } catch (e) { setErr(e.message) }
  }
  return (
    <Modal abierto={abierto} onCerrar={onCerrar} titulo="Contratación" subtitulo={s.resumen.codigo} ancho="max-w-2xl"
      pie={<><button className="btn-outline" onClick={onCerrar}>Volver</button><button className="btn-primary" onClick={ok}>Guardar</button></>}>
      <div className="grid gap-3 sm:grid-cols-2">
        <Campo etiqueta="Estado contractual" htmlFor="ct-e"><select id="ct-e" className="input" value={f.estadoContractual ?? ''} onChange={set('estadoContractual')}>{Object.entries(ESTADOS_CONTRATO).map(([k, t]) => <option key={k} value={k}>{t}</option>)}</select></Campo>
        <Campo etiqueta="Modalidad" htmlFor="ct-m"><select id="ct-m" className="input" value={f.modalidad ?? ''} onChange={set('modalidad')}><option value="">—</option>{(cat.MODALIDAD_CONTRATACION ?? []).map((x) => <option key={x}>{x}</option>)}</select></Campo>
        <Campo etiqueta="Número de contrato u orden" htmlFor="ct-n"><input id="ct-n" className="input" value={f.contratoNumero ?? ''} onChange={set('contratoNumero')} maxLength={40} /></Campo>
        <Campo etiqueta="Contratista" htmlFor="ct-c"><input id="ct-c" className="input" value={f.contratista ?? ''} onChange={set('contratista')} maxLength={160} /></Campo>
        <Campo etiqueta="Valor contratado (COP)" htmlFor="ct-v"><input id="ct-v" type="number" min="0" step="1000" className="input" value={f.valorContratado ?? ''} onChange={set('valorContratado')} /></Campo>
        <Campo etiqueta="Fecha" htmlFor="ct-f"><input id="ct-f" type="date" className="input" value={f.fechaContrato ?? ''} onChange={set('fechaContrato')} /></Campo>
        <div className="sm:col-span-2"><Campo etiqueta="Observación" htmlFor="ct-o"><textarea id="ct-o" rows={2} className="input" value={f.observacion ?? ''} onChange={set('observacion')} maxLength={1000} /></Campo></div>
        {!yaGasto && ['APROBADA', 'CERRADA'].includes(s.resumen.estado) && (
          <div className="grid gap-3 rounded-lg bg-slate-50 p-3 sm:col-span-2 sm:grid-cols-2">
            <label className="flex cursor-pointer items-center gap-2 text-sm font-semibold"><input type="checkbox" checked={!!f.registrarGasto} onChange={set('registrarGasto')} className="h-4 w-4 accent-[#8F141B]" /> Registrar el gasto en el presupuesto de la sede</label>
            {f.registrarGasto && <Campo etiqueta="Categoría del gasto" htmlFor="ct-cat"><select id="ct-cat" className="input" value={f.categoriaGasto} onChange={set('categoriaGasto')}>{CATEGORIAS_PRESUPUESTO.filter((c) => c !== 'Asignación').map((c) => <option key={c}>{c}</option>)}</select></Campo>}
          </div>
        )}
        <div className="sm:col-span-2"><ErrorCaja mensaje={err} /></div>
      </div>
    </Modal>
  )
}

/** Avance con evidencias (también se usa desde «Compromisos y avances»). */
export function AvanceModal({ abierto, solicitud, compromiso, laboratorios, onCerrar, onHecho }) {
  const toast = useToast()
  const [f, setF] = useState({ laboratorioId: '', descripcion: '', porcentaje: '' })
  const [evidencias, setEvidencias] = useState([])
  const [err, setErr] = useState('')
  const [enviando, setEnviando] = useState(false)
  useEffect(() => { if (abierto) { setF({ laboratorioId: laboratorios?.[0]?.id ?? '', descripcion: '', porcentaje: '' }); setEvidencias([]); setErr('') } }, [abierto, laboratorios])
  const ok = async () => {
    if (f.descripcion.trim().length < 10) { setErr('Describe el avance (mínimo 10 caracteres)'); return }
    setEnviando(true); setErr('')
    try {
      await api.registrarAvance({ solicitudId: solicitud?.id, compromisoId: compromiso?.id, laboratorioId: !solicitud && !compromiso?.laboratorioId ? f.laboratorioId : undefined,
        descripcion: f.descripcion.trim(), porcentaje: f.porcentaje === '' ? undefined : Number(f.porcentaje), evidencias })
      toast.exito('Avance registrado', 'Queda pendiente de verificación del Administrador'); onHecho()
    } catch (e) { setErr(e.message) } finally { setEnviando(false) }
  }
  return (
    <Modal abierto={abierto} onCerrar={onCerrar} titulo="Registrar avance" subtitulo={solicitud ? `${solicitud.codigo} · ${solicitud.titulo}` : compromiso ? compromiso.titulo : undefined}
      pie={<><button className="btn-outline" onClick={onCerrar}>Volver</button><button className="btn-primary" onClick={ok} disabled={enviando}><TrendingUp className="h-4 w-4" aria-hidden="true" /> {enviando ? 'Guardando…' : 'Registrar'}</button></>}>
      <div className="space-y-3">
        {!solicitud && !compromiso?.laboratorioId && laboratorios && (
          <Campo etiqueta="Laboratorio" htmlFor="av-l"><select id="av-l" className="input" value={f.laboratorioId} onChange={(e) => setF((x) => ({ ...x, laboratorioId: e.target.value }))}>{laboratorios.map((l) => <option key={l.id} value={l.id}>{l.nombre}</option>)}</select></Campo>
        )}
        <Campo etiqueta="¿Qué se avanzó?" htmlFor="av-d"><textarea id="av-d" rows={3} className="input" value={f.descripcion} onChange={(e) => setF((x) => ({ ...x, descripcion: e.target.value }))} maxLength={2000} /></Campo>
        <Campo etiqueta="Porcentaje de avance (opcional)" htmlFor="av-p"><input id="av-p" type="number" min="0" max="100" className="input" value={f.porcentaje} onChange={(e) => setF((x) => ({ ...x, porcentaje: e.target.value }))} /></Campo>
        <SelectorArchivos archivos={evidencias} onCambio={setEvidencias} texto="Adjuntar evidencias" id="av-ev" />
        <ErrorCaja mensaje={err} />
      </div>
    </Modal>
  )
}
