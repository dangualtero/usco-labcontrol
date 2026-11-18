import { useEffect, useMemo, useState } from 'react'
import { Link } from 'react-router-dom'
import { CalendarClock, CalendarPlus, Pencil, Play, Plus, RefreshCw, Save, Search, Wrench } from 'lucide-react'
import { api } from '../../api/index.js'
import { useAuth } from '../../context/AuthContext.jsx'
import { useSede } from '../../context/SedeContext.jsx'
import { useToast } from '../../context/ToastContext.jsx'
import { useDatos } from '../../lib/hooks.js'
import { norm, pesos, puede } from '../../lib/constantes.js'
import { fmtFecha, toDia } from '../../lib/fecha.js'
import { Campo, Cargando, ErrorCaja, Modal, Vacio } from '../../components/ui.jsx'
import { Encabezado, ESTADOS_MANTENIMIENTO, EstadoMantenimiento } from '../../components/Estados.jsx'
import { FiltroTipo } from '../../components/FiltroTipo.jsx'

export const TIPOS_MANT = [['PREVENTIVO', 'Preventivo'], ['CORRECTIVO', 'Correctivo'], ['CALIBRACION', 'Calibración'], ['VERIFICACION', 'Verificación']]
const FILTROS = [
  { valor: 'ABIERTOS', texto: 'Abiertos', Icono: Wrench },
  { valor: 'REQUERIDO', texto: 'Requeridos', Icono: ESTADOS_MANTENIMIENTO.REQUERIDO.Icono },
  { valor: 'PROGRAMADO', texto: 'Programados', Icono: CalendarClock },
  { valor: 'REALIZADO', texto: 'Realizados', Icono: ESTADOS_MANTENIMIENTO.REALIZADO.Icono },
  { valor: 'todos', texto: 'Todos', Icono: Wrench },
]

/**
 * Mantenimientos: el monitor y el coordinador registran requerimientos; el coordinador (o el Administrador) los programa
 * con fecha, responsable y costo, los ejecuta y los cierra con su resultado. Todo queda en la hoja de vida del equipo.
 */
