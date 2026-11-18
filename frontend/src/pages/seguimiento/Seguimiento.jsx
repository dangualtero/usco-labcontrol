import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { CircleCheck, ListChecks, Pencil, Plus, RefreshCw, Save, TrendingUp, Undo2 } from 'lucide-react'
import { api } from '../../api/index.js'
import { useAuth } from '../../context/AuthContext.jsx'
import { useSede } from '../../context/SedeContext.jsx'
import { useToast } from '../../context/ToastContext.jsx'
import { useDatos, useLabsRef } from '../../lib/hooks.js'
import { puede } from '../../lib/constantes.js'
import { fmtFecha, fmtFechaHora, toDia } from '../../lib/fecha.js'
import { Campo, Cargando, ErrorCaja, Modal, Vacio } from '../../components/ui.jsx'
import { ListaArchivos } from '../../components/Archivos.jsx'
import { ESTADOS_COMPROMISO, Encabezado, EstadoCompromiso, VERIFICACION, Verificacion } from '../../components/Estados.jsx'
import { FiltroTipo } from '../../components/FiltroTipo.jsx'
import { AvanceModal } from '../solicitudes/SolicitudDetalle.jsx'

/**
 * Reportes y seguimiento: compromisos de cada laboratorio (o institucionales) con responsable y fecha límite, avances con
 * evidencias y su verificación por el Administrador.
 */
export default function Seguimiento() {
  const { usuario } = useAuth()
  const { sede } = useSede()
  const [pestana, setPestana] = useState('compromisos')
  return (
    <div className="space-y-5">
      <Encabezado modulo={`Reportes y seguimiento · ${sede?.nombre}`} titulo="Compromisos y avances"
        detalle="Registra los compromisos de cada laboratorio, sus avances con evidencias y la verificación institucional." />
      <FiltroTipo valor={pestana} onCambio={setPestana} etiqueta="Sección"
        opciones={[{ valor: 'compromisos', texto: 'Compromisos', Icono: ListChecks }, { valor: 'avances', texto: 'Avances y evidencias', Icono: TrendingUp }]} />
      {pestana === 'compromisos' ? <Compromisos usuario={usuario} /> : <Avances usuario={usuario} />}
    </div>
  )
}

