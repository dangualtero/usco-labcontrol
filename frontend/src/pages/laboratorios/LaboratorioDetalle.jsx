import { useEffect, useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { ArrowLeft, Beaker, FileUp, FlaskConical, Microscope, Package, Pencil, Save, UserCog, UserRound, X } from 'lucide-react'
import { api } from '../../api/index.js'
import { useSede } from '../../context/SedeContext.jsx'
import { useToast } from '../../context/ToastContext.jsx'
import { useCatalogos, useDatos } from '../../lib/hooks.js'
import { fmtFechaHora } from '../../lib/fecha.js'
import { Campo, Cargando, Confirmar, ErrorCaja, Modal } from '../../components/ui.jsx'
import { ACEPTA, ArchivoChip } from '../../components/Archivos.jsx'
import { Encabezado } from '../../components/Estados.jsx'

const CAMPOS = [
  ['codigo', 'Código del laboratorio', 'input', 20, 'Ej. LAB-ELE-01'],
  ['ubicacion', 'Ubicación', 'input', 160, 'Bloque, piso, salón'],
  ['areaM2', 'Área (m²)', 'number'],
  ['capacidad', 'Capacidad (personas)', 'number'],
  ['horario', 'Horario de atención', 'input', 200, 'Lunes a viernes 7:00–18:00'],
  ['telefono', 'Teléfono o extensión', 'input', 30],
  ['correo', 'Correo del laboratorio', 'input', 120],
  ['programas', 'Programas académicos que atiende', 'area', 500],
  ['servicios', 'Servicios y prácticas que ofrece', 'area', 1500],
  ['normasSeguridad', 'Normas de seguridad', 'area', 2000],
  ['descripcion', 'Descripción general', 'area', 300],
]

/** Caracterización y documentación de un laboratorio: datos, responsables, documentos y evidencias. */
export default function LaboratorioDetalle() {
  const { id } = useParams()
  const { ruta } = useSede()
  const toast = useToast()
  const { datos: c, error, recargar } = useDatos(() => api.laboratorio(id), [id], { cada: 0 })
  const [editar, setEditar] = useState(false)
  const [subir, setSubir] = useState(false)
  const [quitar, setQuitar] = useState(null)

  const quitarDoc = async () => {
    try { await api.quitarDocumento(id, quitar.id); toast.exito('Documento retirado', quitar.titulo); setQuitar(null); recargar() }
    catch (e) { toast.error('No se pudo retirar', e.message) }
  }

  if (error) return <div className="space-y-4"><Link to={ruta('/laboratorios')} className="btn-outline"><ArrowLeft className="h-4 w-4" aria-hidden="true" /> Laboratorios</Link><ErrorCaja mensaje={error} /></div>
  if (!c) return <Cargando />
  const l = c.laboratorio
  const dato = (v, sufijo = '') => (v === null || v === undefined || v === '' ? <span className="text-slate-400">Sin registrar</span> : `${v}${sufijo}`)

  return (
    <div className="space-y-6">
      <Link to={ruta('/laboratorios')} className="inline-flex items-center gap-1 text-sm font-bold text-usco hover:underline"><ArrowLeft className="h-4 w-4" aria-hidden="true" /> Laboratorios</Link>
      <Encabezado modulo="Caracterización y documentación" titulo={`Laboratorio de ${l.nombre}`}
        detalle={c.actualizadoEn ? `Actualizado por ${c.actualizadoPor} · ${fmtFechaHora(c.actualizadoEn)}` : 'Aún no se ha registrado la caracterización.'}>
        <Link to={`${ruta('/inventario')}?laboratorio=${encodeURIComponent(l.nombre)}`} className="btn-outline"><Package className="h-4 w-4" aria-hidden="true" /> Inventario</Link>
        {c.puedeEditar && <button className="btn-primary" onClick={() => setEditar(true)}><Pencil className="h-4 w-4" aria-hidden="true" /> Actualizar datos</button>}
      </Encabezado>

      <div className="grid gap-5 xl:grid-cols-[1fr_340px]">
        <section className="card p-5" aria-label="Datos del laboratorio">
          <dl className="grid gap-x-6 gap-y-3 sm:grid-cols-2">
            {CAMPOS.filter(([k]) => !['programas', 'servicios', 'normasSeguridad', 'descripcion'].includes(k)).map(([k, t]) => (
              <div key={k}><dt className="text-xs font-bold uppercase tracking-wide text-pizarra">{t}</dt>
                <dd className="font-semibold text-slate-900">{k === 'ubicacion' ? dato(l.ubicacion) : dato(c[k], k === 'areaM2' ? ' m²' : '')}</dd></div>
            ))}
          </dl>
          {[['descripcion', 'Descripción general', l.descripcion], ['programas', 'Programas académicos', c.programas], ['servicios', 'Servicios y prácticas', c.servicios], ['normasSeguridad', 'Normas de seguridad', c.normasSeguridad]].map(([k, t, v]) => (
            <div key={k} className="mt-4"><p className="text-xs font-bold uppercase tracking-wide text-pizarra">{t}</p><p className="whitespace-pre-line text-sm text-slate-800">{dato(v)}</p></div>
          ))}
        </section>

        <aside className="space-y-4">
          <section className="card p-4" aria-label="Responsables">
            <h2 className="mb-2 font-extrabold">Responsables</h2>
            <p className="flex items-start gap-2 text-sm"><UserCog className="mt-0.5 h-4 w-4 shrink-0 text-usco" aria-hidden="true" /> <span><strong>Coordinación:</strong> {l.coordinadores.join(', ') || 'Sin asignar'}</span></p>
            <p className="mt-1 flex items-start gap-2 text-sm"><UserRound className="mt-0.5 h-4 w-4 shrink-0 text-usco" aria-hidden="true" /> <span><strong>Monitores:</strong> {l.monitores.join(', ') || 'Sin asignar'}</span></p>
          </section>
          <section className="card flex gap-4 p-4 text-sm" aria-label="Inventario">
            <span className="flex items-center gap-1.5 font-semibold"><Microscope className="h-4 w-4 text-pizarra" aria-hidden="true" /> {l.equipos} equipos</span>
            <span className="flex items-center gap-1.5 font-semibold"><Beaker className="h-4 w-4 text-pizarra" aria-hidden="true" /> {l.insumos} insumos</span>
          </section>
        </aside>
      </div>

      <section className="card overflow-hidden" aria-labelledby="h-docs">
        <div className="flex flex-wrap items-center justify-between gap-2 border-b border-slate-200 px-4 py-3">
          <h2 id="h-docs" className="flex items-center gap-2 font-extrabold"><FlaskConical className="h-4 w-4 text-usco" aria-hidden="true" /> Documentos y evidencias ({c.documentos.length})</h2>
          {c.puedeEditar && <button className="btn-primary btn-sm" onClick={() => setSubir(true)}><FileUp className="h-4 w-4" aria-hidden="true" /> Subir documento</button>}
        </div>
        {c.documentos.length === 0 ? <p className="px-4 py-6 text-center text-sm text-pizarra">Aún no hay manuales, guías, certificados ni evidencias.</p> : (
          <ul className="divide-y divide-slate-100">
            {c.documentos.map((d) => (
              <li key={d.id} className="px-4 py-3">
                <div className="flex flex-wrap items-baseline justify-between gap-2">
                  <p className="font-bold text-slate-900">{d.titulo} <span className="ml-1 rounded bg-slate-100 px-1.5 py-0.5 text-[11px] font-bold text-pizarra">{d.tipo}</span></p>
                  <p className="text-xs text-pizarra">{d.subidoPor} · {fmtFechaHora(d.subidoEn)}</p>
                </div>
                {d.descripcion && <p className="text-sm text-pizarra">{d.descripcion}</p>}
                <ul className="mt-2"><ArchivoChip archivo={d.archivo} onQuitar={c.puedeEditar ? () => setQuitar(d) : undefined} /></ul>
              </li>
            ))}
          </ul>
        )}
      </section>

      <EditarModal abierto={editar} c={c} onCerrar={() => setEditar(false)} onHecho={recargar} />
      <SubirModal abierto={subir} labId={id} onCerrar={() => setSubir(false)} onHecho={recargar} />
      <Confirmar abierto={!!quitar} peligro titulo="Retirar documento" mensaje={`¿Retirar «${quitar?.titulo}» del laboratorio?`} textoOk="Retirar"
        onOk={quitarDoc} onCancelar={() => setQuitar(null)} />
    </div>
  )
}

function EditarModal({ abierto, c, onCerrar, onHecho }) {
  const toast = useToast()
  const [f, setF] = useState({})
  const [err, setErr] = useState('')
  const [enviando, setEnviando] = useState(false)
  useEffect(() => {
    if (!abierto) return
    setErr('')
    setF(Object.fromEntries(CAMPOS.map(([k]) => [k, k === 'ubicacion' || k === 'descripcion' ? c.laboratorio[k] ?? '' : c[k] ?? ''])))
  }, [abierto, c])
  const set = (k) => (e) => setF((x) => ({ ...x, [k]: e.target.value }))
  const guardar = async (e) => {
    e.preventDefault()
    setEnviando(true); setErr('')
    const n = (v) => (v === '' || v === null ? null : Number(String(v).replace(',', '.')))
    try {
      await api.caracterizar(c.laboratorio.id, { ...f, areaM2: n(f.areaM2), capacidad: n(f.capacidad) })
      toast.exito('Caracterización actualizada', c.laboratorio.nombre)
      onHecho(); onCerrar()
    } catch (er) { setErr(er.message) } finally { setEnviando(false) }
  }
  return (
    <Modal abierto={abierto} onCerrar={onCerrar} ancho="max-w-3xl" titulo="Actualizar caracterización" subtitulo={c.laboratorio.nombre}
      pie={<>
        <button type="button" className="btn-outline" onClick={onCerrar}>Cancelar</button>
        <button type="submit" form="form-carac" className="btn-primary" disabled={enviando}><Save className="h-4 w-4" aria-hidden="true" /> {enviando ? 'Guardando…' : 'Guardar'}</button>
      </>}>
      <form id="form-carac" onSubmit={guardar} noValidate className="grid gap-4 md:grid-cols-2">
        {CAMPOS.map(([k, t, tipo, max, ph]) => (
          <div key={k} className={tipo === 'area' ? 'md:col-span-2' : ''}>
            <Campo etiqueta={t} htmlFor={`car-${k}`}>
              {tipo === 'area'
                ? <textarea id={`car-${k}`} rows={3} className="input" value={f[k] ?? ''} onChange={set(k)} maxLength={max} />
                : <input id={`car-${k}`} type={tipo === 'number' ? 'number' : 'text'} inputMode={tipo === 'number' ? 'decimal' : undefined} min="0" className="input" value={f[k] ?? ''} onChange={set(k)} maxLength={max} placeholder={ph} />}
            </Campo>
          </div>
        ))}
        <div className="md:col-span-2"><ErrorCaja mensaje={err} /></div>
      </form>
    </Modal>
  )
}

function SubirModal({ abierto, labId, onCerrar, onHecho }) {
  const toast = useToast()
  const catalogos = useCatalogos()
  const tipos = catalogos.TIPO_DOCUMENTO ?? []
  const [f, setF] = useState({ tipo: '', titulo: '', descripcion: '' })
  const [archivo, setArchivo] = useState(null)
  const [err, setErr] = useState('')
  const [enviando, setEnviando] = useState(false)
  useEffect(() => { if (abierto) { setF({ tipo: tipos[0] ?? '', titulo: '', descripcion: '' }); setArchivo(null); setErr('') } /* eslint-disable-next-line react-hooks/exhaustive-deps */ }, [abierto])
  useEffect(() => { if (abierto && !f.tipo && tipos.length) setF((x) => ({ ...x, tipo: tipos[0] })) }, [abierto, tipos, f.tipo])
  const guardar = async (e) => {
    e.preventDefault()
    if (!archivo) { setErr('Selecciona el archivo'); return }
    if (f.titulo.trim().length < 3) { setErr('Escribe un título (mínimo 3 caracteres)'); return }
    setEnviando(true); setErr('')
    try {
      await api.subirDocumento(labId, { ...f, titulo: f.titulo.trim(), archivo })
      toast.exito('Documento subido', f.titulo)
      onHecho(); onCerrar()
    } catch (er) { setErr(er.message) } finally { setEnviando(false) }
  }
  return (
    <Modal abierto={abierto} onCerrar={onCerrar} titulo="Subir documento o evidencia" ancho="max-w-lg"
      pie={<>
        <button type="button" className="btn-outline" onClick={onCerrar}>Cancelar</button>
        <button type="submit" form="form-doc" className="btn-primary" disabled={enviando}><FileUp className="h-4 w-4" aria-hidden="true" /> {enviando ? 'Subiendo…' : 'Subir'}</button>
      </>}>
      <form id="form-doc" onSubmit={guardar} noValidate className="space-y-4">
        <Campo etiqueta="Tipo de documento" htmlFor="doc-tipo">
          <select id="doc-tipo" className="input" value={f.tipo} onChange={(e) => setF((x) => ({ ...x, tipo: e.target.value }))}>{tipos.map((t) => <option key={t}>{t}</option>)}</select>
        </Campo>
        <Campo etiqueta="Título" htmlFor="doc-tit"><input id="doc-tit" className="input" value={f.titulo} onChange={(e) => setF((x) => ({ ...x, titulo: e.target.value }))} maxLength={160} placeholder="Ej. Manual del osciloscopio TBS1052B" /></Campo>
        <Campo etiqueta="Descripción (opcional)" htmlFor="doc-desc"><textarea id="doc-desc" rows={2} className="input" value={f.descripcion} onChange={(e) => setF((x) => ({ ...x, descripcion: e.target.value }))} maxLength={500} /></Campo>
        <Campo etiqueta="Archivo" htmlFor="doc-arch" ayuda="PDF, JPG, PNG, WEBP, Word o Excel · máximo 10 MB">
          <div className="flex items-center gap-2">
            <input id="doc-arch" type="file" accept={ACEPTA} className="input py-2" onChange={(e) => setArchivo(e.target.files?.[0] ?? null)} />
            {archivo && <button type="button" className="rounded p-2 text-pizarra hover:text-alerta-borde" onClick={() => setArchivo(null)} aria-label="Quitar archivo"><X className="h-4 w-4" /></button>}
          </div>
        </Campo>
        <ErrorCaja mensaje={err} />
      </form>
    </Modal>
  )
}