export default function Mantenimientos() {
  const { usuario } = useAuth()
  const { sede, ruta } = useSede()
  const gestiona = puede(usuario, 'MANTENIMIENTO_GESTIONAR')
  const requiere = puede(usuario, 'MANTENIMIENTO_REQUERIR', 'MANTENIMIENTO_GESTIONAR')
  const [filtro, setFiltro] = useState('ABIERTOS')
  const [q, setQ] = useState('')
  const { datos, error, cargando, recargar } = useDatos(() => api.mantenimientos({ estado: filtro }), [filtro], { cada: 30000 })
  const [form, setForm] = useState(null)       // { mant } o {}
  const [estado, setEstado] = useState(null)

  const lista = useMemo(() => {
    const nq = norm(q)
    return (datos ?? []).filter((m) => !nq || norm(`${m.codigo} ${m.placa} ${m.equipo} ${m.laboratorio} ${m.descripcion} ${m.responsable ?? ''}`).includes(nq))
  }, [datos, q])

  return (
    <div className="space-y-5">
      <Encabezado modulo={`Inventarios y mantenimientos · ${sede?.nombre}`} titulo="Mantenimientos"
        detalle="Requerimientos, programación y cierre de mantenimientos preventivos, correctivos, calibraciones y verificaciones.">
        <button onClick={recargar} disabled={cargando} className="btn-outline"><RefreshCw className={`h-4 w-4 ${cargando ? 'animate-spin' : ''}`} aria-hidden="true" /> Actualizar</button>
        {requiere && <button onClick={() => setForm({})} className="btn-primary"><Plus className="h-4 w-4" aria-hidden="true" /> {gestiona ? 'Nuevo mantenimiento' : 'Registrar requerimiento'}</button>}
      </Encabezado>

      <div className="flex flex-col gap-3 lg:flex-row lg:items-center">
        <FiltroTipo valor={filtro} onCambio={setFiltro} opciones={FILTROS} etiqueta="Filtrar por estado" />
        <div className="relative flex-1"><label htmlFor="mt-q" className="sr-only">Buscar</label><Search className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-pizarra" aria-hidden="true" />
          <input id="mt-q" className="input pl-9" placeholder="Código, placa, equipo, laboratorio o responsable" value={q} onChange={(e) => setQ(e.target.value)} /></div>
      </div>

      <ErrorCaja mensaje={error} />
      {!datos && !error && <Cargando />}
      {datos && lista.length === 0 && <Vacio icono={Wrench} titulo="Sin mantenimientos" detalle="No hay mantenimientos con ese filtro." />}
      {lista.length > 0 && (
        <ul className="space-y-2.5">
          {lista.map((m) => {
            const abierto = ['REQUERIDO', 'PROGRAMADO', 'EN_EJECUCION'].includes(m.estado)
            return (
              <li key={m.id} className={`card flex flex-col gap-3 p-4 lg:flex-row lg:items-center ${m.vencido ? 'border-l-4 border-l-alerta-borde' : ''}`}>
                <div className="min-w-0 flex-1">
                  <div className="flex flex-wrap items-center gap-2">
                    <span className="font-mono text-xs font-bold text-pizarra">{m.codigo}</span>
                    <EstadoMantenimiento estado={m.estado} vencido={m.vencido} />
                    <span className="text-sm font-extrabold">{m.tipoTexto}</span>
                  </div>
                  <p className="mt-1 text-sm"><Link to={ruta(`/inventario/${m.placa}`)} className="placa hover:underline">{m.placa}</Link> · <strong>{m.equipo}</strong> · <span className="text-pizarra">{m.laboratorio}</span></p>
                  <p className="text-sm text-slate-700">{m.descripcion}</p>
                  <p className="mt-1 text-xs text-pizarra">
                    {m.fechaProgramada ? `Programado para el ${fmtFecha(m.fechaProgramada)}` : 'Sin fecha'}{m.responsable ? ` · ${m.responsable}` : ''}
                    {m.costo != null ? ` · ${pesos(m.costo)}` : ''}{m.fechaRealizada ? ` · Realizado el ${fmtFecha(m.fechaRealizada)}` : ''} · Registró {m.registradoPor}
                  </p>
                  {m.resultado && <p className="text-xs text-slate-700"><strong>Resultado:</strong> {m.resultado}</p>}
                </div>
                {gestiona && abierto && (
                  <div className="flex shrink-0 flex-wrap gap-1.5">
                    <button className="btn-outline btn-sm" onClick={() => setForm({ mant: m })}>{m.fechaProgramada ? <><Pencil className="h-4 w-4" aria-hidden="true" /> Editar</> : <><CalendarPlus className="h-4 w-4" aria-hidden="true" /> Programar</>}</button>
                    <button className="btn-primary btn-sm" onClick={() => setEstado(m)}><Play className="h-4 w-4" aria-hidden="true" /> Avanzar</button>
                  </div>
                )}
              </li>
            )
          })}
        </ul>
      )}

      <MantenimientoFormModal abierto={!!form} mant={form?.mant} onCerrar={() => setForm(null)} onHecho={recargar} />
      <EstadoMantModal mant={estado} onCerrar={() => setEstado(null)} onHecho={recargar} />
    </div>
  )
}

