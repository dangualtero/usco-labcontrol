import { useEffect, useState } from 'react'
import { Bot, CalendarClock, CircleAlert, Clock, Hourglass, Mail, MailCheck, MailWarning, MinusCircle, Send } from 'lucide-react'
import { api } from '../api/index.js'
import { useAuth } from '../context/AuthContext.jsx'
import { useToast } from '../context/ToastContext.jsx'
import { fmtCantidad, puede, unidadesCompatibles } from '../lib/constantes.js'
import { Campo, ErrorCaja, Modal } from './ui.jsx'
import { SelectorArchivos } from './Archivos.jsx'

/** Tipos de novedad. conteo = conteo físico o consumo de un insumo (lleva cantidad). */
export const TIPOS_NOVEDAD = [
  { valor: 'DANADO', texto: 'Dañado', equipo: true },
  { valor: 'FALLA', texto: 'Falla de funcionamiento', equipo: true },
  { valor: 'PERDIDO', texto: 'Perdido o extraviado', equipo: true, insumo: true },
  { valor: 'AGOTADO', texto: 'Agotado / sin existencias', insumo: true },
  { valor: 'EXISTENCIAS', texto: 'Conteo de existencias', insumo: true, conteo: true },
  { valor: 'CONSUMO', texto: 'Consumo de insumo', insumo: true, conteo: true },
  { valor: 'OTRO', texto: 'Otra novedad', equipo: true, insumo: true },
]

export const AVISO = {
  EN_ESPERA: { icono: Hourglass, texto: 'Se avisará al validarse', clase: 'text-pizarra' },
  PENDIENTE: { icono: Clock, texto: 'Analizando y enviando…', clase: 'text-pizarra' },
  PENDIENTE_RESUMEN: { icono: CalendarClock, texto: 'Irá en el resumen diario', clase: 'text-blue-900' },
  ENVIADA_RESUMEN: { icono: MailCheck, texto: 'Enviado en el resumen diario', clase: 'text-emerald-800' },
  ENVIADA_IA: { icono: Bot, texto: 'Aviso enviado · redactado por IA', clase: 'text-emerald-800' },
  ENVIADA_PLANTILLA: { icono: Mail, texto: 'Aviso enviado con plantilla', clase: 'text-emerald-800' },
  SIN_CONFIGURAR: { icono: MailWarning, texto: 'Correo sin configurar', clase: 'text-amber-700' },
  FALLIDA: { icono: CircleAlert, texto: 'No se pudo enviar el aviso', clase: 'text-alerta-borde' },
  NO_APLICA: { icono: MinusCircle, texto: 'Sin aviso por correo', clase: 'text-pizarra' },
}

/**
 * Registro preliminar de una novedad (falla, daño, pérdida, existencias o consumo) con fotografías, observaciones y
 * soportes. Si la cuenta no puede validar (monitor), queda «Por validar» y se avisa al coordinador del laboratorio.
 */
