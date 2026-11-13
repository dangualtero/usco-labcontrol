import { useEffect, useRef, useState } from 'react'
import { Save, ImageOff, ImagePlus, Link2, Loader2, RefreshCw, Trash2, Upload, Microscope, Beaker } from 'lucide-react'
import { api } from '../../api/index.js'
import { useToast } from '../../context/ToastContext.jsx'
import { FAMILIAS, REGEX_PLACA_INVENTARIO, UMBRAL_ALERTA, UNIDADES, unidad as uni } from '../../lib/constantes.js'
import { useLaboratorios } from '../../lib/hooks.js'
import { Campo, ErrorCaja, Modal } from '../../components/ui.jsx'
import { prepararFoto, tamanoLegible } from '../../lib/imagen.js'
import { useSede } from '../../context/SedeContext.jsx'

const VACIO = { placa: '', nombre: '', marca: '', modelo: '', numeroSerie: '', categoria: '', laboratorio: '', ubicacion: '',
  descripcion: '', specs: '', accesorios: '', estadoFisico: 'Bueno', fotoUrl: '', estado: 'DISPONIBLE',
  tipo: 'EQUIPO', cantidad: '1', unidad: 'UNIDAD', cantidadReferencia: '' }
const PREFIJO = { EQUIPO: 'USCO-EQ-', INSUMO: 'USCO-IN-' }
const numero = (v) => (v === '' || v === null || v === undefined ? null : Number(String(v).replace(',', '.')))
const lineas = (t) => t.split('\n').map((x) => x.trim()).filter(Boolean)

/**
 * Alta y edición de equipos e insumos (CRUD de inventario). `equipo` null = nuevo; `tipoInicial` = EQUIPO o INSUMO.
 * Los equipos se cuentan por unidades; los insumos por unidades, mL/L, mg/g/kg o cm/m.
 */