/** Nuevo requerimiento o programación de un mantenimiento. */
export function MantenimientoFormModal({ abierto, mant, placaInicial, onCerrar, onHecho }) {
  const toast = useToast()
  const { usuario } = useAuth()
  const gestiona = puede(usuario, 'MANTENIMIENTO_GESTIONAR')
  const nuevo = !mant
  const [equipos, setEquipos] = useState([])
  const [f, setF] = useState({})
  const [err, setErr] = useState('')
  const [enviando, setEnviando] = useState(false)

  useEffect(() => {
    if (!abierto) return
    setErr('')
    setF(mant ? { placa: mant.placa, tipo: mant.tipo, descripcion: mant.descripcion, fechaProgramada: mant.fechaProgramada ?? '', responsable: mant.responsable ?? '', costo: mant.costo ?? '' }
      : { placa: placaInicial ?? '', tipo: 'PREVENTIVO', descripcion: '', fechaProgramada: '', responsable: '', costo: '' })
    if (nuevo && !placaInicial) api.inventario({ tipo: 'EQUIPO' }).then((l) => setEquipos(l.filter((x) => x.activo).map((x) => x.equipo))).catch(() => {})
  }, [abierto, mant, placaInicial, nuevo])

  const set = (k) => (e) => setF((x) => ({ ...x, [k]: e.target.value }))
  const guardar = async (e) => {
    e.preventDefault()
    if (!f.placa) { setErr('Selecciona el equipo'); return }
    if ((f.descripcion ?? '').trim().length < 5) { setErr('Describe el mantenimiento (mínimo 5 caracteres)'); return }
    setEnviando(true); setErr('')
    const cuerpo = { placa: f.placa, tipo: f.tipo, descripcion: f.descripcion.trim(),
      ...(gestiona ? { fechaProgramada: f.fechaProgramada || null, responsable: f.responsable.trim() || null, costo: f.costo === '' ? null : Number(f.costo) } : {}) }
    try {
      const r = nuevo ? await api.crearMantenimiento(cuerpo) : await api.actualizarMantenimiento(mant.id, cuerpo)
      toast.exito(nuevo ? 'Mantenimiento registrado' : 'Mantenimiento actualizado', `${r.codigo} · ${r.estadoTexto}`)
      onHecho?.(); onCerrar()
    } catch (er) { setErr(er.message) } finally { setEnviando(false) }
  }

  return (
    <Modal abierto={abierto} onCerrar={onCerrar} titulo={nuevo ? (gestiona ? 'Nuevo mantenimiento' : 'Registrar requerimiento de mantenimiento') : `Mantenimiento ${mant?.codigo}`}
      subtitulo={mant ? `${mant.placa} · ${mant.equipo}` : undefined}
      pie={<>
        <button type="button" className="btn-outline" onClick={onCerrar}>Cancelar</button>
        <button type="submit" form="form-mant" className="btn-primary" disabled={enviando}><Save className="h-4 w-4" aria-hidden="true" /> {enviando ? 'Guardando…' : 'Guardar'}</button>
      </>}>
      <form id="form-mant" onSubmit={guardar} noValidate className="grid gap-4 sm:grid-cols-2">
        {nuevo && !placaInicial && (
          <div className="sm:col-span-2"><Campo etiqueta="Equipo" htmlFor="mt-placa">
            <select id="mt-placa" className="input" value={f.placa ?? ''} onChange={set('placa')}>
              <option value="">Selecciona…</option>{equipos.map((x) => <option key={x.placa} value={x.placa}>{x.placa} · {x.nombre} ({x.laboratorio})</option>)}
            </select>
          </Campo></div>
        )}
        <Campo etiqueta="Tipo" htmlFor="mt-tipo"><select id="mt-tipo" className="input" value={f.tipo ?? ''} onChange={set('tipo')}>{TIPOS_MANT.map(([v, t]) => <option key={v} value={v}>{t}</option>)}</select></Campo>
        {gestiona && <Campo etiqueta="Fecha programada" htmlFor="mt-fecha" ayuda="Vacía = queda como requerimiento"><input id="mt-fecha" type="date" min={toDia(new Date())} className="input" value={f.fechaProgramada ?? ''} onChange={set('fechaProgramada')} /></Campo>}
        <div className="sm:col-span-2"><Campo etiqueta="Descripción" htmlFor="mt-desc"><textarea id="mt-desc" rows={3} className="input" value={f.descripcion ?? ''} onChange={set('descripcion')} maxLength={1000} placeholder="Qué se debe hacer y por qué" /></Campo></div>
        {gestiona && <Campo etiqueta="Responsable (técnico o proveedor)" htmlFor="mt-resp"><input id="mt-resp" className="input" value={f.responsable ?? ''} onChange={set('responsable')} maxLength={120} /></Campo>}
        {gestiona && <Campo etiqueta="Costo estimado (COP)" htmlFor="mt-costo"><input id="mt-costo" type="number" min="0" step="1000" className="input" value={f.costo ?? ''} onChange={set('costo')} /></Campo>}
        {!gestiona && <p className="text-xs text-pizarra sm:col-span-2">El coordinador del laboratorio define la fecha, el responsable y el costo.</p>}
        <div className="sm:col-span-2"><ErrorCaja mensaje={err} /></div>
      </form>
    </Modal>
  )
}