function Compromisos({ usuario }) {
  const toast = useToast()
  const gestiona = puede(usuario, 'COMPROMISOS_GESTIONAR')
  const registra = puede(usuario, 'AVANCES_REGISTRAR')
  const [estado, setEstado] = useState('ABIERTOS')
  const { datos, error, cargando, recargar } = useDatos(() => api.compromisos({ estado }), [estado], { cada: 30000 })
  const [form, setForm] = useState(null)
  const [avance, setAvance] = useState(null)
  const cambiar = async (c, e) => {
    try { await api.estadoCompromiso(c.id, e); toast.exito('Compromiso actualizado', `${c.titulo} · ${ESTADOS_COMPROMISO[e].texto}`); recargar() }
    catch (er) { toast.error('No se pudo actualizar', er.message) }
  }
  return (
    <>
      <div className="flex flex-wrap items-center justify-between gap-2">
        <select className="input max-w-[240px]" value={estado} onChange={(e) => setEstado(e.target.value)} aria-label="Filtrar compromisos">
          <option value="ABIERTOS">Abiertos</option><option value="VENCIDOS">Vencidos</option>
          {Object.entries(ESTADOS_COMPROMISO).map(([k, v]) => <option key={k} value={k}>{v.texto}</option>)}<option value="todos">Todos</option>
        </select>
        <div className="flex gap-2">
          <button onClick={recargar} disabled={cargando} className="btn-outline"><RefreshCw className={`h-4 w-4 ${cargando ? 'animate-spin' : ''}`} aria-hidden="true" /></button>
          {gestiona && <button onClick={() => setForm({})} className="btn-primary"><Plus className="h-4 w-4" aria-hidden="true" /> Nuevo compromiso</button>}
        </div>
      </div>
      <ErrorCaja mensaje={error} />
      {!datos && !error && <Cargando />}
      {datos && datos.length === 0 && <Vacio icono={ListChecks} titulo="Sin compromisos" detalle="No hay compromisos con ese filtro." />}
      {datos && datos.length > 0 && (
        <ul className="space-y-2.5">
          {datos.map((c) => {
            const abierto = c.estado === 'PENDIENTE' || c.estado === 'EN_CURSO'
            return (
              <li key={c.id} className={`card flex flex-col gap-3 p-4 lg:flex-row lg:items-center ${c.vencido ? 'border-l-4 border-l-alerta-borde' : ''}`}>
                <div className="min-w-0 flex-1">
                  <div className="flex flex-wrap items-center gap-2"><EstadoCompromiso estado={c.estado} vencido={c.vencido} /><span className="font-extrabold">{c.titulo}</span></div>
                  <p className="mt-0.5 text-sm text-pizarra">{c.laboratorio} · Responsable: {c.responsable ?? 'sin asignar'} · Fecha límite {fmtFecha(c.fechaLimite)} · {c.avances} avance(s)</p>
                  {c.descripcion && <p className="text-sm text-slate-700">{c.descripcion}</p>}
                  {c.ultimoPorcentaje != null && (
                    <div className="mt-2 flex max-w-sm items-center gap-2">
                      <div className="h-2 flex-1 overflow-hidden rounded-full bg-slate-200" role="meter" aria-valuemin={0} aria-valuemax={100} aria-valuenow={c.ultimoPorcentaje} aria-label="Avance">
                        <div className="h-full rounded-full bg-usco" style={{ width: `${Math.max(c.ultimoPorcentaje, 3)}%` }} />
                      </div>
                      <span className="text-xs font-bold">{c.ultimoPorcentaje} %</span>
                    </div>
                  )}
                </div>
                {abierto && (
                  <div className="flex shrink-0 flex-wrap gap-1.5">
                    {registra && <button className="btn-outline btn-sm" onClick={() => setAvance(c)}><TrendingUp className="h-4 w-4" aria-hidden="true" /> Avance</button>}
                    {gestiona && <button className="btn-outline btn-sm" onClick={() => setForm({ c })}><Pencil className="h-4 w-4" aria-hidden="true" /> Editar</button>}
                    {gestiona && <button className="btn-entrada btn-sm" onClick={() => cambiar(c, 'CUMPLIDO')}><CircleCheck className="h-4 w-4" aria-hidden="true" /> Cumplido</button>}
                    {gestiona && <button className="btn-outline btn-sm" onClick={() => cambiar(c, 'CANCELADO')}>Cancelar</button>}
                  </div>
                )}
                {!abierto && gestiona && <button className="btn-outline btn-sm self-start" onClick={() => cambiar(c, 'EN_CURSO')}><Undo2 className="h-4 w-4" aria-hidden="true" /> Reabrir</button>}
              </li>
            )
          })}
        </ul>
      )}
      <CompromisoModal abierto={!!form} c={form?.c} admin={usuario.rol === 'ADMINISTRADOR'} onCerrar={() => setForm(null)} onHecho={() => { setForm(null); recargar() }} />
      <AvanceModal abierto={!!avance} compromiso={avance} onCerrar={() => setAvance(null)} onHecho={() => { setAvance(null); recargar() }} />
    </>
  )
}

