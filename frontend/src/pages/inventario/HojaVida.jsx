import { useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { ArrowLeft, BellPlus, ClipboardEdit, History, Pencil, Settings2, Wrench } from 'lucide-react'
import { api } from '../../api/index.js'
import { useAuth } from '../../context/AuthContext.jsx'
import { useSede } from '../../context/SedeContext.jsx'
import { useDatos } from '../../lib/hooks.js'
import { puede } from '../../lib/constantes.js'
import { fmtFecha, fmtFechaHora } from '../../lib/fecha.js'
import { Cargando, ErrorCaja, EquipoFoto } from '../../components/ui.jsx'
import { Existencias, TipoBadge } from '../../components/Existencias.jsx'
import { Encabezado, EstadoEquipo, EstadoMantenimiento, EstadoNovedad, Validacion } from '../../components/Estados.jsx'
import { ReportarNovedadModal } from '../../components/Novedades.jsx'
import EstadoEquipoModal from '../../components/EstadoEquipoModal.jsx'
import EquipoFormModal from './EquipoFormModal.jsx'
import OperativoModal from './OperativoModal.jsx'
import { MantenimientoFormModal } from './Mantenimientos.jsx'

const PESTANAS = [['mantenimientos', 'Mantenimientos'], ['novedades', 'Novedades'], ['eventos', 'Trazabilidad']]

/** Hoja de vida de un equipo o insumo: ficha técnica, información operativa, mantenimientos, novedades y todos sus eventos. */
export default function HojaVida() {
  const { placa } = useParams()
  const { usuario } = useAuth()
  const { ruta } = useSede()
  const { datos: hv, error, recargar } = useDatos(() => api.hojaVida(placa), [placa], { cada: 0 })
  const [pestana, setPestana] = useState('mantenimientos')
  const [modal, setModal] = useState(null)
  const cerrar = () => setModal(null)

  if (error) return <div className="space-y-4"><Link to={ruta('/inventario')} className="btn-outline"><ArrowLeft className="h-4 w-4" aria-hidden="true" /> Inventario</Link><ErrorCaja mensaje={error} /></div>
  if (!hv) return <Cargando texto="Cargando hoja de vida…" />
  const e = hv.equipo.equipo
  const activo = hv.equipo.activo
  const insumo = e.tipo === 'INSUMO'
  const gestiona = puede(usuario, 'INVENTARIO_GESTIONAR')

  return (
    <div className="space-y-6">
      <Link to={ruta('/inventario')} className="inline-flex items-center gap-1 text-sm font-bold text-usco hover:underline"><ArrowLeft className="h-4 w-4" aria-hidden="true" /> Inventario</Link>
      <Encabezado modulo={`Hoja de vida · ${e.laboratorio}`} titulo={e.nombre} detalle={`${e.placa}${e.marca ? ` · ${e.marca}` : ''}${e.modelo ? ` ${e.modelo}` : ''}${e.numeroSerie ? ` · Serie ${e.numeroSerie}` : ''}`}>
        {activo && puede(usuario, 'NOVEDAD_REPORTAR') && <button className="btn-outline" onClick={() => setModal('novedad')}><BellPlus className="h-4 w-4" aria-hidden="true" /> Registrar novedad</button>}
        {activo && !insumo && puede(usuario, 'MANTENIMIENTO_REQUERIR', 'MANTENIMIENTO_GESTIONAR') && <button className="btn-outline" onClick={() => setModal('mant')}><Wrench className="h-4 w-4" aria-hidden="true" /> Mantenimiento</button>}
        {activo && puede(usuario, 'INVENTARIO_OPERATIVO') && !gestiona && <button className="btn-outline" onClick={() => setModal('op')}><ClipboardEdit className="h-4 w-4" aria-hidden="true" /> Información operativa</button>}
        {activo && !insumo && puede(usuario, 'INVENTARIO_GESTIONAR', 'MANTENIMIENTO_GESTIONAR') && <button className="btn-outline" onClick={() => setModal('estado')}><Settings2 className="h-4 w-4" aria-hidden="true" /> Estado</button>}
        {activo && gestiona && <button className="btn-primary" onClick={() => setModal('editar')}><Pencil className="h-4 w-4" aria-hidden="true" /> Editar ficha</button>}
      </Encabezado>

      {!activo && <ErrorCaja mensaje={`Dado de baja el ${fmtFechaHora(hv.equipo.bajaFecha)}: ${hv.equipo.bajaMotivo}`} />}

      <div className="grid gap-5 lg:grid-cols-[320px_1fr]">
        <div className="card overflow-hidden">
          <EquipoFoto equipo={e} className="h-56" zoom={false} />
          <div className="space-y-2 p-4 text-sm">
            <div className="flex flex-wrap gap-2"><TipoBadge tipo={e.tipo} />{!insumo && <EstadoEquipo estado={e.estado} />}</div>
            <Existencias equipo={e} />
          </div>
        </div>
        <section className="card p-5" aria-label="Ficha técnica">
          <dl className="grid gap-x-6 gap-y-3 sm:grid-cols-2">
            {[['Categoría', e.categoria], ['Laboratorio', e.laboratorio], ['Ubicación', e.ubicacion], ['Estado físico', e.estadoFisico]].map(([t, v]) => (
              <div key={t}><dt className="text-xs font-bold uppercase tracking-wide text-pizarra">{t}</dt><dd className="font-semibold text-slate-900">{v || '—'}</dd></div>
            ))}
          </dl>
          {e.observaciones && <p className="mt-4 rounded-lg bg-amber-50 p-3 text-sm text-amber-900"><strong>Observaciones operativas:</strong> {e.observaciones}</p>}
          {e.descripcion && <p className="mt-4 text-sm text-slate-700">{e.descripcion}</p>}
          <div className="mt-4 grid gap-4 sm:grid-cols-2">
            {e.specs.length > 0 && <div><p className="text-xs font-bold uppercase tracking-wide text-pizarra">Especificaciones</p><ul className="list-inside list-disc text-sm">{e.specs.map((x) => <li key={x}>{x}</li>)}</ul></div>}
            {e.accesorios.length > 0 && <div><p className="text-xs font-bold uppercase tracking-wide text-pizarra">Accesorios</p><ul className="list-inside list-disc text-sm">{e.accesorios.map((x) => <li key={x}>{x}</li>)}</ul></div>}
          </div>
        </section>
      </div>

      <section className="card overflow-hidden">
        <div className="flex flex-wrap gap-1 border-b border-slate-200 p-2" role="tablist" aria-label="Secciones de la hoja de vida">
          {PESTANAS.map(([k, t]) => (
            <button key={k} role="tab" aria-selected={pestana === k} onClick={() => setPestana(k)}
              className={`min-h-[40px] rounded-lg px-4 text-sm font-bold ${pestana === k ? 'bg-usco text-white' : 'text-pizarra-oscuro hover:bg-slate-100'}`}>
              {t} ({k === 'mantenimientos' ? hv.mantenimientos.length : k === 'novedades' ? hv.novedades.length : hv.eventos.length})
            </button>
          ))}
        </div>
        {pestana === 'mantenimientos' && (hv.mantenimientos.length === 0 ? <p className="p-6 text-center text-sm text-pizarra">Sin mantenimientos registrados.</p> : (
          <ul className="divide-y divide-slate-100">
            {hv.mantenimientos.map((m) => (
              <li key={m.id} className="flex flex-col gap-1 px-4 py-3 text-sm sm:flex-row sm:items-start sm:justify-between">
                <div className="min-w-0"><p><span className="font-mono text-xs font-bold text-pizarra">{m.codigo}</span> <strong>{m.tipoTexto}</strong> · {m.descripcion}</p>
                  <p className="text-xs text-pizarra">{m.fechaProgramada ? `Programado: ${fmtFecha(m.fechaProgramada)}` : 'Sin fecha'}{m.fechaRealizada ? ` · Realizado: ${fmtFecha(m.fechaRealizada)}` : ''}{m.responsable ? ` · ${m.responsable}` : ''}</p>
                  {m.resultado && <p className="text-xs text-slate-700">Resultado: {m.resultado}</p>}</div>
                <EstadoMantenimiento estado={m.estado} vencido={m.vencido} />
              </li>
            ))}
          </ul>
        ))}
        {pestana === 'novedades' && (hv.novedades.length === 0 ? <p className="p-6 text-center text-sm text-pizarra">Sin novedades.</p> : (
          <ul className="divide-y divide-slate-100">
            {hv.novedades.map((n) => (
              <li key={n.id} className="flex flex-col gap-1 px-4 py-3 text-sm sm:flex-row sm:items-start sm:justify-between">
                <div className="min-w-0"><p><span className="font-mono text-xs font-bold text-pizarra">#{n.id}</span> <strong>{n.tipoTexto}</strong> · {n.descripcion}</p>
                  <p className="text-xs text-pizarra">{n.reportadoPor} · {fmtFechaHora(n.creadoEn)}{n.validadoPor ? ` · validó ${n.validadoPor}` : ''}</p></div>
                <div className="flex gap-1.5"><Validacion estado={n.validacion} />{n.validacion === 'VALIDADA' && <EstadoNovedad estado={n.estado} />}</div>
              </li>
            ))}
          </ul>
        ))}
        {pestana === 'eventos' && (hv.eventos.length === 0 ? <p className="p-6 text-center text-sm text-pizarra">Sin eventos.</p> : (
          <ol className="relative m-4 space-y-3 border-l-2 border-slate-200 pl-4">
            {hv.eventos.map((h) => (
              <li key={h.id} className="relative text-sm">
                <span className="absolute -left-[23px] top-1 h-3 w-3 rounded-full border-2 border-white bg-usco" aria-hidden="true" />
                <p className="flex flex-wrap items-center gap-1.5 text-xs text-pizarra"><History className="h-3 w-3" aria-hidden="true" /> <strong className="text-slate-800">{h.tipo}</strong> · {fmtFechaHora(h.fecha)} · {h.actor ?? '—'}{h.referencia ? ` · ${h.referencia}` : ''}</p>
                <p className="text-slate-800">{h.detalle}</p>
              </li>
            ))}
          </ol>
        ))}
      </section>

      <ReportarNovedadModal equipo={modal === 'novedad' ? e : null} onCerrar={cerrar} onHecho={recargar} />
      <OperativoModal equipo={modal === 'op' ? e : null} onCerrar={cerrar} onHecho={recargar} />
      <EstadoEquipoModal equipo={modal === 'estado' ? e : null} onCerrar={cerrar} onHecho={recargar} />
      <EquipoFormModal abierto={modal === 'editar'} equipo={e} tipoInicial={e.tipo} onCerrar={cerrar} onHecho={recargar} />
      <MantenimientoFormModal abierto={modal === 'mant'} placaInicial={e.placa} onCerrar={cerrar} onHecho={recargar} />
    </div>
  )
}