const SIGUIENTES = {
  REQUERIDO: ['EN_EJECUCION', 'REALIZADO', 'CANCELADO'],
  PROGRAMADO: ['EN_EJECUCION', 'REALIZADO', 'CANCELADO'],
  EN_EJECUCION: ['REALIZADO', 'CANCELADO'],
}

function EstadoMantModal({ mant, onCerrar, onHecho }) {
  const toast = useToast()
  const [estado, setEstado] = useState('')
  const [fecha, setFecha] = useState('')
  const [resultado, setResultado] = useState('')
  const [operativo, setOperativo] = useState(true)
  const [err, setErr] = useState('')
  const [enviando, setEnviando] = useState(false)
  useEffect(() => {
    if (!mant) return
    setEstado(SIGUIENTES[mant.estado]?.[0] ?? ''); setFecha(toDia(new Date())); setResultado(''); setOperativo(true); setErr('')
  }, [mant])
  const guardar = async () => {
    setEnviando(true); setErr('')
    try {
      const r = await api.estadoMantenimiento(mant.id, { estado, fechaRealizada: estado === 'REALIZADO' ? fecha : null, resultado: resultado.trim() || null, equipoOperativo: operativo })
      toast.exito('Mantenimiento actualizado', `${r.codigo} · ${r.estadoTexto}`)
      onHecho?.(); onCerrar()
    } catch (e) { setErr(e.message) } finally { setEnviando(false) }
  }
  return (
    <Modal abierto={!!mant} onCerrar={onCerrar} titulo="Avanzar mantenimiento" subtitulo={mant ? `${mant.codigo} · ${mant.placa} · ${mant.equipo}` : undefined}
      pie={<>
        <button className="btn-outline" onClick={onCerrar}>Cancelar</button>
        <button className="btn-primary" onClick={guardar} disabled={enviando}><Save className="h-4 w-4" aria-hidden="true" /> {enviando ? 'Guardando…' : 'Guardar'}</button>
      </>}>
      {mant && (
        <div className="space-y-4">
          <fieldset><legend className="label">Nuevo estado</legend>
            <div className="grid gap-2 sm:grid-cols-3">
              {(SIGUIENTES[mant.estado] ?? []).map((k) => {
                const d = ESTADOS_MANTENIMIENTO[k]
                return (
                  <label key={k} className={`flex min-h-[44px] cursor-pointer items-center gap-2 rounded-lg border-2 px-3 text-sm font-semibold ${estado === k ? 'border-usco bg-usco-soft' : 'border-slate-200'}`}>
                    <input type="radio" name="est-mant" checked={estado === k} onChange={() => setEstado(k)} className="accent-[#8F141B]" /> <d.Icono className="h-4 w-4" aria-hidden="true" /> {d.texto}
                  </label>
                )
              })}
            </div>
          </fieldset>
          {estado === 'EN_EJECUCION' && <p className="text-sm text-pizarra">El equipo pasará a «En mantenimiento».</p>}
          {estado === 'REALIZADO' && (
            <>
              <Campo etiqueta="Fecha de realización" htmlFor="em-fecha"><input id="em-fecha" type="date" max={toDia(new Date())} className="input" value={fecha} onChange={(e) => setFecha(e.target.value)} /></Campo>
              <label className="flex cursor-pointer items-center gap-2 text-sm font-semibold"><input type="checkbox" checked={operativo} onChange={(e) => setOperativo(e.target.checked)} className="h-4 w-4 accent-[#8F141B]" /> El equipo quedó operativo (si no, pasa a «Fuera de servicio»)</label>
            </>
          )}
          {(estado === 'REALIZADO' || estado === 'CANCELADO') && (
            <Campo etiqueta={estado === 'REALIZADO' ? 'Resultado' : 'Motivo de la cancelación'} htmlFor="em-res">
              <textarea id="em-res" rows={3} className="input" value={resultado} onChange={(e) => setResultado(e.target.value)} maxLength={1000} placeholder={estado === 'REALIZADO' ? 'Ej. Calibrado dentro de tolerancia; certificado adjunto en documentos' : ''} />
            </Campo>
          )}
          <ErrorCaja mensaje={err} />
        </div>
      )}
    </Modal>
  )
}
