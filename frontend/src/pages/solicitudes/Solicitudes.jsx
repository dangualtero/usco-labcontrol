import { useMemo, useState } from 'react'
import { Link, useNavigate, useSearchParams } from 'react-router-dom'
import { ClipboardList, Inbox, Plus, RefreshCw, Search, Send, ShieldQuestion, CircleCheck } from 'lucide-react'
import { api } from '../../api/index.js'
import { useAuth } from '../../context/AuthContext.jsx'
import { useSede } from '../../context/SedeContext.jsx'
import { useDatos, useLabsRef } from '../../lib/hooks.js'
import { norm, pesos, puede } from '../../lib/constantes.js'
import { fmtFecha } from '../../lib/fecha.js'
import { Cargando, ErrorCaja, Vacio } from '../../components/ui.jsx'
import { Encabezado, ESTADOS_SOLICITUD, EstadoSolicitud, Prioridad } from '../../components/Estados.jsx'
import { FiltroTipo } from '../../components/FiltroTipo.jsx'
import SolicitudFormModal from './SolicitudFormModal.jsx'

/** Solicitudes y requerimientos: lista con filtros; la del Administrador, en orden de priorización. */
export default function Solicitudes() {
  const { usuario } = useAuth()
  const { sede, ruta } = useSede()
  const nav = useNavigate()
  const [params, setParams] = useSearchParams()
  const admin = puede(usuario, 'SOLICITUD_REVISAR')
  const validador = puede(usuario, 'SOLICITUD_VALIDAR') && !admin
  const opciones = [
    { valor: 'ABIERTAS', texto: 'Abiertas', Icono: Inbox },
    ...(validador ? [{ valor: 'EN_REVISION', texto: 'Por validar', Icono: ShieldQuestion }] : []),
    ...(admin ? [{ valor: 'ENVIADA', texto: 'Por decidir', Icono: Send }] : []),
    { valor: 'APROBADA', texto: 'Aprobadas', Icono: CircleCheck },
    { valor: 'todos', texto: 'Todas', Icono: ClipboardList },
  ]
  const estado = params.get('estado') ?? (admin ? 'ENVIADA' : 'ABIERTAS')
  const setEstado = (e) => setParams((p) => { p.set('estado', e); return p }, { replace: true })
  const [labId, setLabId] = useState('todos')
  const [q, setQ] = useState('')
  const [nueva, setNueva] = useState(false)
  const labs = useLabsRef()
  const { datos, error, cargando, recargar } = useDatos(() => api.solicitudes({ estado, laboratorioId: labId }), [estado, labId], { cada: 30000 })
  const lista = useMemo(() => {
    const nq = norm(q)
    return (datos ?? []).filter((s) => !nq || norm(`${s.codigo} ${s.titulo} ${s.tipo} ${s.laboratorio} ${s.creadoPor}`).includes(nq))
  }, [datos, q])
  const extra = !opciones.some((o) => o.valor === estado) ? [{ valor: estado, texto: ESTADOS_SOLICITUD[estado]?.texto ?? estado, Icono: ClipboardList }] : []

  return (
    <div className="space-y-5">
      <Encabezado modulo={`Solicitudes y requerimientos · ${sede?.nombre}`} titulo="Solicitudes"
        detalle={admin ? 'Consolida las necesidades de los laboratorios: revisa, prioriza, decide y registra presupuesto y contratación.'
          : 'Elabora solicitudes con justificación, especificaciones y cotizaciones. El monitor prepara; el coordinador valida y envía.'}>
        <button onClick={recargar} disabled={cargando} className="btn-outline"><RefreshCw className={`h-4 w-4 ${cargando ? 'animate-spin' : ''}`} aria-hidden="true" /> Actualizar</button>
        {puede(usuario, 'SOLICITUD_CREAR') && <button onClick={() => setNueva(true)} className="btn-primary"><Plus className="h-4 w-4" aria-hidden="true" /> Nueva solicitud</button>}
      </Encabezado>

      <FiltroTipo valor={estado} onCambio={setEstado} opciones={[...opciones, ...extra]} etiqueta="Filtrar solicitudes" />
      <section className="card grid gap-3 p-4 sm:grid-cols-[1fr_240px]" aria-label="Filtros">
        <div className="relative"><label htmlFor="s-q" className="sr-only">Buscar</label><Search className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-pizarra" aria-hidden="true" />
          <input id="s-q" className="input pl-9" placeholder="Código, título, tipo o quién la creó" value={q} onChange={(e) => setQ(e.target.value)} /></div>
        <div><label htmlFor="s-l" className="sr-only">Laboratorio</label>
          <select id="s-l" className="input" value={labId} onChange={(e) => setLabId(e.target.value)}><option value="todos">Todos los laboratorios</option>{labs.map((l) => <option key={l.id} value={l.id}>{l.nombre}</option>)}</select></div>
      </section>

      <ErrorCaja mensaje={error} />
      {!datos && !error && <Cargando />}
      {datos && lista.length === 0 && <Vacio icono={ClipboardList} titulo="No hay solicitudes con ese filtro" />}
      {lista.length > 0 && (
        <div className="card overflow-hidden">
          <div className="overflow-x-auto">
            <table className="w-full min-w-[880px]">
              <thead><tr>{estado === 'ENVIADA' && <th className="th">Orden</th>}<th className="th">Solicitud</th><th className="th">Laboratorio</th><th className="th">Prioridad</th><th className="th">Valor estimado</th><th className="th">Estado</th><th className="th">Fecha</th></tr></thead>
              <tbody>
                {lista.map((s) => (
                  <tr key={s.id} className="cursor-pointer hover:bg-slate-50" onClick={() => nav(ruta(`/solicitudes/${s.id}`))}>
                    {estado === 'ENVIADA' && <td className="td font-titulo text-lg font-extrabold">{s.ordenPrioridad ?? '—'}</td>}
                    <td className="td"><Link to={ruta(`/solicitudes/${s.id}`)} className="font-bold text-slate-900 hover:text-usco hover:underline" onClick={(e) => e.stopPropagation()}>{s.titulo}</Link>
                      <p className="text-xs text-pizarra"><span className="font-mono font-bold">{s.codigo}</span> · {s.tipo ?? '—'} · {s.creadoPor} ({s.creadoPorRol})</p></td>
                    <td className="td">{s.laboratorio}</td>
                    <td className="td"><div className="flex flex-col items-start gap-1"><Prioridad valor={s.prioridadAsignada ?? s.prioridadPropuesta} />{s.prioridadAsignada && s.prioridadAsignada !== s.prioridadPropuesta && <span className="text-[11px] text-pizarra">Propuesta: {s.prioridadPropuesta}</span>}</div></td>
                    <td className="td font-semibold">{s.valorEstimado != null ? pesos(s.valorEstimado) : '—'}{s.valorAprobado != null && <p className="text-xs font-normal text-entrada-dark">Aprobado {pesos(s.valorAprobado)}</p>}</td>
                    <td className="td"><EstadoSolicitud estado={s.estado} />{s.estadoContractual && s.estadoContractual !== 'SIN_INICIAR' && <p className="mt-1 text-[11px] text-pizarra">{s.estadoContractualTexto}</p>}</td>
                    <td className="td text-sm text-pizarra">{fmtFecha(s.enviadoEn ?? s.creadoEn)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </div>
      )}

      <SolicitudFormModal abierto={nueva} onCerrar={() => setNueva(false)} onHecho={(r) => nav(ruta(`/solicitudes/${r.resumen.id}`))} />
    </div>
  )
}
