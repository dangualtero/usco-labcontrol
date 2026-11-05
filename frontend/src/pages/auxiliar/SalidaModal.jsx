import { useEffect, useState } from 'react'
import { ArrowUpRight, Search, UserRound } from 'lucide-react'
import { api } from '../../api/index.js'
import { useToast } from '../../context/ToastContext.jsx'
import { REGEX_PLACA } from '../../lib/constantes.js'
import { Campo, Confirmar, ErrorCaja, EstadoBadge, Modal } from '../../components/ui.jsx'

const campoDe = (m = '') => {
  const t = m.toLowerCase()
  if (t.includes('límite') || t.includes('reservado') || t.includes('duración')) return 'global'
  if (t.includes('formato') || t.includes('equipo') || t.includes('placa')) return 'placa'
  if (t.includes('documento') || t.includes('estudiante')) return 'documento'
  return 'global'
}

/** Registrar Salida / préstamo (PF-08, PV-04, PV-10, PU-07). Botón primario en AZUL institucional (PA-07). */
export default function SalidaModal({ abierto, inicial, onCerrar, onHecho }) {
  const toast = useToast()
  const [documento, setDocumento] = useState('')
  const [placa, setPlaca] = useState('')
  const [horas, setHoras] = useState(2)
  const [persona, setPersona] = useState(null)
  const [equipo, setEquipo] = useState(null)
  const [errores, setErrores] = useState({})
  const [enviando, setEnviando] = useState(false)
  const [descartar, setDescartar] = useState(false)

  useEffect(() => {
    if (!abierto) return
    setDocumento(inicial?.documento ?? ''); setPlaca(inicial?.placa ?? ''); setHoras(2); setPersona(null); setEquipo(null); setErrores({}); setDescartar(false)
    if (inicial?.documento) buscarPersona(inicial.documento)
    if (inicial?.placa) buscarEquipo(inicial.placa)
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [abierto, inicial])

  const buscarPersona = async (doc) => {
    if (!doc.trim()) { setPersona(null); return }
    try { setPersona(await api.solicitudesUsuario(doc.trim())); setErrores((e) => ({ ...e, documento: undefined })) }
    catch (e) { setPersona(null); setErrores((x) => ({ ...x, documento: e.message })) }
  }
  const buscarEquipo = async (pl) => {
    const p = pl.trim().toUpperCase()
    if (!REGEX_PLACA.test(p)) { setEquipo(null); return }
    try { setEquipo((await api.equipoDetalle(p)).equipo); setErrores((e) => ({ ...e, placa: undefined })) }
    catch (e) { setEquipo(null); setErrores((x) => ({ ...x, placa: e.message })) }
  }

  const sucio = !!(documento || placa)
  const cerrar = () => (sucio && !enviando ? setDescartar(true) : onCerrar())

  const enviar = async () => {
    const e = {}
    if (!documento.trim()) e.documento = 'Ingresa el código estudiantil o el documento'
    const pl = placa.trim().toUpperCase()
    if (!pl) e.placa = 'Ingresa o escanea la placa del equipo'
    else if (!REGEX_PLACA.test(pl)) e.placa = 'Usa el formato USCO-EQ-000. Ejemplo: USCO-EQ-102'
    setErrores(e)
    if (Object.keys(e).length) return
    setEnviando(true)
    try {
      const p = await api.salida({ placa: pl, documento: documento.trim(), horas: Number(horas) })
      toast.exito(`Salida registrada: Estado Prestado`, `${p.placa} · ${p.equipoNombre} → ${p.solicitanteNombre}`)
      onHecho?.(); onCerrar()
    } catch (err) {
      const c = campoDe(err.message)
      setErrores({ [c]: err.message })
    } finally { setEnviando(false) }
  }

  return (
    <>
      <Modal abierto={abierto} onCerrar={cerrar} titulo="Registrar Salida" subtitulo="Entrega de un equipo en préstamo" acento="bg-salida"
        pie={<>
          <button className="btn-outline" onClick={cerrar}>Cancelar</button>
          <button className="btn-salida" onClick={enviar} disabled={enviando}><ArrowUpRight className="h-4 w-4" aria-hidden="true" /> {enviando ? 'Registrando…' : 'Registrar Salida'}</button>
        </>}>
        <div className="space-y-4">
          <Campo etiqueta="Código estudiantil o documento" htmlFor="s-doc" error={errores.documento}>
            <div className="flex gap-2">
              <input id="s-doc" className={`input ${errores.documento ? 'input-error' : ''}`} value={documento} onChange={(e) => { setDocumento(e.target.value); setPersona(null) }}
                onBlur={() => buscarPersona(documento)} onKeyDown={(e) => { if (e.key === 'Enter') { e.preventDefault(); buscarPersona(documento) } }} inputMode="numeric" placeholder="Ej. 20211100456 o 1075123456" />
              <button type="button" className="btn-outline" onClick={() => buscarPersona(documento)} aria-label="Buscar usuario"><Search className="h-4 w-4" /></button>
            </div>
          </Campo>
          {persona && (
            <div className="flex items-center gap-3 rounded-lg bg-slate-50 px-3 py-2.5 ring-1 ring-slate-200 aparecer">
              <span className="flex h-9 w-9 items-center justify-center rounded-full bg-salida/10 text-salida"><UserRound className="h-5 w-5" aria-hidden="true" /></span>
              <div className="text-sm"><p className="font-bold">{persona.nombre}</p><p className="text-pizarra">{persona.programa} · {persona.activos} de {persona.limite} equipos en préstamo</p></div>
            </div>
          )}

          <Campo etiqueta="Placa del equipo" htmlFor="s-placa" error={errores.placa} ayuda="Escanea o digita la placa física. Formato USCO-EQ-000.">
            <input id="s-placa" className={`input font-mono uppercase ${errores.placa ? 'input-error' : ''}`} value={placa} onChange={(e) => { setPlaca(e.target.value.toUpperCase()); setEquipo(null); buscarEquipo(e.target.value) }}
              placeholder="USCO-EQ-102" maxLength={11} autoCapitalize="characters" />
          </Campo>
          {equipo && (
            <div className="flex items-center justify-between gap-3 rounded-lg bg-slate-50 px-3 py-2.5 ring-1 ring-slate-200 aparecer">
              <div className="min-w-0 text-sm"><p className="truncate font-bold">{equipo.nombre}</p><p className="text-pizarra">{equipo.laboratorio}</p></div>
              <EstadoBadge estado={equipo.estado} />
            </div>
          )}

          <Campo etiqueta="Duración del préstamo" htmlFor="s-horas" ayuda="Pasado este tiempo se genera una alerta de devolución vencida.">
            <select id="s-horas" className="input" value={horas} onChange={(e) => setHoras(e.target.value)}>
              {[1, 2, 3, 4, 6, 8].map((h) => <option key={h} value={h}>{h} {h === 1 ? 'hora' : 'horas'}</option>)}
            </select>
          </Campo>
          <ErrorCaja mensaje={errores.global} />
        </div>
      </Modal>
      <Confirmar abierto={descartar} titulo="¿Cancelar el registro?" mensaje="Los datos no guardados se perderán" textoOk="Sí, cancelar" textoCancelar="Seguir editando"
        onCancelar={() => setDescartar(false)} onOk={() => { setDescartar(false); onCerrar() }} />
    </>
  )
}
