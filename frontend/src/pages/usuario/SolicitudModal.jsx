import { useEffect, useMemo, useState } from 'react'
import { ArrowLeft, ArrowRight, CalendarCheck2, CircleCheck, ShieldCheck, TriangleAlert } from 'lucide-react'
import { api } from '../../api/index.js'
import { useAuth } from '../../context/AuthContext.jsx'
import { useToast } from '../../context/ToastContext.jsx'
import { Campo, ErrorCaja, EquipoFoto, Modal, Confirmar } from '../../components/ui.jsx'
import { FRANJAS } from '../../lib/constantes.js'
import { fmtDiaCorto, fmtFranja, toDia, toIso } from '../../lib/fecha.js'

const PASOS = ['Selección', 'Horario', 'Confirmar']

/** Solicitud de préstamo en 3 pasos (PU-04): Selección → Horario → Confirmar. Solo pide código, asignatura y equipo (PS-07). */
export default function SolicitudModal({ equipo, onCerrar, onCreada }) {
  const { usuario } = useAuth()
  const toast = useToast()
  const [paso, setPaso] = useState(0)
  const [asignatura, setAsignatura] = useState('')
  const [docente, setDocente] = useState('')
  const [dia, setDia] = useState(null)
  const [franja, setFranja] = useState(null)
  const [errores, setErrores] = useState({})
  const [errorSrv, setErrorSrv] = useState('')
  const [enviando, setEnviando] = useState(false)
  const [reserva, setReserva] = useState(null)
  const [confirmarSalida, setConfirmarSalida] = useState(false)

  useEffect(() => {
    if (!equipo) return
    setPaso(0); setAsignatura(''); setDocente(''); setDia(null); setFranja(null); setErrores({}); setErrorSrv(''); setReserva(null); setConfirmarSalida(false)
  }, [equipo])

  const dias = useMemo(() => Array.from({ length: 7 }, (_, i) => { const d = new Date(); d.setHours(0, 0, 0, 0); d.setDate(d.getDate() + i); return d }), [equipo])
  const sucio = !reserva && (asignatura.trim() || docente.trim() || dia || franja)

  const intentarCerrar = () => { if (sucio) setConfirmarSalida(true); else onCerrar() }

  const rango = () => {
    if (!dia || !franja) return null
    const [hi, mi] = franja.inicio.split(':').map(Number); const [hf, mf] = franja.fin.split(':').map(Number)
    const ini = new Date(dia); ini.setHours(hi, mi, 0, 0)
    const fin = new Date(dia); fin.setHours(hf, mf, 0, 0)
    return { ini, fin }
  }
  const pasada = (d, f) => { const [h, m] = f.fin.split(':').map(Number); const x = new Date(d); x.setHours(h, m, 0, 0); return x <= new Date() }

  const siguiente = () => {
    setErrorSrv('')
    if (paso === 0) {
      if (!asignatura.trim()) { setErrores({ asignatura: 'Indica la asignatura para la cual necesitas el equipo' }); return }
      setErrores({}); setPaso(1)
    } else if (paso === 1) {
      if (!dia || !franja) { setErrores({ franja: 'Seleccione fecha y franja horaria para continuar' }); return } // PV-03
      setErrores({}); setPaso(2)
    }
  }

  const confirmar = async () => {
    const r = rango()
    setEnviando(true); setErrorSrv('')
    try {
      const p = await api.reservar({ placa: equipo.placa, asignatura: asignatura.trim(), docente: docente.trim(), inicio: toIso(r.ini), fin: toIso(r.fin) })
      setReserva(p)
      toast.exito('Reserva confirmada', `Código ${p.codigo} · ${equipo.placa}`)
      onCreada?.()
    } catch (e) { setErrorSrv(e.message) } finally { setEnviando(false) }
  }

  const r = rango()
  return (
    <>
      <Modal abierto={!!equipo} onCerrar={reserva ? onCerrar : intentarCerrar} ancho="max-w-xl"
        titulo={reserva ? 'Reserva confirmada' : 'Solicitar préstamo'} subtitulo={reserva ? undefined : equipo?.nombre}
        acento={reserva ? 'bg-entrada' : 'bg-usco'}
        pie={reserva ? <button className="btn-primary" onClick={onCerrar}>Listo</button> : <>
          {paso > 0 ? <button className="btn-outline" onClick={() => { setPaso(paso - 1); setErrores({}); setErrorSrv('') }}><ArrowLeft className="h-4 w-4" aria-hidden="true" /> Atrás</button>
            : <button className="btn-outline" onClick={intentarCerrar}>Cancelar</button>}
          {paso < 2 ? <button className="btn-primary" onClick={siguiente}>Continuar <ArrowRight className="h-4 w-4" aria-hidden="true" /></button>
            : <button className="btn-primary" onClick={confirmar} disabled={enviando}><CalendarCheck2 className="h-4 w-4" aria-hidden="true" /> {enviando ? 'Confirmando…' : 'Confirmar reserva'}</button>}
        </>}>
        {equipo && reserva && (
          <div className="space-y-4 text-center">
            <div className="mx-auto flex h-16 w-16 items-center justify-center rounded-full bg-emerald-50"><CircleCheck className="h-9 w-9 text-entrada" aria-hidden="true" /></div>
            <div>
              <p className="text-sm font-semibold text-pizarra">Código de reserva</p>
              <p className="font-mono text-3xl font-extrabold tracking-wider text-usco">{reserva.codigo}</p>
            </div>
            <dl className="mx-auto grid max-w-sm grid-cols-[auto_1fr] gap-x-4 gap-y-1.5 rounded-xl bg-slate-50 p-4 text-left text-sm ring-1 ring-slate-200">
              <dt className="font-semibold text-pizarra">Equipo</dt><dd className="font-bold">{equipo.nombre}</dd>
              <dt className="font-semibold text-pizarra">Placa</dt><dd className="font-mono font-bold">{equipo.placa}</dd>
              <dt className="font-semibold text-pizarra">Laboratorio</dt><dd className="font-bold">{equipo.laboratorio}</dd>
              <dt className="font-semibold text-pizarra">Franja</dt><dd className="font-bold">{fmtFranja(reserva.franjaInicio, reserva.franjaFin)}</dd>
            </dl>
            <p className="text-sm text-pizarra">Preséntate en la ventanilla del laboratorio con tu documento. El auxiliar registrará la salida del equipo.</p>
          </div>
        )}

        {equipo && !reserva && (
          <div className="space-y-5">
            <ol className="flex items-center gap-2" aria-label="Progreso de la solicitud">
              {PASOS.map((t, i) => (
                <li key={t} className="flex flex-1 items-center gap-2" aria-current={i === paso ? 'step' : undefined}>
                  <span className={`flex h-7 w-7 shrink-0 items-center justify-center rounded-full text-xs font-extrabold ${i < paso ? 'bg-entrada text-white' : i === paso ? 'bg-usco text-white' : 'bg-slate-200 text-pizarra'}`}>{i < paso ? '✓' : i + 1}</span>
                  <span className={`text-xs font-bold sm:text-sm ${i === paso ? 'text-usco' : 'text-pizarra'}`}>{t}</span>
                  {i < PASOS.length - 1 && <span className="h-px flex-1 bg-slate-200" aria-hidden="true" />}
                </li>
              ))}
            </ol>

            <div className="flex items-center gap-3 overflow-hidden rounded-xl bg-slate-50 ring-1 ring-slate-200">
              <EquipoFoto equipo={equipo} className="h-20 w-24 shrink-0" placa={false} zoom={false} />
              <div className="min-w-0 py-2 pr-3">
                <p className="truncate font-extrabold">{equipo.nombre}</p>
                <p className="text-sm text-pizarra"><span className="font-mono font-semibold">{equipo.placa}</span> · {equipo.laboratorio}</p>
              </div>
            </div>

            {paso === 0 && (
              <div className="space-y-4">
                <Campo etiqueta="Código del solicitante" htmlFor="codigo" ayuda="Tomado de tu cuenta institucional.">
                  <input id="codigo" className="input bg-slate-50" value={usuario.documento} readOnly />
                </Campo>
                <Campo etiqueta="Asignatura" htmlFor="asignatura" error={errores.asignatura}>
                  <input id="asignatura" className={`input ${errores.asignatura ? 'input-error' : ''}`} value={asignatura} onChange={(e) => setAsignatura(e.target.value)} placeholder="Ej. Circuitos Electrónicos II" maxLength={120} />
                </Campo>
                <Campo etiqueta="Docente de la asignatura (opcional)" htmlFor="docente">
                  <input id="docente" className="input" value={docente} onChange={(e) => setDocente(e.target.value)} placeholder="Ej. Ing. Marcela Ortiz" maxLength={120} />
                </Campo>
                <p className="flex items-start gap-2 rounded-lg bg-emerald-50 px-3 py-2 text-xs font-semibold text-emerald-900"><ShieldCheck className="mt-0.5 h-4 w-4 shrink-0" aria-hidden="true" /> Solo pedimos los datos académicos mínimos de la solicitud. No solicitamos datos sensibles (Ley 1581 de 2012).</p>
              </div>
            )}

            {paso === 1 && (
              <div className={`space-y-4 rounded-xl p-3 ${errores.franja ? 'ring-2 ring-alerta-borde bg-alerta-fondo' : ''}`}>
                <fieldset>
                  <legend className="label">Fecha</legend>
                  <div className="flex gap-2 overflow-x-auto pb-1" role="radiogroup" aria-label="Fecha de la reserva">
                    {dias.map((d, i) => {
                      const sel = dia && toDia(dia) === toDia(d)
                      return (
                        <button key={i} role="radio" aria-checked={!!sel} onClick={() => { setDia(d); setFranja(null); setErrores({}) }}
                          className={`min-h-[52px] min-w-[84px] shrink-0 rounded-xl border-2 px-2 text-sm font-bold ${sel ? 'border-usco bg-usco text-white' : 'border-slate-200 bg-white text-pizarra-oscuro hover:border-usco'}`}>
                          {i === 0 ? 'Hoy' : fmtDiaCorto(d)}
                        </button>
                      )
                    })}
                  </div>
                </fieldset>
                <fieldset>
                  <legend className="label">Franja horaria</legend>
                  <div className="grid grid-cols-2 gap-2 sm:grid-cols-3" role="radiogroup" aria-label="Franja horaria">
                    {FRANJAS.map((f) => {
                      const off = !dia || pasada(dia, f)
                      const sel = franja?.inicio === f.inicio
                      return (
                        <button key={f.inicio} role="radio" aria-checked={sel} disabled={off} onClick={() => { setFranja(f); setErrores({}) }}
                          className={`min-h-[48px] rounded-xl border-2 text-sm font-bold ${sel ? 'border-usco bg-usco text-white' : 'border-slate-200 bg-white text-pizarra-oscuro hover:border-usco disabled:cursor-not-allowed disabled:opacity-40 disabled:hover:border-slate-200'}`}>
                          {f.inicio} – {f.fin}
                        </button>
                      )
                    })}
                  </div>
                  {!dia && <p className="mt-2 text-xs text-pizarra">Primero elige una fecha.</p>}
                </fieldset>
                {errores.franja && <p role="alert" className="flex items-center gap-1.5 text-sm font-bold text-alerta-borde"><TriangleAlert className="h-4 w-4" aria-hidden="true" /> {errores.franja}</p>}
              </div>
            )}

            {paso === 2 && r && (
              <div className="space-y-3">
                <h3 className="text-sm font-extrabold uppercase tracking-wide text-pizarra-oscuro">Resumen de tu solicitud</h3>
                <dl className="grid grid-cols-[auto_1fr] gap-x-4 gap-y-2 rounded-xl bg-slate-50 p-4 text-sm ring-1 ring-slate-200">
                  <dt className="font-semibold text-pizarra">Solicitante</dt><dd className="font-bold">{usuario.nombre} · {usuario.documento}</dd>
                  <dt className="font-semibold text-pizarra">Asignatura</dt><dd className="font-bold">{asignatura}{docente.trim() ? ` · ${docente.trim()}` : ''}</dd>
                  <dt className="font-semibold text-pizarra">Equipo</dt><dd className="font-bold">{equipo.nombre} ({equipo.placa})</dd>
                  <dt className="font-semibold text-pizarra">Franja</dt><dd className="font-bold">{fmtFranja(toIso(r.ini), toIso(r.fin))}</dd>
                </dl>
              </div>
            )}
            <ErrorCaja mensaje={errorSrv} />
          </div>
        )}
      </Modal>

      <Confirmar abierto={confirmarSalida} titulo="¿Descartar la solicitud?" mensaje="Los datos no guardados se perderán" textoOk="Sí, descartar" textoCancelar="Seguir editando"
        onCancelar={() => setConfirmarSalida(false)} onOk={() => { setConfirmarSalida(false); onCerrar() }} />
    </>
  )
}
