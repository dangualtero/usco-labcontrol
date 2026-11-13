import { useMemo, useState } from 'react'
import { Search, SlidersHorizontal, X, CircleCheck, Clock, Wrench, RefreshCw, ArrowRight, Info, Ban, Beaker, Microscope } from 'lucide-react'
import { api } from '../../api/index.js'
import { useAuth } from '../../context/AuthContext.jsx'
import { useDatos, useAhora } from '../../lib/hooks.js'
import { norm } from '../../lib/constantes.js'
import { Cargando, ErrorCaja, EquipoFoto, EstadoBadge, Vacio, Chip, AlertaRetraso } from '../../components/ui.jsx'
import FichaEquipo from './FichaEquipo.jsx'
import SolicitudModal from './SolicitudModal.jsx'
import SolicitudInsumoModal from './SolicitudInsumoModal.jsx'
import { Existencias } from '../../components/Existencias.jsx'
import { FiltroTipo } from '../../components/FiltroTipo.jsx'

const PESTANAS = [
  { valor: 'EQUIPO', texto: 'Equipos (préstamo)', Icono: Microscope },
  { valor: 'INSUMO', texto: 'Insumos (por cantidad)', Icono: Beaker },
]

export default function Catalogo() {
  const { usuario } = useAuth()
  const ahora = useAhora(30000)
  const { datos: base, error, actualizado, cargando, recargar } = useDatos(() => api.equipos(), [], { cada: 20000 })
  const [tipo, setTipo] = useState('EQUIPO')
  // Por laboratorio y luego por placa; solo la pestaña elegida (equipos o insumos)
  const equiposBase = useMemo(() => base && base.filter((e) => (e.tipo ?? 'EQUIPO') === tipo), [base, tipo])
  const equipos = useMemo(() => equiposBase && [...equiposBase].sort((a, b) =>
    a.laboratorio.localeCompare(b.laboratorio) || a.placa.localeCompare(b.placa)), [equiposBase])
  const laboratorios = useMemo(() => [...new Set((base ?? []).map((e) => e.laboratorio))].sort(), [base])
  const insumo = tipo === 'INSUMO'
  const { datos: mis } = useDatos(() => api.misPrestamos(), [], { cada: 15000 })

  const [q, setQ] = useState('')
  const [lab, setLab] = useState('todos')
  const [cat, setCat] = useState('todos')
  const [soloDisp, setSoloDisp] = useState(false)
  const [sugerir, setSugerir] = useState(false)
  const [ficha, setFicha] = useState(null)
  const [solicitud, setSolicitud] = useState(null)
  const [pedido, setPedido] = useState(null)

  const categorias = useMemo(() => [...new Set((equipos ?? []).filter((e) => lab === 'todos' || e.laboratorio === lab).map((e) => e.categoria))].sort(), [equipos, lab])

  const filtrados = useMemo(() => {
    const nq = norm(q)
    return (equipos ?? []).filter((e) =>
      (lab === 'todos' || e.laboratorio === lab) && (cat === 'todos' || e.categoria === cat) &&
      (!soloDisp || (e.tipo === 'INSUMO' ? Number(e.cantidad) > 0 : e.estado === 'DISPONIBLE')) &&
      (!nq || norm(`${e.placa} ${e.nombre} ${e.marca} ${e.modelo} ${e.categoria}`).includes(nq)))
  }, [equipos, q, lab, cat, soloDisp])

  // Autocompletado (PU-15): sugerencias por nombre mientras se escribe
  const sugerencias = useMemo(() => {
    const nq = norm(q)
    if (nq.length < 2) return []
    return (equipos ?? []).filter((e) => norm(`${e.nombre} ${e.marca}`).includes(nq)).slice(0, 5)
  }, [equipos, q])

  const conteo = useMemo(() => {
    const c = { DISPONIBLE: 0, PRESTADO: 0, MANTENIMIENTO: 0 }
    ;(equipos ?? []).forEach((e) => { c[e.estado] += 1 })
    return c
  }, [equipos])

  const vencidos = (mis ?? []).filter((p) => p.vencido)
  const limpiar = () => { setQ(''); setLab('todos'); setCat('todos'); setSoloDisp(false) }

  return (
    <div className="space-y-5">
      {vencidos.length > 0 && (
        <AlertaRetraso prestamos={vencidos} ahora={ahora} titulo="Tienes equipos con devolución vencida. Devuélvelos en ventanilla lo antes posible." />
      )}

      <section className="card overflow-hidden">
        <div className="bg-gradient-to-r from-usco to-usco-dark px-5 py-5 text-white sm:px-7">
          <p className="text-xs font-bold uppercase tracking-widest text-oro-claro">Hola, {usuario.nombre.split(' ')[0]}</p>
          <h1 className="mt-1 text-2xl font-extrabold !text-white sm:text-3xl">Encuentra el equipo o insumo para tu práctica</h1>
          <p className="mt-1 text-sm text-white/85">Los equipos se reservan en 3 pasos: Selección → Horario → Confirmar. Los insumos se piden por cantidad y los entrega el auxiliar.</p>
        </div>

        <div className="space-y-4 p-4 sm:p-5">
          <FiltroTipo valor={tipo} onCambio={(t) => { setTipo(t); setCat('todos') }} opciones={PESTANAS} etiqueta="Equipos o insumos"
            conteo={{ EQUIPO: (base ?? []).filter((e) => e.tipo !== 'INSUMO').length, INSUMO: (base ?? []).filter((e) => e.tipo === 'INSUMO').length }} />
          <div className="relative">
            <label htmlFor="busqueda" className="sr-only">Buscar equipo</label>
            <Search className="pointer-events-none absolute left-3.5 top-1/2 h-5 w-5 -translate-y-1/2 text-pizarra" aria-hidden="true" />
            <input id="busqueda" type="search" value={q} onChange={(e) => { setQ(e.target.value); setSugerir(true) }} onFocus={() => setSugerir(true)} onBlur={() => setTimeout(() => setSugerir(false), 150)}
              placeholder={insumo ? 'Buscar insumo… (ej. Alcohol, cable, guantes)' : 'Buscar por nombre, marca o placa… (ej. Multímetro)'} className="input !min-h-[52px] pl-11 pr-11 text-base" autoComplete="off" />
            {q && <button onClick={() => setQ('')} aria-label="Borrar búsqueda" className="absolute right-2 top-1/2 flex h-9 w-9 -translate-y-1/2 items-center justify-center rounded-lg text-pizarra hover:bg-slate-100"><X className="h-4 w-4" /></button>}
            {sugerir && sugerencias.length > 0 && (
              <ul className="absolute z-20 mt-1 w-full overflow-hidden rounded-xl border border-slate-200 bg-white shadow-xl" role="listbox" aria-label="Sugerencias">
                {sugerencias.map((e) => (
                  <li key={e.placa} role="option" aria-selected="false">
                    <button className="flex w-full items-center justify-between gap-3 px-4 py-2.5 text-left hover:bg-usco-soft" onMouseDown={() => { setQ(e.nombre); setSugerir(false) }}>
                      <span className="min-w-0"><span className="block truncate text-sm font-bold">{e.nombre}</span><span className="text-xs text-pizarra">{e.laboratorio} · {e.categoria}</span></span>
                      <span className="placa shrink-0">{e.placa}</span>
                    </button>
                  </li>
                ))}
              </ul>
            )}
          </div>

          <div className="flex flex-col gap-3 lg:flex-row lg:items-center lg:justify-between">
            <div className="flex flex-wrap gap-2" role="group" aria-label="Filtrar por laboratorio">
              {['todos', ...laboratorios].map((l) => (
                <button key={l} onClick={() => { setLab(l); setCat('todos') }} aria-pressed={lab === l}
                  className={`min-h-[40px] rounded-full border px-4 text-sm font-bold transition-colors ${lab === l ? 'border-usco bg-usco text-white' : 'border-slate-300 bg-white text-pizarra-oscuro hover:border-usco hover:text-usco'}`}>
                  {l === 'todos' ? 'Todos los laboratorios' : l}
                </button>
              ))}
            </div>
            <div className="flex flex-wrap items-center gap-3">
              <div className="flex items-center gap-2">
                <SlidersHorizontal className="h-4 w-4 text-pizarra" aria-hidden="true" />
                <label htmlFor="cat" className="sr-only">Categoría</label>
                <select id="cat" value={cat} onChange={(e) => setCat(e.target.value)} className="input !min-h-[40px] !w-auto py-1.5">
                  <option value="todos">Todas las categorías</option>
                  {categorias.map((c) => <option key={c} value={c}>{c}</option>)}
                </select>
              </div>
              <label className="flex min-h-[40px] cursor-pointer items-center gap-2 text-sm font-semibold text-pizarra-oscuro">
                <input type="checkbox" checked={soloDisp} onChange={(e) => setSoloDisp(e.target.checked)} className="h-4 w-4 rounded border-slate-300 accent-[#8F141B]" /> {insumo ? 'Solo con existencias' : 'Solo disponibles'}
              </label>
            </div>
          </div>
        </div>
      </section>

      <div className="flex flex-wrap items-center justify-between gap-3 text-sm">
        <div className="flex flex-wrap items-center gap-2">
          {insumo ? <Chip className="!bg-sky-50 !text-sky-900"><Beaker className="h-3.5 w-3.5" aria-hidden="true" /> {(equipos ?? []).length} insumos · {(equipos ?? []).filter((e) => Number(e.cantidad) > 0).length} con existencias</Chip> : <>
          <Chip className="!bg-emerald-50 !text-emerald-800"><CircleCheck className="h-3.5 w-3.5" aria-hidden="true" /> {conteo.DISPONIBLE} disponibles</Chip>
          <Chip className="!bg-amber-50 !text-amber-900"><Clock className="h-3.5 w-3.5" aria-hidden="true" /> {conteo.PRESTADO} prestados</Chip>
          <Chip><Wrench className="h-3.5 w-3.5" aria-hidden="true" /> {conteo.MANTENIMIENTO} en mantenimiento</Chip></>}
        </div>
        <span className="inline-flex items-center gap-2 text-xs font-semibold text-pizarra">
          {actualizado && <span>Última consulta: {actualizado.toLocaleTimeString('es-CO', { hour: '2-digit', minute: '2-digit' })}</span>}
          <button onClick={recargar} disabled={cargando} className="btn-outline btn-sm" aria-label="Actualizar disponibilidad de equipos">
            <RefreshCw className={`h-4 w-4 ${cargando ? 'animate-spin' : ''}`} aria-hidden="true" /> Actualizar
          </button>
        </span>
      </div>

      <ErrorCaja mensaje={error} />
      {!equipos && !error && <Cargando texto="Consultando disponibilidad…" />}

      {equipos && filtrados.length === 0 && (
        // PV-07 / PU-07: estado vacío claro, sin listado completo debajo
        <Vacio titulo={insumo ? 'No se encontraron insumos' : 'No se encontraron equipos'} detalle="Prueba con otro término de búsqueda o cambia los filtros de laboratorio y categoría.">
          <button className="btn-outline" onClick={limpiar}>Limpiar filtros</button>
        </Vacio>
      )}

      {filtrados.length > 0 && (
        <ul className="grid gap-4 sm:grid-cols-2 xl:grid-cols-3">
          {filtrados.map((e) => {
            if (e.tipo === 'INSUMO') {
              const hay = Number(e.cantidad) > 0
              return (
                <li key={e.placa} className="card group flex flex-col overflow-hidden transition-shadow hover:shadow-lg">
                  <EquipoFoto equipo={e} className="h-40" />
                  <div className="flex flex-1 flex-col gap-3 p-4">
                    <div className="flex items-center justify-between gap-2"><Chip>{e.laboratorio}</Chip><span className="text-xs font-semibold text-pizarra">{e.categoria}</span></div>
                    <div>
                      <h3 className="text-[17px] font-extrabold leading-snug">{e.nombre}</h3>
                      {e.descripcion && <p className="line-clamp-2 text-sm text-pizarra">{e.descripcion}</p>}
                    </div>
                    <Existencias equipo={e} />
                    <button className="btn-primary btn-sm mt-auto" disabled={!hay} onClick={() => setPedido(e)} aria-label={hay ? `Solicitar ${e.nombre}` : `${e.nombre}: agotado`}>
                      {hay ? <>Solicitar cantidad <ArrowRight className="h-4 w-4" aria-hidden="true" /></> : <><Ban className="h-4 w-4" aria-hidden="true" /> Agotado</>}
                    </button>
                  </div>
                </li>
              )
            }
            const libre = e.estado === 'DISPONIBLE'
            return (
              <li key={e.placa} className="card group flex flex-col overflow-hidden transition-shadow hover:shadow-lg">
                <button onClick={() => setFicha(e.placa)} className="block w-full text-left" aria-label={`Ver ficha técnica de ${e.nombre}`}>
                  <EquipoFoto equipo={e} className="h-48" />
                </button>
                <div className="flex flex-1 flex-col gap-3 p-4">
                  <div className="flex items-center justify-between gap-2">
                    <Chip>{e.laboratorio}</Chip>
                    <span className="text-xs font-semibold text-pizarra">{e.categoria}</span>
                  </div>
                  <div>
                    <h3 className="text-[17px] font-extrabold leading-snug">{e.nombre}</h3>
                    <p className="text-sm text-pizarra">{e.marca} · {e.modelo}</p>
                  </div>
                  <EstadoBadge estado={e.estado} className="self-start" />
                  <div className="mt-auto flex gap-2 pt-1">
                    {/* PV-05: botón desactivado e indica «No disponible»; PU-03: acción primaria sólida / secundaria outline */}
                    <button className="btn-primary btn-sm flex-1" disabled={!libre} onClick={() => setSolicitud(e)} aria-label={libre ? `Solicitar préstamo de ${e.nombre}` : `${e.nombre}: no disponible`}>
                      {libre ? <>Solicitar préstamo <ArrowRight className="h-4 w-4" aria-hidden="true" /></> : <><Ban className="h-4 w-4" aria-hidden="true" /> No disponible</>}
                    </button>
                    <button className="btn-outline btn-sm" onClick={() => setFicha(e.placa)}><Info className="h-4 w-4" aria-hidden="true" /> Ficha</button>
                  </div>
                </div>
              </li>
            )
          })}
        </ul>
      )}

      <FichaEquipo placa={ficha} onCerrar={() => setFicha(null)} onSolicitar={(e) => { setFicha(null); setSolicitud(e) }} />
      <SolicitudModal equipo={solicitud} onCerrar={() => setSolicitud(null)} onCreada={recargar} />
      <SolicitudInsumoModal insumo={pedido} onCerrar={() => setPedido(null)} onCreada={recargar} />
    </div>
  )
}
