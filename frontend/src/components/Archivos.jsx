import { useRef, useState } from 'react'
import { Download, Eye, FileImage, FileSpreadsheet, FileText, Paperclip, Trash2, X } from 'lucide-react'
import { useToast } from '../context/ToastContext.jsx'
import { descargarArchivo, verArchivo, visible } from '../lib/descarga.js'
import { tamanoLegible } from '../lib/imagen.js'

export const ACEPTA = '.pdf,.jpg,.jpeg,.png,.webp,.docx,.xlsx,image/*,application/pdf'
export const MAX_ARCHIVO = 10 * 1024 * 1024

const icono = (tipo = '') => (tipo.startsWith('image/') ? FileImage : tipo.includes('spreadsheet') ? FileSpreadsheet : FileText)

/** Un archivo privado: ver (imágenes y PDF) o descargar, con la sesión activa. */
export function ArchivoChip({ archivo, etiqueta, onQuitar }) {
  const toast = useToast()
  const Ic = icono(archivo.tipoContenido)
  const abrir = async () => {
    try { if (visible(archivo)) await verArchivo(archivo); else await descargarArchivo(archivo) }
    catch (e) { toast.error('No se pudo abrir el archivo', e.message) }
  }
  const bajar = async () => {
    try { await descargarArchivo(archivo) } catch (e) { toast.error('No se pudo descargar', e.message) }
  }
  return (
    <li className="flex items-center gap-2 rounded-lg border border-slate-200 bg-superficie px-2.5 py-1.5 text-sm">
      <Ic className="h-4 w-4 shrink-0 text-usco" aria-hidden="true" />
      <button type="button" onClick={abrir} className="min-w-0 flex-1 truncate text-left font-semibold text-slate-800 hover:text-usco hover:underline" title={archivo.nombre}>
        {etiqueta && <span className="mr-1 text-xs font-bold uppercase text-pizarra">{etiqueta} ·</span>}{archivo.nombre}
      </button>
      <span className="hidden shrink-0 text-xs text-pizarra sm:inline">{tamanoLegible(archivo.tamano)}</span>
      {visible(archivo) && <button type="button" onClick={abrir} className="rounded p-1 text-pizarra hover:bg-slate-100 hover:text-usco" aria-label={`Ver ${archivo.nombre}`}><Eye className="h-4 w-4" /></button>}
      <button type="button" onClick={bajar} className="rounded p-1 text-pizarra hover:bg-slate-100 hover:text-usco" aria-label={`Descargar ${archivo.nombre}`}><Download className="h-4 w-4" /></button>
      {onQuitar && <button type="button" onClick={onQuitar} className="rounded p-1 text-pizarra hover:bg-alerta-fondo hover:text-alerta-borde" aria-label={`Quitar ${archivo.nombre}`}><Trash2 className="h-4 w-4" /></button>}
    </li>
  )
}

export function ListaArchivos({ archivos, vacio = 'Sin archivos' }) {
  if (!archivos?.length) return <p className="text-sm text-pizarra">{vacio}</p>
  return <ul className="space-y-1.5">{archivos.map((a) => <ArchivoChip key={a.id} archivo={a} />)}</ul>
}

/**
 * Selector de archivos para adjuntar (fotografías, cotizaciones, soportes). Valida tamaño (10 MB) y cantidad;
 * el servidor verifica además el tipo real por su contenido.
 */
export function SelectorArchivos({ archivos, onCambio, maximo = 5, texto = 'Adjuntar fotografías o soportes', id = 'sel-archivos' }) {
  const ref = useRef(null)
  const [error, setError] = useState('')
  const agregar = (lista) => {
    setError('')
    const nuevos = [...lista]
    const grandes = nuevos.filter((f) => f.size > MAX_ARCHIVO)
    if (grandes.length) setError(`${grandes.map((f) => f.name).join(', ')} supera los 10 MB`)
    const validos = nuevos.filter((f) => f.size <= MAX_ARCHIVO)
    const total = [...archivos, ...validos].slice(0, maximo)
    if (archivos.length + validos.length > maximo) setError(`Máximo ${maximo} archivos`)
    onCambio(total)
    if (ref.current) ref.current.value = ''
  }
  return (
    <div>
      <button type="button" onClick={() => ref.current?.click()} disabled={archivos.length >= maximo}
        className="flex w-full items-center justify-center gap-2 rounded-lg border-2 border-dashed border-slate-300 px-3 py-3 text-sm font-bold text-pizarra-oscuro hover:border-usco hover:text-usco disabled:opacity-50">
        <Paperclip className="h-4 w-4" aria-hidden="true" /> {texto} ({archivos.length}/{maximo})
      </button>
      <input id={id} ref={ref} type="file" multiple accept={ACEPTA} className="sr-only" tabIndex={-1} aria-hidden="true" onChange={(e) => agregar(e.target.files ?? [])} />
      <p className="mt-1 text-xs text-pizarra">PDF, JPG, PNG, WEBP, Word o Excel · máximo 10 MB cada uno. En el celular puedes tomar la foto directamente.</p>
      {error && <p role="alert" className="mt-1 text-sm font-semibold text-alerta-borde">{error}</p>}
      {archivos.length > 0 && (
        <ul className="mt-2 space-y-1">
          {archivos.map((f, i) => (
            <li key={`${f.name}-${i}`} className="flex items-center gap-2 rounded-md bg-slate-50 px-2 py-1 text-sm">
              <Paperclip className="h-3.5 w-3.5 shrink-0 text-pizarra" aria-hidden="true" />
              <span className="min-w-0 flex-1 truncate">{f.name}</span>
              <span className="text-xs text-pizarra">{tamanoLegible(f.size)}</span>
              <button type="button" onClick={() => onCambio(archivos.filter((_, j) => j !== i))} className="rounded p-1 text-pizarra hover:text-alerta-borde" aria-label={`Quitar ${f.name}`}><X className="h-4 w-4" /></button>
            </li>
          ))}
        </ul>
      )}
    </div>
  )
}
