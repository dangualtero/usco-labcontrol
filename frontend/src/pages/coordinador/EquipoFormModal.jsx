import { useEffect, useRef, useState } from 'react'
import { Save, ImageOff, ImagePlus, Link2, Loader2, RefreshCw, Trash2, Upload } from 'lucide-react'
import { api } from '../../api/index.js'
import { useToast } from '../../context/ToastContext.jsx'
import { LABORATORIOS, REGEX_PLACA } from '../../lib/constantes.js'
import { Campo, ErrorCaja, Modal } from '../../components/ui.jsx'
import { prepararFoto, tamanoLegible } from '../../lib/imagen.js'
import { useSede } from '../../context/SedeContext.jsx'

const VACIO = { placa: '', nombre: '', marca: '', modelo: '', numeroSerie: '', categoria: '', laboratorio: 'Electrónica', ubicacion: '',
  descripcion: '', specs: '', accesorios: '', estadoFisico: 'Bueno', fotoUrl: '', estado: 'DISPONIBLE' }
const lineas = (t) => t.split('\n').map((x) => x.trim()).filter(Boolean)

/** Alta y edición de equipos (CRUD de inventario). `equipo` null = nuevo. */
export default function EquipoFormModal({ abierto, equipo, onCerrar, onHecho }) {
  const toast = useToast()
  const { sede } = useSede()
  const nuevo = !equipo
  const [f, setF] = useState(VACIO)
  const [errores, setErrores] = useState({})
  const [errorSrv, setErrorSrv] = useState('')
  const [enviando, setEnviando] = useState(false)
  const [fotoMala, setFotoMala] = useState(false)
  const [subiendo, setSubiendo] = useState(false)
  const [errFoto, setErrFoto] = useState('')
  const [infoFoto, setInfoFoto] = useState('')
  const [conUrl, setConUrl] = useState(false)
  const [arrastrando, setArrastrando] = useState(false)
  const entrada = useRef(null)

  useEffect(() => {
    if (!abierto) return
    setErrores({}); setErrorSrv(''); setFotoMala(false); setErrFoto(''); setInfoFoto(''); setArrastrando(false)
    setConUrl(!!equipo?.fotoUrl && !equipo.fotoUrl.startsWith('/api/imagenes/'))
    setF(equipo ? { ...VACIO, ...equipo, marca: equipo.marca ?? '', modelo: equipo.modelo ?? '', numeroSerie: equipo.numeroSerie ?? '', ubicacion: equipo.ubicacion ?? '',
      descripcion: equipo.descripcion ?? '', fotoUrl: equipo.fotoUrl ?? '', estadoFisico: equipo.estadoFisico ?? 'Bueno',
      specs: (equipo.specs ?? []).join('\n'), accesorios: (equipo.accesorios ?? []).join('\n') } : VACIO)
  }, [abierto, equipo])

  const set = (k) => (e) => { setF((x) => ({ ...x, [k]: e.target.value })); if (k === 'fotoUrl') setFotoMala(false) }

  /** Foto elegida desde el computador o el celular (galería o cámara): se reduce y se sube al servidor. */
  const subir = async (archivo) => {
    if (!archivo) return
    setErrFoto(''); setSubiendo(true)
    try {
      const listo = await prepararFoto(archivo)
      const r = await api.subirImagen(listo)
      setF((x) => ({ ...x, fotoUrl: r.url })); setFotoMala(false); setConUrl(false)
      setInfoFoto(`${archivo.name || 'Foto'} · ${tamanoLegible(r.tamano)}`)
      setErrores((x) => ({ ...x, fotoUrl: undefined }))
    } catch (err) { setErrFoto(err.message) } finally {
      setSubiendo(false)
      if (entrada.current) entrada.current.value = ''
    }
  }
  const soltar = (e) => { e.preventDefault(); setArrastrando(false); subir(e.dataTransfer.files?.[0]) }

  const guardar = async (e) => {
    e.preventDefault()
    if (subiendo) return
    const er = {}
    if (nuevo && !REGEX_PLACA.test(f.placa.trim().toUpperCase())) er.placa = 'Usa el formato USCO-EQ-000. Ejemplo: USCO-EQ-102'
    if (!f.nombre.trim()) er.nombre = 'El nombre es obligatorio'
    if (!f.categoria.trim()) er.categoria = 'La categoría es obligatoria'
    const foto = f.fotoUrl.trim()
    if (foto && !/^(https?:\/\/|\/images\/|\/api\/imagenes\/)/.test(foto)) er.fotoUrl = 'Debe ser una URL http(s) o una ruta /images/…'
    setErrores(er)
    if (Object.keys(er).length) return
    setEnviando(true); setErrorSrv('')
    const cuerpo = { ...f, placa: f.placa.trim().toUpperCase(), fotoUrl: foto, specs: lineas(f.specs), accesorios: lineas(f.accesorios) }
    try {
      if (nuevo) await api.crearEquipo(cuerpo); else await api.actualizarEquipo(equipo.placa, cuerpo)
      toast.exito(nuevo ? 'Equipo creado' : 'Equipo actualizado', `${cuerpo.placa || equipo.placa} · ${cuerpo.nombre}`)
      onHecho?.(); onCerrar()
    } catch (err) { setErrorSrv(err.message) } finally { setEnviando(false) }
  }

  return (
    <Modal abierto={abierto} onCerrar={onCerrar} ancho="max-w-3xl" titulo={nuevo ? 'Nuevo equipo' : 'Editar equipo'} subtitulo={nuevo ? `Se agregará al inventario y al catálogo de la ${sede?.nombre ?? 'sede'}` : `${equipo?.placa} · ${equipo?.nombre}`}
      pie={<>
        <button type="button" className="btn-outline" onClick={onCerrar}>Cancelar</button>
        <button type="submit" form="form-equipo" className="btn-primary" disabled={enviando || subiendo}><Save className="h-4 w-4" aria-hidden="true" /> {enviando ? 'Guardando…' : nuevo ? 'Crear equipo' : 'Guardar cambios'}</button>
      </>}>
      <form id="form-equipo" onSubmit={guardar} noValidate className="grid gap-4 md:grid-cols-2">
        <Campo etiqueta="Placa (código)" htmlFor="q-placa" error={errores.placa} ayuda={nuevo ? 'Formato USCO-EQ-000' : 'La placa no se puede modificar'}>
          <input id="q-placa" className={`input font-mono uppercase ${errores.placa ? 'input-error' : ''}`} value={f.placa} onChange={set('placa')} disabled={!nuevo} maxLength={11} placeholder="USCO-EQ-410" />
        </Campo>
        <Campo etiqueta="Nombre del equipo" htmlFor="q-nombre" error={errores.nombre}><input id="q-nombre" className={`input ${errores.nombre ? 'input-error' : ''}`} value={f.nombre} onChange={set('nombre')} maxLength={120} /></Campo>
        <Campo etiqueta="Marca" htmlFor="q-marca"><input id="q-marca" className="input" value={f.marca} onChange={set('marca')} maxLength={60} /></Campo>
        <Campo etiqueta="Modelo" htmlFor="q-modelo"><input id="q-modelo" className="input" value={f.modelo} onChange={set('modelo')} maxLength={60} /></Campo>
        <Campo etiqueta="Número de serie" htmlFor="q-serie"><input id="q-serie" className="input font-mono" value={f.numeroSerie} onChange={set('numeroSerie')} maxLength={60} /></Campo>
        <Campo etiqueta="Categoría" htmlFor="q-cat" error={errores.categoria}><input id="q-cat" className={`input ${errores.categoria ? 'input-error' : ''}`} value={f.categoria} onChange={set('categoria')} maxLength={60} placeholder="Ej. Osciloscopios" /></Campo>
        <Campo etiqueta="Laboratorio" htmlFor="q-lab">
          <select id="q-lab" className="input" value={f.laboratorio} onChange={set('laboratorio')}>{LABORATORIOS.map((l) => <option key={l}>{l}</option>)}</select>
        </Campo>
        <Campo etiqueta="Ubicación física exacta" htmlFor="q-ubi"><input id="q-ubi" className="input" value={f.ubicacion} onChange={set('ubicacion')} maxLength={160} placeholder="Bloque · Laboratorio · Mesa/estante" /></Campo>
        <Campo etiqueta="Estado físico" htmlFor="q-fis">
          <select id="q-fis" className="input" value={f.estadoFisico} onChange={set('estadoFisico')}>{['Excelente', 'Bueno', 'Regular', 'Deteriorado'].map((x) => <option key={x}>{x}</option>)}</select>
        </Campo>
        {nuevo && (
          <Campo etiqueta="Estado inicial" htmlFor="q-est">
            <select id="q-est" className="input" value={f.estado} onChange={set('estado')}><option value="DISPONIBLE">Disponible</option><option value="MANTENIMIENTO">En mantenimiento</option></select>
          </Campo>
        )}
        <div className="md:col-span-2">
          <p className="label">Fotografía del equipo</p>
          <div className="flex flex-col gap-3 sm:flex-row">
            {/* Vista previa */}
            <div className="flex h-36 w-full shrink-0 items-center justify-center overflow-hidden rounded-lg bg-slate-100 ring-1 ring-slate-200 sm:w-52">
              {subiendo ? <span className="flex flex-col items-center gap-1 text-xs font-semibold text-pizarra"><Loader2 className="h-6 w-6 animate-spin" aria-hidden="true" /> Subiendo…</span>
                : !f.fotoUrl.trim() || errores.fotoUrl ? <span className="flex flex-col items-center gap-1 text-xs font-semibold text-pizarra"><ImagePlus className="h-6 w-6" aria-hidden="true" /> Sin fotografía</span>
                : fotoMala ? <span className="flex flex-col items-center gap-1 text-xs font-semibold text-pizarra"><ImageOff className="h-5 w-5" aria-hidden="true" /> No se pudo cargar</span>
                : <img src={f.fotoUrl.trim()} alt="Vista previa de la fotografía" className="h-full w-full object-cover" onError={() => setFotoMala(true)} />}
            </div>
            {/* Zona para elegir o arrastrar el archivo */}
            <div className="flex flex-1 flex-col gap-2">
              <button type="button" onClick={() => entrada.current?.click()} disabled={subiendo}
                onDragOver={(e) => { e.preventDefault(); setArrastrando(true) }} onDragLeave={() => setArrastrando(false)} onDrop={soltar}
                className={`flex min-h-[100px] flex-1 flex-col items-center justify-center gap-1 rounded-lg border-2 border-dashed px-4 py-3 text-center transition-colors ${arrastrando ? 'border-usco bg-usco-soft' : 'border-slate-300 hover:border-usco hover:bg-slate-50'}`}>
                <Upload className="h-5 w-5 text-usco" aria-hidden="true" />
                <span className="text-sm font-bold text-slate-800">{f.fotoUrl.trim() ? 'Cambiar fotografía' : 'Subir fotografía'}</span>
                <span className="text-xs text-pizarra">Desde el computador o el celular (galería o cámara) · JPG, PNG o WEBP · se ajusta automáticamente</span>
              </button>
              <input ref={entrada} type="file" accept="image/*" className="sr-only" tabIndex={-1} aria-hidden="true" onChange={(e) => subir(e.target.files?.[0])} />
              {infoFoto && !errFoto && <p className="text-xs font-semibold text-emerald-800">Subida: {infoFoto}</p>}
              <ErrorCaja mensaje={errFoto} />
              <div className="flex flex-wrap gap-2">
                {f.fotoUrl.trim() && <button type="button" className="btn-outline btn-sm" onClick={() => { setF((x) => ({ ...x, fotoUrl: '' })); setInfoFoto(''); setFotoMala(false) }}><Trash2 className="h-4 w-4" aria-hidden="true" /> Quitar foto</button>}
                <button type="button" className="btn-outline btn-sm" onClick={() => setConUrl((v) => !v)} aria-expanded={conUrl} aria-controls="q-foto-url"><Link2 className="h-4 w-4" aria-hidden="true" /> {conUrl ? 'Ocultar URL' : 'O usar una URL'}</button>
                {fotoMala && <button type="button" className="btn-outline btn-sm" onClick={() => setFotoMala(false)}><RefreshCw className="h-4 w-4" aria-hidden="true" /> Reintentar vista previa</button>}
              </div>
            </div>
          </div>
          {conUrl && (
            <div id="q-foto-url" className="mt-3">
              <Campo etiqueta="URL o ruta de la fotografía" htmlFor="q-foto" error={errores.fotoUrl} ayuda="Ej. https://… o /images/equipos/usco-eq-410.jpg">
                <input id="q-foto" className={`input ${errores.fotoUrl ? 'input-error' : ''}`} value={f.fotoUrl} onChange={set('fotoUrl')} maxLength={400} />
              </Campo>
            </div>
          )}
          {!conUrl && errores.fotoUrl && <p role="alert" className="mt-1.5 text-sm font-semibold text-alerta-borde">{errores.fotoUrl}</p>}
        </div>
        <div className="md:col-span-2"><Campo etiqueta="Descripción" htmlFor="q-desc"><textarea id="q-desc" rows={2} className="input" value={f.descripcion} onChange={set('descripcion')} maxLength={500} /></Campo></div>
        <Campo etiqueta="Especificaciones (una por línea)" htmlFor="q-specs"><textarea id="q-specs" rows={4} className="input" value={f.specs} onChange={set('specs')} /></Campo>
        <Campo etiqueta="Accesorios incluidos (uno por línea)" htmlFor="q-acc"><textarea id="q-acc" rows={4} className="input" value={f.accesorios} onChange={set('accesorios')} placeholder={'Cable de poder\nPuntas de prueba\nEstuche'} /></Campo>
        <div className="md:col-span-2"><ErrorCaja mensaje={errorSrv} /></div>
      </form>
    </Modal>
  )
}
