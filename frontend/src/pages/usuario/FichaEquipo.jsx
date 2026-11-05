import { useEffect, useState } from 'react'
import { ArrowRight, Ban, Boxes, History, ListChecks, MapPin, PackageCheck, ShieldCheck, Tag } from 'lucide-react'
import { api } from '../../api/index.js'
import { Cargando, EquipoFoto, ErrorCaja, EstadoBadge, Modal, Chip } from '../../components/ui.jsx'
import { fmtFechaHora } from '../../lib/fecha.js'

const FISICO = {
  Excelente: 'bg-emerald-50 text-emerald-800 border-emerald-600',
  Bueno: 'bg-emerald-50 text-emerald-800 border-emerald-600',
  Regular: 'bg-amber-50 text-amber-900 border-amber-600',
  Deteriorado: 'bg-alerta-fondo text-alerta-texto border-alerta-borde',
}

function Dato({ icono: Ic, etiqueta, children }) {
  return (
    <div className="rounded-lg bg-slate-50 px-3 py-2 ring-1 ring-slate-200">
      <dt className="flex items-center gap-1.5 text-[11.5px] font-bold uppercase tracking-wide text-pizarra">{Ic && <Ic className="h-3.5 w-3.5" aria-hidden="true" />} {etiqueta}</dt>
      <dd className="mt-0.5 text-sm font-bold text-slate-900">{children || <span className="font-medium text-slate-400">No registrado</span>}</dd>
    </div>
  )
}

/** Ficha técnica completa (PF-05): foto, marca, modelo, serie, ubicación, estado físico, accesorios y disponibilidad. */
export default function FichaEquipo({ placa, onCerrar, onSolicitar }) {
  const [d, setD] = useState(null)
  const [error, setError] = useState('')

  useEffect(() => {
    setD(null); setError('')
    if (!placa) return
    api.equipoDetalle(placa).then(setD).catch((e) => setError(e.message))
  }, [placa])

  const e = d?.equipo
  const libre = e?.estado === 'DISPONIBLE'

  return (
    <Modal abierto={!!placa} onCerrar={onCerrar} titulo={e?.nombre ?? 'Ficha técnica'} subtitulo={e ? `${e.marca ?? ''} ${e.modelo ?? ''}`.trim() : undefined} ancho="max-w-3xl"
      pie={e && <>
        <button className="btn-outline" onClick={onCerrar}>Cerrar</button>
        <button className="btn-primary" disabled={!libre} onClick={() => onSolicitar(e)}>
          {libre ? <>Solicitar préstamo <ArrowRight className="h-4 w-4" aria-hidden="true" /></> : <><Ban className="h-4 w-4" aria-hidden="true" /> No disponible</>}
        </button>
      </>}>
      <ErrorCaja mensaje={error} />
      {!d && !error && <Cargando />}
      {e && (
        <div className="space-y-5">
          <div className="grid gap-4 md:grid-cols-[1.1fr_1fr]">
            <div className="overflow-hidden rounded-xl ring-1 ring-slate-200"><EquipoFoto equipo={e} className="h-64 md:h-[340px]" zoom={false} /></div>
            <div className="space-y-3">
              <div className="flex flex-wrap items-center gap-2">
                <Chip>{e.laboratorio}</Chip><Chip>{e.categoria}</Chip>
                <EstadoBadge estado={e.estado} />
              </div>
              <p className={`text-sm font-bold ${libre ? 'text-emerald-800' : 'text-amber-900'}`}>
                {libre ? 'Disponible para solicitar ahora.' : e.estado === 'PRESTADO' ? 'Prestado actualmente: no se puede solicitar hasta su devolución.' : 'En mantenimiento: no disponible temporalmente.'}
              </p>
              <p className="text-[15px] leading-relaxed text-slate-800">{e.descripcion}</p>
              <dl className="grid grid-cols-2 gap-2">
                <Dato icono={Tag} etiqueta="Marca">{e.marca}</Dato>
                <Dato icono={Tag} etiqueta="Modelo">{e.modelo}</Dato>
                <Dato icono={Boxes} etiqueta="N.º de serie"><span className="font-mono text-[13px]">{e.numeroSerie}</span></Dato>
                <Dato icono={ShieldCheck} etiqueta="Estado físico">
                  {e.estadoFisico && <span className={`inline-flex rounded-full border px-2 py-0.5 text-xs font-bold ${FISICO[e.estadoFisico] ?? FISICO.Bueno}`}>{e.estadoFisico}</span>}
                </Dato>
              </dl>
              <dl><Dato icono={MapPin} etiqueta="Ubicación física">{e.ubicacion}</Dato></dl>
            </div>
          </div>

          <div className="grid gap-4 md:grid-cols-2">
            <section>
              <h3 className="mb-2 flex items-center gap-2 text-sm font-extrabold uppercase tracking-wide text-pizarra-oscuro"><PackageCheck className="h-4 w-4 text-usco" aria-hidden="true" /> Accesorios incluidos en el préstamo</h3>
              {e.accesorios.length === 0 ? <p className="text-sm text-pizarra">Sin accesorios registrados.</p> : (
                <ul className="space-y-1.5">{e.accesorios.map((a) => <li key={a} className="flex items-center gap-2 rounded-lg bg-slate-50 px-3 py-2 text-sm text-slate-800 ring-1 ring-slate-200"><span className="text-entrada" aria-hidden="true">✓</span> {a}</li>)}</ul>
              )}
              <p className="mt-2 text-xs text-pizarra">Debes devolverlos completos; el auxiliar los verifica en la entrega.</p>
            </section>
            <section>
              <h3 className="mb-2 flex items-center gap-2 text-sm font-extrabold uppercase tracking-wide text-pizarra-oscuro"><ListChecks className="h-4 w-4 text-usco" aria-hidden="true" /> Especificaciones</h3>
              <ul className="space-y-1.5">{e.specs.map((s) => <li key={s} className="rounded-lg bg-slate-50 px-3 py-2 text-sm text-slate-800 ring-1 ring-slate-200">{s}</li>)}</ul>
            </section>
          </div>

          <section>
            <h3 className="mb-2 flex items-center gap-2 text-sm font-extrabold uppercase tracking-wide text-pizarra-oscuro"><History className="h-4 w-4 text-usco" aria-hidden="true" /> Historial público del equipo</h3>
            {d.historialPublico.length === 0 ? <p className="text-sm text-pizarra">Aún no registra préstamos.</p> : (
              <div className="overflow-hidden rounded-lg border border-slate-200">
                <table className="w-full">
                  <thead><tr><th className="th">ID anónimo</th><th className="th">Salida</th><th className="th">Entrada</th></tr></thead>
                  <tbody>{d.historialPublico.map((u) => (
                    <tr key={u.idAnonimo + u.salida}><td className="td font-mono text-[13px] font-semibold">{u.idAnonimo}</td><td className="td">{fmtFechaHora(u.salida)}</td><td className="td">{u.entrada ? fmtFechaHora(u.entrada) : 'En curso'}</td></tr>
                  ))}</tbody>
                </table>
              </div>
            )}
            <p className="mt-2 flex items-center gap-1.5 text-xs text-pizarra"><ShieldCheck className="h-3.5 w-3.5" aria-hidden="true" /> Préstamos anonimizados: no se muestran datos de otros usuarios.</p>
          </section>
        </div>
      )}
    </Modal>
  )
}