export function ReportarNovedadModal({ equipo, onCerrar, onHecho }) {
  const toast = useToast()
  const { usuario } = useAuth()
  const valida = puede(usuario, 'NOVEDAD_VALIDAR')
  const insumo = equipo?.tipo === 'INSUMO'
  const tipos = TIPOS_NOVEDAD.filter((t) => (insumo ? t.insumo : t.equipo))
  const [tipo, setTipo] = useState('DANADO')
  const [descripcion, setDescripcion] = useState('')
  const [mantenimiento, setMantenimiento] = useState(true)
  const [cantidad, setCantidad] = useState('')
  const [unidad, setUnidad] = useState('UNIDAD')
  const [archivos, setArchivos] = useState([])
  const [error, setError] = useState('')
  const [enviando, setEnviando] = useState(false)
  const def = TIPOS_NOVEDAD.find((t) => t.valor === tipo)

  useEffect(() => {
    if (!equipo) return
    setTipo(equipo.tipo === 'INSUMO' ? 'CONSUMO' : 'DANADO'); setDescripcion(''); setError(''); setArchivos([]); setCantidad('')
    setUnidad(equipo.unidad ?? 'UNIDAD'); setMantenimiento(equipo.estado === 'DISPONIBLE')
  }, [equipo])

  const enviar = async (e) => {
    e.preventDefault()
    const minimo = def?.conteo ? 3 : 10
    if (descripcion.trim().length < minimo) { setError(def?.conteo ? 'Agrega una observación (mínimo 3 caracteres)' : 'Describe qué pasó (mínimo 10 caracteres)'); return }
    const cant = Number(String(cantidad).replace(',', '.'))
    if (def?.conteo && (cantidad === '' || Number.isNaN(cant) || cant < 0 || (tipo === 'CONSUMO' && cant === 0))) { setError('Indica una cantidad válida'); return }
    setEnviando(true); setError('')
    try {
      const r = await api.reportarNovedad(equipo.placa, {
        tipo, descripcion: descripcion.trim(), mantenimiento: !insumo && mantenimiento,
        cantidad: def?.conteo ? cant : undefined, unidad: def?.conteo ? unidad : undefined, archivos,
      })
      toast.exito(r.validacion === 'PENDIENTE' ? 'Novedad registrada · por validar' : 'Novedad registrada y validada',
        r.validacion === 'PENDIENTE' ? 'El coordinador del laboratorio recibió el aviso para validarla.' : 'Se aplicó al inventario.')
      onHecho?.(); onCerrar()
    } catch (err) { setError(err.message) } finally { setEnviando(false) }
  }

  return (
    <Modal abierto={!!equipo} onCerrar={onCerrar} titulo="Registrar novedad" subtitulo={equipo ? `${equipo.placa} · ${equipo.nombre}` : undefined} acento="bg-alerta-borde" ancho="max-w-xl"
      pie={<>
        <button type="button" className="btn-outline" onClick={onCerrar}>Cancelar</button>
        <button type="submit" form="form-novedad" className="btn-primary" disabled={enviando}><Send className="h-4 w-4" aria-hidden="true" /> {enviando ? 'Enviando…' : valida ? 'Registrar' : 'Enviar al coordinador'}</button>
      </>}>
      {equipo && (
        <form id="form-novedad" onSubmit={enviar} noValidate className="space-y-4">
          <fieldset>
            <legend className="label">¿Qué pasó?</legend>
            <div className="grid gap-2 sm:grid-cols-2">
              {tipos.map((t) => (
                <label key={t.valor} className={`flex min-h-[44px] cursor-pointer items-center gap-2 rounded-lg border-2 px-3 text-sm font-semibold ${tipo === t.valor ? 'border-usco bg-usco-soft text-usco-darker' : 'border-slate-200 text-pizarra'}`}>
                  <input type="radio" name="tipo-novedad" value={t.valor} checked={tipo === t.valor} onChange={() => setTipo(t.valor)} className="accent-[#8F141B]" /> {t.texto}
                </label>
              ))}
            </div>
          </fieldset>
          {def?.conteo && (
            <div className="grid gap-3 rounded-xl border border-slate-200 bg-slate-50 p-3 sm:grid-cols-2">
              <Campo etiqueta={tipo === 'CONSUMO' ? 'Cantidad consumida' : 'Cantidad contada (existencias reales)'} htmlFor="nv-cant"
                ayuda={`Registradas hoy: ${fmtCantidad(equipo.cantidad, equipo.unidad)}`}>
                <input id="nv-cant" type="number" inputMode="decimal" min="0" step="any" className="input" value={cantidad} onChange={(e) => setCantidad(e.target.value)} />
              </Campo>
              <Campo etiqueta="Unidad" htmlFor="nv-uni">
                <select id="nv-uni" className="input" value={unidad} onChange={(e) => setUnidad(e.target.value)}>
                  {unidadesCompatibles(equipo.unidad).map((u) => <option key={u.valor} value={u.valor}>{u.texto}</option>)}
                </select>
              </Campo>
            </div>
          )}
          <Campo etiqueta={def?.conteo ? 'Observación' : 'Descripción'} htmlFor="nv-desc" ayuda="Qué se observó, desde cuándo y cualquier dato útil para la coordinación">
            <textarea id="nv-desc" rows={3} className="input" value={descripcion} onChange={(e) => setDescripcion(e.target.value)} maxLength={1000}
              placeholder={def?.conteo ? 'Ej. Práctica de Circuitos II, grupo 3' : 'Ej. Al encenderlo la pantalla no muestra imagen y huele a quemado.'} />
          </Campo>
          {!insumo && equipo.estado === 'DISPONIBLE' && !def?.conteo && (
            <label className="flex cursor-pointer items-start gap-2.5 text-sm text-slate-700">
              <input type="checkbox" checked={mantenimiento} onChange={(e) => setMantenimiento(e.target.checked)} className="mt-0.5 h-4 w-4 accent-[#8F141B]" />
              <span><strong>Requiere mantenimiento</strong>: al validarse, el equipo pasa a «En mantenimiento» y se crea el requerimiento correctivo.</span>
            </label>
          )}
          <SelectorArchivos archivos={archivos} onCambio={setArchivos} texto="Adjuntar fotografías o soportes" id="nv-archivos" />
          <p className="flex items-start gap-2 rounded-lg bg-slate-100 px-3 py-2.5 text-xs text-pizarra">
            <Bot className="mt-0.5 h-4 w-4 shrink-0 text-usco" aria-hidden="true" />
            {valida
              ? 'Como puedes validar, la novedad se aplica de inmediato. Las fallas, daños y pérdidas se avisan por correo al Administrador (prioridad alta al instante).'
              : 'Es un registro preliminar: el coordinador del laboratorio lo valida. Al validarse se aplica al inventario y se avisa por correo.'}
          </p>
          <ErrorCaja mensaje={error} />
        </form>
      )}
    </Modal>
  )
}