export default function EquipoFormModal({ abierto, equipo, tipoInicial = 'EQUIPO', onCerrar, onHecho }) {
  const toast = useToast()
  const { sede, slug } = useSede()
  const labs = useLaboratorios([slug, abierto])
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
      specs: (equipo.specs ?? []).join('\n'), accesorios: (equipo.accesorios ?? []).join('\n'),
      tipo: equipo.tipo ?? 'EQUIPO', cantidad: String(equipo.cantidad ?? 1), unidad: equipo.unidad ?? 'UNIDAD',
      cantidadReferencia: equipo.cantidadReferencia != null ? String(equipo.cantidadReferencia) : '' }
      : { ...VACIO, tipo: tipoInicial, placa: PREFIJO[tipoInicial], unidad: tipoInicial === 'INSUMO' ? 'L' : 'UNIDAD', cantidad: tipoInicial === 'INSUMO' ? '' : '1' })
  }, [abierto, equipo, tipoInicial])

  // Laboratorio por defecto: el primero de la sede
  useEffect(() => {
    if (abierto && !f.laboratorio && labs.length) setF((x) => ({ ...x, laboratorio: labs[0] }))
  }, [abierto, labs, f.laboratorio])

  const insumo = f.tipo === 'INSUMO'
  const cambiarTipo = (t) => setF((x) => ({
    ...x, tipo: t,
    placa: !x.placa || x.placa === PREFIJO[x.tipo] || /^USCO-(EQ|IN)-/.test(x.placa) ? PREFIJO[t] + x.placa.replace(/^USCO-(EQ|IN)-?/, '') : x.placa,
    unidad: t === 'INSUMO' ? (x.unidad === 'UNIDAD' ? 'L' : x.unidad) : 'UNIDAD',
    cantidad: t === 'EQUIPO' && !x.cantidad ? '1' : x.cantidad,
  }))
  const opcionesLab = f.laboratorio && !labs.includes(f.laboratorio) ? [f.laboratorio, ...labs] : labs

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
    const placa = f.placa.trim().toUpperCase()
    if (nuevo && (!REGEX_PLACA_INVENTARIO.test(placa) || !placa.startsWith(PREFIJO[f.tipo]))) {
      er.placa = insumo ? 'Usa el formato USCO-IN-000. Ejemplo: USCO-IN-012' : 'Usa el formato USCO-EQ-000. Ejemplo: USCO-EQ-102'
    }
    if (!f.nombre.trim()) er.nombre = 'El nombre es obligatorio'
    if (!f.categoria.trim()) er.categoria = 'La categoría es obligatoria'
    if (!f.laboratorio) er.laboratorio = 'Selecciona el laboratorio (créalo primero en «Laboratorios» si no aparece)'
    const cant = numero(f.cantidad)
    const ref = numero(f.cantidadReferencia)
    const entero = f.unidad === 'UNIDAD'
    if (cant === null || Number.isNaN(cant) || cant < (insumo ? 0 : 1)) er.cantidad = insumo ? 'Indica la cantidad disponible (0 o más)' : 'Mínimo 1 unidad'
    else if (entero && !Number.isInteger(cant)) er.cantidad = 'En unidades usa un número entero (1, 2, 3…)'
    if (insumo && ref !== null && (Number.isNaN(ref) || ref <= 0)) er.cantidadReferencia = 'Debe ser mayor que cero'
    else if (insumo && ref === null && cant === 0) er.cantidadReferencia = 'Indica la cantidad de referencia (100 %)'
    const foto = f.fotoUrl.trim()
    if (foto && !/^(https?:\/\/|\/images\/|\/api\/imagenes\/)/.test(foto)) er.fotoUrl = 'Debe ser una URL http(s) o una ruta /images/…'
    setErrores(er)
    if (Object.keys(er).length) return
    setEnviando(true); setErrorSrv('')
    const cuerpo = { ...f, placa, fotoUrl: foto, specs: lineas(f.specs), accesorios: insumo ? [] : lineas(f.accesorios),
      cantidad: numero(f.cantidad), unidad: insumo ? f.unidad : 'UNIDAD', cantidadReferencia: insumo ? numero(f.cantidadReferencia) : null }
    const que = insumo ? 'Insumo' : 'Equipo'
    try {
      if (nuevo) await api.crearEquipo(cuerpo); else await api.actualizarEquipo(equipo.placa, cuerpo)
      toast.exito(nuevo ? `${que} creado` : `${que} actualizado`, `${cuerpo.placa || equipo.placa} · ${cuerpo.nombre}`)
      onHecho?.(); onCerrar()
    } catch (err) { setErrorSrv(err.message) } finally { setEnviando(false) }
  }

  return (
    <Modal abierto={abierto} onCerrar={onCerrar} ancho="max-w-3xl" titulo={nuevo ? (insumo ? 'Nuevo insumo' : 'Nuevo equipo') : (insumo ? 'Editar insumo' : 'Editar equipo')} subtitulo={nuevo ? `Se agregará al inventario y al catálogo de la ${sede?.nombre ?? 'sede'}` : `${equipo?.placa} · ${equipo?.nombre}`}
      pie={<>
        <button type="button" className="btn-outline" onClick={onCerrar}>Cancelar</button>
        <button type="submit" form="form-equipo" className="btn-primary" disabled={enviando || subiendo}><Save className="h-4 w-4" aria-hidden="true" /> {enviando ? 'Guardando…' : nuevo ? (insumo ? 'Crear insumo' : 'Crear equipo') : 'Guardar cambios'}</button>
      </>}>
      <form id="form-equipo" onSubmit={guardar} noValidate className="grid gap-4 md:grid-cols-2">
        {nuevo && (
          <fieldset className="md:col-span-2">
            <legend className="label">¿Qué vas a registrar?</legend>
            <div className="grid gap-2 sm:grid-cols-2">
              {[['EQUIPO', Microscope, 'Equipo', 'Se presta y se devuelve (osciloscopio, GPS…)'], ['INSUMO', Beaker, 'Insumo', 'Se consume por cantidad (reactivos, cable, guantes…)']].map(([t, Ic, titulo, desc]) => (
                <label key={t} className={`flex cursor-pointer items-start gap-3 rounded-lg border-2 p-3 ${f.tipo === t ? 'border-usco bg-usco-soft' : 'border-slate-200 hover:border-slate-300'}`}>
                  <input type="radio" name="q-tipo" value={t} checked={f.tipo === t} onChange={() => cambiarTipo(t)} className="mt-1 accent-[#8F141B]" />
                  <Ic className="mt-0.5 h-5 w-5 shrink-0 text-usco" aria-hidden="true" />
                  <span><span className="block text-sm font-extrabold text-slate-900">{titulo}</span><span className="block text-xs text-pizarra">{desc}</span></span>
                </label>
              ))}
            </div>
          </fieldset>
        )}
        <Campo etiqueta="Placa (código)" htmlFor="q-placa" error={errores.placa} ayuda={nuevo ? `Formato ${PREFIJO[f.tipo]}000` : 'La placa no se puede modificar'}>
          <input id="q-placa" className={`input font-mono uppercase ${errores.placa ? 'input-error' : ''}`} value={f.placa} onChange={set('placa')} disabled={!nuevo} maxLength={11} placeholder={insumo ? 'USCO-IN-012' : 'USCO-EQ-410'} />
        </Campo>
        <Campo etiqueta={insumo ? 'Nombre del insumo' : 'Nombre del equipo'} htmlFor="q-nombre" error={errores.nombre}><input id="q-nombre" className={`input ${errores.nombre ? 'input-error' : ''}`} value={f.nombre} onChange={set('nombre')} maxLength={120} /></Campo>
        <Campo etiqueta="Marca" htmlFor="q-marca"><input id="q-marca" className="input" value={f.marca} onChange={set('marca')} maxLength={60} /></Campo>
        <Campo etiqueta="Modelo" htmlFor="q-modelo"><input id="q-modelo" className="input" value={f.modelo} onChange={set('modelo')} maxLength={60} /></Campo>
        <Campo etiqueta="Número de serie" htmlFor="q-serie"><input id="q-serie" className="input font-mono" value={f.numeroSerie} onChange={set('numeroSerie')} maxLength={60} /></Campo>
        <Campo etiqueta="Categoría" htmlFor="q-cat" error={errores.categoria}><input id="q-cat" className={`input ${errores.categoria ? 'input-error' : ''}`} value={f.categoria} onChange={set('categoria')} maxLength={60} placeholder="Ej. Osciloscopios" /></Campo>
        <Campo etiqueta="Laboratorio" htmlFor="q-lab" error={errores.laboratorio}>
          <select id="q-lab" className={`input ${errores.laboratorio ? 'input-error' : ''}`} value={f.laboratorio} onChange={set('laboratorio')}>
            {!f.laboratorio && <option value="">{labs.length ? 'Selecciona…' : 'Sin laboratorios en esta sede'}</option>}
            {opcionesLab.map((l) => <option key={l}>{l}</option>)}
          </select>
        </Campo>
        <div className="grid gap-3 rounded-xl border border-slate-200 bg-slate-50 p-3 md:col-span-2 sm:grid-cols-3">
          {insumo ? (
            <>
              <Campo etiqueta="Unidad de medida" htmlFor="q-uni" ayuda={FAMILIAS[uni(f.unidad).familia]}>
                <select id="q-uni" className="input" value={f.unidad} onChange={set('unidad')}>
                  {Object.entries(FAMILIAS).map(([fam, texto]) => (
                    <optgroup key={fam} label={texto}>{UNIDADES.filter((u) => u.familia === fam).map((u) => <option key={u.valor} value={u.valor}>{u.texto}</option>)}</optgroup>
                  ))}
                </select>
              </Campo>
              <Campo etiqueta={`Cantidad disponible (${uni(f.unidad).simbolo})`} htmlFor="q-cant" error={errores.cantidad}>
                <input id="q-cant" type="number" inputMode="decimal" min="0" step={f.unidad === 'UNIDAD' ? 1 : 'any'} className={`input ${errores.cantidad ? 'input-error' : ''}`} value={f.cantidad} onChange={set('cantidad')} placeholder="Ej. 5" />
              </Campo>
              <Campo etiqueta={`Cantidad de referencia · 100 % (${uni(f.unidad).simbolo})`} htmlFor="q-ref" error={errores.cantidadReferencia}
                ayuda={`Se alerta con ${UMBRAL_ALERTA} % o menos. Vacío = la cantidad actual`}>
                <input id="q-ref" type="number" inputMode="decimal" min="0" step="any" className={`input ${errores.cantidadReferencia ? 'input-error' : ''}`} value={f.cantidadReferencia} onChange={set('cantidadReferencia')} placeholder="Ej. 10" />
              </Campo>
            </>
          ) : (
            <Campo etiqueta="Cantidad (unidades)" htmlFor="q-cant" error={errores.cantidad} ayuda="Número de piezas: 1, 2, 3…">
              <input id="q-cant" type="number" inputMode="numeric" min="1" step="1" className={`input ${errores.cantidad ? 'input-error' : ''}`} value={f.cantidad} onChange={set('cantidad')} />
            </Campo>
          )}
        </div>
        <Campo etiqueta="Ubicación física exacta" htmlFor="q-ubi"><input id="q-ubi" className="input" value={f.ubicacion} onChange={set('ubicacion')} maxLength={160} placeholder="Bloque · Laboratorio · Mesa/estante" /></Campo>
        <Campo etiqueta="Estado físico" htmlFor="q-fis">
          <select id="q-fis" className="input" value={f.estadoFisico} onChange={set('estadoFisico')}>{['Excelente', 'Bueno', 'Regular', 'Deteriorado'].map((x) => <option key={x}>{x}</option>)}</select>
        </Campo>
        {nuevo && !insumo && (
          <Campo etiqueta="Estado inicial" htmlFor="q-est">
            <select id="q-est" className="input" value={f.estado} onChange={set('estado')}><option value="DISPONIBLE">Disponible</option><option value="MANTENIMIENTO">En mantenimiento</option></select>
          </Campo>
        )}
        <div className="md:col-span-2">
          <p className="label">Fotografía {insumo ? 'del insumo' : 'del equipo'}</p>
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
        {!insumo && <Campo etiqueta="Accesorios incluidos (uno por línea)" htmlFor="q-acc"><textarea id="q-acc" rows={4} className="input" value={f.accesorios} onChange={set('accesorios')} placeholder={'Cable de poder\nPuntas de prueba\nEstuche'} /></Campo>}
        <div className="md:col-span-2"><ErrorCaja mensaje={errorSrv} /></div>
      </form>
    </Modal>
  )
}
