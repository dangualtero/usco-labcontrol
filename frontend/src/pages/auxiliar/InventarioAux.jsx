import { useMemo, useState } from 'react'
import { MapPin, RefreshCw, Search, Wrench, CircleCheck } from 'lucide-react'
import { api } from '../../api/index.js'
import { useDatos } from '../../lib/hooks.js'
import { LABORATORIOS, norm } from '../../lib/constantes.js'
import { Cargando, ErrorCaja, EquipoFoto, EstadoBadge, Vacio } from '../../components/ui.jsx'
import EstadoEquipoModal from '../../components/EstadoEquipoModal.jsx'

/** Inventario operativo del auxiliar: consulta del estado y envío a mantenimiento / reactivación de equipos. */
export default function InventarioAux() {
  const { datos, error, cargando, recargar } = useDatos(() => api.equipos(), [], { cada: 20000 })
  const [q, setQ] = useState('')
  const [lab, setLab] = useState('todos')
  const [estado, setEstado] = useState('todos')
  const [modal, setModal] = useState(null)

  const lista = useMemo(() => {
    const nq = norm(q)
    return [...(datos ?? [])].sort((a, b) => LABORATORIOS.indexOf(a.laboratorio) - LABORATORIOS.indexOf(b.laboratorio) || a.placa.localeCompare(b.placa))
      .filter((e) => (lab === 'todos' || e.laboratorio === lab) && (estado === 'todos' || e.estado === estado)
        && (!nq || norm(`${e.placa} ${e.nombre} ${e.marca} ${e.modelo} ${e.numeroSerie ?? ''} ${e.ubicacion ?? ''}`).includes(nq)))
  }, [datos, q, lab, estado])

  return (
    <div className="space-y-5">
      <div className="flex flex-col gap-3 sm:flex-row sm:items-end sm:justify-between">
        <div>
          <p className="text-xs font-bold uppercase tracking-widest text-usco">Panel de Ventanilla</p>
          <h1 className="text-2xl font-extrabold sm:text-3xl">Inventario de laboratorios</h1>
          <p className="text-sm text-pizarra">Consulta la ubicación y el estado de cada equipo. Puedes enviar a mantenimiento los equipos que no estén prestados.</p>
        </div>
        <button onClick={recargar} disabled={cargando} className="btn-outline"><RefreshCw className={`h-4 w-4 ${cargando ? 'animate-spin' : ''}`} aria-hidden="true" /> Actualizar</button>
      </div>

      <section className="card grid gap-3 p-4 sm:grid-cols-[1fr_200px_200px]" aria-label="Filtros">
        <div className="relative"><label htmlFor="ia-q" className="sr-only">Buscar equipo</label><Search className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-pizarra" aria-hidden="true" />
          <input id="ia-q" className="input pl-9" placeholder="Placa, nombre, serie o ubicación" value={q} onChange={(e) => setQ(e.target.value)} /></div>
        <div><label htmlFor="ia-l" className="sr-only">Laboratorio</label>
          <select id="ia-l" className="input" value={lab} onChange={(e) => setLab(e.target.value)}><option value="todos">Todos los laboratorios</option>{LABORATORIOS.map((l) => <option key={l}>{l}</option>)}</select></div>
        <div><label htmlFor="ia-e" className="sr-only">Estado</label>
          <select id="ia-e" className="input" value={estado} onChange={(e) => setEstado(e.target.value)}>
            <option value="todos">Todos los estados</option><option value="DISPONIBLE">Disponible</option><option value="PRESTADO">Prestado</option><option value="MANTENIMIENTO">En mantenimiento</option>
          </select></div>
      </section>

      <ErrorCaja mensaje={error} />
      {!datos && !error && <Cargando />}
      {datos && lista.length === 0 && <Vacio titulo="No se encontraron equipos" detalle="Cambia los filtros de búsqueda." />}

      {lista.length > 0 && (
        <ul className="grid gap-4 sm:grid-cols-2 xl:grid-cols-3">
          {lista.map((e) => (
            <li key={e.placa} className="card group flex flex-col overflow-hidden">
              <EquipoFoto equipo={e} className="h-36" />
              <div className="flex flex-1 flex-col gap-2 p-4">
                <h3 className="font-extrabold leading-snug">{e.nombre}</h3>
                <p className="text-sm text-pizarra">{e.marca} · {e.modelo}{e.numeroSerie ? ` · S/N ${e.numeroSerie}` : ''}</p>
                <p className="flex items-start gap-1.5 text-[13px] text-slate-700"><MapPin className="mt-0.5 h-3.5 w-3.5 shrink-0 text-usco" aria-hidden="true" /> {e.ubicacion ?? 'Ubicación no registrada'}</p>
                <EstadoBadge estado={e.estado} className="self-start" />
                <div className="mt-auto pt-2">
                  {e.estado === 'PRESTADO' ? <p className="text-xs font-semibold text-pizarra">En préstamo: registra su entrada para cambiar el estado.</p> : (
                    <button className="btn-outline btn-sm w-full" onClick={() => setModal(e)}>
                      {e.estado === 'MANTENIMIENTO' ? <><CircleCheck className="h-4 w-4" aria-hidden="true" /> Marcar disponible</> : <><Wrench className="h-4 w-4" aria-hidden="true" /> Enviar a mantenimiento</>}
                    </button>
                  )}
                </div>
              </div>
            </li>
          ))}
        </ul>
      )}

      <EstadoEquipoModal equipo={modal} accion={api.estadoEquipoAux} onCerrar={() => setModal(null)} onHecho={recargar} />
    </div>
  )
}