function CompromisoModal({ abierto, c, admin, onCerrar, onHecho }) {
  const toast = useToast()
  const labs = useLabsRef([abierto])
  const [f, setF] = useState({})
  const [personas, setPersonas] = useState([])
  const [err, setErr] = useState('')
  useEffect(() => {
    if (!abierto) return
    setErr('')
    setF(c ? { laboratorioId: c.laboratorioId ?? '', titulo: c.titulo, descripcion: c.descripcion ?? '', responsableId: c.responsableId ?? '', fechaLimite: c.fechaLimite }
      : { laboratorioId: '', titulo: '', descripcion: '', responsableId: '', fechaLimite: '' })
  }, [abierto, c])
  useEffect(() => { if (abierto && !c && !admin && !f.laboratorioId && labs.length) setF((x) => ({ ...x, laboratorioId: labs[0].id })) }, [abierto, c, admin, labs, f.laboratorioId])
  useEffect(() => {
    if (!abierto) return
    api.personas(f.laboratorioId || undefined).then(setPersonas).catch(() => setPersonas([]))
  }, [abierto, f.laboratorioId])
  const set = (k) => (e) => setF((x) => ({ ...x, [k]: e.target.value }))
  const ok = async () => {
    if ((f.titulo ?? '').trim().length < 5) { setErr('Escribe el título (mínimo 5 caracteres)'); return }
    if (!f.fechaLimite) { setErr('Indica la fecha límite'); return }
    const cuerpo = { laboratorioId: f.laboratorioId === '' ? null : Number(f.laboratorioId), titulo: f.titulo.trim(), descripcion: f.descripcion || null,
      responsableId: f.responsableId === '' ? null : Number(f.responsableId), fechaLimite: f.fechaLimite }
    try {
      if (c) await api.actualizarCompromiso(c.id, cuerpo); else await api.crearCompromiso(cuerpo)
      toast.exito(c ? 'Compromiso actualizado' : 'Compromiso creado', cuerpo.titulo); onHecho()
    } catch (e) { setErr(e.message) }
  }
  return (
    <Modal abierto={abierto} onCerrar={onCerrar} titulo={c ? 'Editar compromiso' : 'Nuevo compromiso'}
      pie={<><button className="btn-outline" onClick={onCerrar}>Cancelar</button><button className="btn-primary" onClick={ok}><Save className="h-4 w-4" aria-hidden="true" /> Guardar</button></>}>
      <div className="grid gap-3 sm:grid-cols-2">
        {!c && (
          <Campo etiqueta="Laboratorio" htmlFor="cp-l">
            <select id="cp-l" className="input" value={f.laboratorioId ?? ''} onChange={set('laboratorioId')}>
              {admin && <option value="">Toda la sede (institucional)</option>}{labs.map((l) => <option key={l.id} value={l.id}>{l.nombre}</option>)}
            </select>
          </Campo>
        )}
        <Campo etiqueta="Fecha límite" htmlFor="cp-f"><input id="cp-f" type="date" min={c ? undefined : toDia(new Date())} className="input" value={f.fechaLimite ?? ''} onChange={set('fechaLimite')} /></Campo>
        <div className="sm:col-span-2"><Campo etiqueta="Compromiso" htmlFor="cp-t"><input id="cp-t" className="input" value={f.titulo ?? ''} onChange={set('titulo')} maxLength={160} placeholder="Ej. Actualizar las guías de práctica del semestre" /></Campo></div>
        <div className="sm:col-span-2"><Campo etiqueta="Descripción" htmlFor="cp-d"><textarea id="cp-d" rows={3} className="input" value={f.descripcion ?? ''} onChange={set('descripcion')} maxLength={2000} /></Campo></div>
        <Campo etiqueta="Responsable" htmlFor="cp-r">
          <select id="cp-r" className="input" value={f.responsableId ?? ''} onChange={set('responsableId')}>
            <option value="">Sin asignar</option>{personas.map((p) => <option key={p.id} value={p.id}>{p.nombre} · {p.rol}</option>)}
          </select>
        </Campo>
        <div className="sm:col-span-2"><ErrorCaja mensaje={err} /></div>
      </div>
    </Modal>
  )
}

function Avances({ usuario }) {
  const toast = useToast()
  const { ruta } = useSede()
  const verifica = puede(usuario, 'AVANCES_VERIFICAR')
  const registra = puede(usuario, 'AVANCES_REGISTRAR')
  const labs = useLabsRef()
  const [verificacion, setVerificacion] = useState(verifica ? 'PENDIENTE' : 'todos')
  const { datos, error, cargando, recargar } = useDatos(() => api.avances({ verificacion: verificacion === 'todos' ? undefined : verificacion }), [verificacion], { cada: 30000 })
  const [nuevo, setNuevo] = useState(false)
  const [verificar, setVerificar] = useState(null)
  return (
    <>
      <div className="flex flex-wrap items-center justify-between gap-2">
        <select className="input max-w-[240px]" value={verificacion} onChange={(e) => setVerificacion(e.target.value)} aria-label="Filtrar avances">
          {Object.entries(VERIFICACION).map(([k, v]) => <option key={k} value={k}>{v.texto}</option>)}<option value="todos">Todos</option>
        </select>
        <div className="flex gap-2">
          <button onClick={recargar} disabled={cargando} className="btn-outline"><RefreshCw className={`h-4 w-4 ${cargando ? 'animate-spin' : ''}`} aria-hidden="true" /></button>
          {registra && <button onClick={() => setNuevo(true)} className="btn-primary"><Plus className="h-4 w-4" aria-hidden="true" /> Registrar avance</button>}
        </div>
      </div>
      <ErrorCaja mensaje={error} />
      {!datos && !error && <Cargando />}
      {datos && datos.length === 0 && <Vacio icono={TrendingUp} titulo="Sin avances" detalle="No hay avances con ese filtro." />}
      {datos && datos.length > 0 && (
        <ul className="space-y-2.5">
          {datos.map((a) => (
            <li key={a.id} className="card p-4 text-sm">
              <div className="flex flex-wrap items-center justify-between gap-2">
                <span className="flex flex-wrap items-center gap-2"><Verificacion estado={a.verificacion} /><strong>{a.laboratorio}</strong>
                  {a.compromiso && <span className="text-pizarra">· Compromiso: {a.compromiso}</span>}
                  {a.solicitud && <Link to={ruta(`/solicitudes/${a.solicitudId}`)} className="font-mono text-xs font-bold text-usco hover:underline">{a.solicitud}</Link>}
                  {a.porcentaje != null && <span className="font-bold">{a.porcentaje} %</span>}</span>
                <span className="text-xs text-pizarra">{a.autor} · {fmtFechaHora(a.creadoEn)}</span>
              </div>
              <p className="mt-1 text-slate-800">{a.descripcion}</p>
              {a.observacionVerificacion && <p className="mt-1 text-xs text-pizarra">Verificación ({a.verificadoPor}): {a.observacionVerificacion}</p>}
              {a.evidencias.length > 0 && <div className="mt-2"><ListaArchivos archivos={a.evidencias} /></div>}
              {verifica && a.verificacion === 'PENDIENTE' && <button className="btn-outline btn-sm mt-2" onClick={() => setVerificar(a)}><CircleCheck className="h-4 w-4" aria-hidden="true" /> Verificar</button>}
            </li>
          ))}
        </ul>
      )}
      <AvanceModal abierto={nuevo} laboratorios={labs} onCerrar={() => setNuevo(false)} onHecho={() => { setNuevo(false); recargar() }} />
      <VerificarModal a={verificar} onCerrar={() => setVerificar(null)} onHecho={() => { setVerificar(null); recargar(); toast.exito('Verificación registrada') }} />
    </>
  )
}

function VerificarModal({ a, onCerrar, onHecho }) {
  const [obs, setObs] = useState('')
  const [err, setErr] = useState('')
  useEffect(() => { setObs(''); setErr('') }, [a])
  const ok = async (resultado) => {
    try { await api.verificarAvance(a.id, resultado, obs.trim() || null); onHecho() } catch (e) { setErr(e.message) }
  }
  return (
    <Modal abierto={!!a} onCerrar={onCerrar} titulo="Verificar avance" subtitulo={a ? `${a.laboratorio} · ${a.autor}` : undefined} ancho="max-w-md"
      pie={<><button className="btn-outline" onClick={() => ok('OBSERVADO')}><Undo2 className="h-4 w-4" aria-hidden="true" /> Con observaciones</button><button className="btn-entrada" onClick={() => ok('VERIFICADO')}><CircleCheck className="h-4 w-4" aria-hidden="true" /> Verificado</button></>}>
      {a && <p className="mb-3 rounded-lg bg-slate-50 p-3 text-sm">{a.descripcion}</p>}
      <Campo etiqueta="Observación (obligatoria si tiene observaciones)" htmlFor="vf-o"><textarea id="vf-o" rows={3} className="input" value={obs} onChange={(e) => setObs(e.target.value)} maxLength={1000} /></Campo>
      <ErrorCaja mensaje={err} className="mt-3" />
    </Modal>
  )
}
