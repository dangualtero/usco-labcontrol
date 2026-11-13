import { useMemo, useState } from 'react'
import { ArrowDownCircle, ArrowUpCircle, Ban, Building2, Plus, RefreshCw, Search, Wallet } from 'lucide-react'
import { api } from '../../api/index.js'
import { useToast } from '../../context/ToastContext.jsx'
import { useSede } from '../../context/SedeContext.jsx'
import { useDatos } from '../../lib/hooks.js'
import { CATEGORIAS_PRESUPUESTO, REGEX_PLACA_INVENTARIO, norm, pesos } from '../../lib/constantes.js'
import { Campo, Cargando, ErrorCaja, Kpi, Modal } from '../../components/ui.jsx'

const hoyIso = () => {
  const d = new Date()
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`
}
const fecha = (iso) => (iso ? new Date(`${iso}T12:00:00`).toLocaleDateString('es-CO', { day: '2-digit', month: 'short', year: 'numeric' }) : '—')
const NUEVO = () => ({ tipo: 'GASTO', concepto: '', categoria: 'Compra de equipos', monto: '', fecha: hoyIso(), placa: '' })
const soloDigitos = (t) => t.replace(/\D/g, '').slice(0, 12)

/** Presupuesto de la sede activa: ingresos (suman), gastos (restan) y saldo disponible. */
export default function Presupuesto() {
  const toast = useToast()
  const { sede } = useSede()
  const { datos, error, cargando, recargar } = useDatos(() => api.presupuesto(), [], { cada: 30000 })
  const resumen = useDatos(() => api.presupuestoResumen(), [], { cada: 60000 })
  const [q, setQ] = useState('')
  const [tipo, setTipo] = useState('todos')
  const [cat, setCat] = useState('todas')
  const [verAnulados, setVerAnulados] = useState(true)
  const [nuevo, setNuevo] = useState(null)
  const [errores, setErrores] = useState({})
  const [errorSrv, setErrorSrv] = useState('')
  const [enviando, setEnviando] = useState(false)
  const [anular, setAnular] = useState(null)
  const [motivo, setMotivo] = useState('')
  const [errAnular, setErrAnular] = useState('')

  const nq = norm(q)
  const lista = useMemo(() => (datos?.movimientos ?? []).filter((m) =>
    (tipo === 'todos' || m.tipo === tipo) && (cat === 'todas' || m.categoria === cat) && (verAnulados || !m.anulado)
    && (!nq || norm(`${m.concepto} ${m.categoria} ${m.placa ?? ''} ${m.registradoPor}`).includes(nq))), [datos, tipo, cat, verAnulados, nq])

  const maxGasto = Math.max(1, ...(datos?.gastosPorCategoria ?? []).map((c) => c.total))
  const set = (k) => (e) => setNuevo((x) => ({ ...x, [k]: k === 'monto' ? soloDigitos(e.target.value) : e.target.value }))
  const montoNum = Number(nuevo?.monto || 0)
  const saldoDespues = datos ? datos.saldo + (nuevo?.tipo === 'INGRESO' ? montoNum : -montoNum) : 0

  const registrar = async (e) => {
    e.preventDefault()
    const er = {}
    const concepto = nuevo.concepto.trim()
    if (concepto.length < 3) er.concepto = 'Describe el movimiento (mínimo 3 caracteres)'
    if (!montoNum) er.monto = 'Ingresa un valor mayor a cero'
    else if (montoNum > 1e11) er.monto = 'El valor es demasiado alto'
    else if (nuevo.tipo === 'GASTO' && montoNum > datos.saldo) er.monto = `Saldo insuficiente: disponible ${pesos(datos.saldo)}`
    if (!nuevo.fecha) er.fecha = 'Indica la fecha'
    else if (nuevo.fecha > hoyIso()) er.fecha = 'La fecha no puede ser futura'
    const placa = nuevo.placa.trim().toUpperCase()
    if (placa && !REGEX_PLACA_INVENTARIO.test(placa)) er.placa = 'Formato USCO-EQ-000 (equipo) o USCO-IN-000 (insumo)'
    setErrores(er)
    if (Object.keys(er).length) return
    setEnviando(true); setErrorSrv('')
    try {
      await api.registrarMovimiento({ tipo: nuevo.tipo, concepto, categoria: nuevo.categoria, monto: montoNum, fecha: nuevo.fecha, placa: placa || null })
      toast.exito(nuevo.tipo === 'INGRESO' ? 'Ingreso registrado' : 'Gasto registrado', `${pesos(montoNum)} · ${concepto}`)
      setNuevo(null); recargar(); resumen.recargar()
    } catch (err) { setErrorSrv(err.message) } finally { setEnviando(false) }
  }

  const confirmarAnular = async () => {
    if (motivo.trim().length < 5) { setErrAnular('Indica el motivo (mínimo 5 caracteres)'); return }
    try {
      await api.anularMovimiento(anular.id, motivo.trim())
      toast.exito('Movimiento anulado', `${anular.concepto} ya no afecta el saldo`)
      setAnular(null); recargar(); resumen.recargar()
    } catch (err) { setErrAnular(err.message) }
  }

  return (
    <div className="space-y-6">
      <div className="flex flex-col gap-3 sm:flex-row sm:items-end sm:justify-between">
        <div>
          <p className="text-xs font-bold uppercase tracking-widest text-usco">Finanzas · {sede?.nombre}</p>
          <h1 className="text-2xl font-extrabold sm:text-3xl">Presupuesto de laboratorios</h1>
          <p className="text-sm text-pizarra">Los ingresos suman y los gastos restan del saldo de la sede. Un movimiento equivocado se anula con su motivo; nunca se borra.</p>
        </div>
        <div className="flex shrink-0 gap-2">
          <button onClick={() => { recargar(); resumen.recargar() }} disabled={cargando} className="btn-outline" aria-label="Actualizar presupuesto"><RefreshCw className={`h-4 w-4 ${cargando ? 'animate-spin' : ''}`} aria-hidden="true" /> Actualizar</button>
          <button onClick={() => { setNuevo(NUEVO()); setErrores({}); setErrorSrv('') }} disabled={!datos} className="btn-primary"><Plus className="h-4 w-4" aria-hidden="true" /> Registrar movimiento</button>
        </div>
      </div>

      <ErrorCaja mensaje={error} />
      {!datos && !error && <Cargando texto="Cargando presupuesto…" />}

      {datos && (
        <>
          <section className="grid gap-4 sm:grid-cols-3" aria-label="Indicadores del presupuesto">
            <Kpi etiqueta="Ingresos" valor={pesos(datos.ingresos)} detalle="Asignaciones y adiciones" icono={ArrowUpCircle} tono="text-emerald-700" fondo="bg-emerald-50" />
            <Kpi etiqueta="Gastos" valor={pesos(datos.gastos)} detalle="Compras, mantenimiento y otros" icono={ArrowDownCircle} tono="text-red-700" fondo="bg-red-50" />
            <Kpi etiqueta="Saldo disponible" valor={pesos(datos.saldo)} detalle={datos.ingresos ? `${Math.round((datos.gastos / datos.ingresos) * 100)} % ejecutado` : 'Sin asignación todavía'} icono={Wallet} />
          </section>

          <div className="grid gap-6 lg:grid-cols-[1fr_320px]">
            <section className="card overflow-hidden" aria-labelledby="t-movs">
              <div className="flex flex-col gap-3 border-b border-slate-200 p-4">
                <h2 id="t-movs" className="font-extrabold">Movimientos</h2>
                <div className="grid gap-2 sm:grid-cols-[1fr_150px_190px]">
                  <div className="relative"><label htmlFor="p-q" className="sr-only">Buscar movimiento</label><Search className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-pizarra" aria-hidden="true" />
                    <input id="p-q" className="input pl-9" placeholder="Concepto, placa o responsable" value={q} onChange={(e) => setQ(e.target.value)} /></div>
                  <div><label htmlFor="p-t" className="sr-only">Tipo</label><select id="p-t" className="input" value={tipo} onChange={(e) => setTipo(e.target.value)}><option value="todos">Todos</option><option value="INGRESO">Ingresos</option><option value="GASTO">Gastos</option></select></div>
                  <div><label htmlFor="p-c" className="sr-only">Categoría</label><select id="p-c" className="input" value={cat} onChange={(e) => setCat(e.target.value)}><option value="todas">Todas las categorías</option>{CATEGORIAS_PRESUPUESTO.map((c) => <option key={c}>{c}</option>)}</select></div>
                </div>
                <label className="flex items-center gap-2 text-sm text-pizarra"><input type="checkbox" checked={verAnulados} onChange={(e) => setVerAnulados(e.target.checked)} className="h-4 w-4" /> Mostrar movimientos anulados</label>
              </div>
              <div className="relative overflow-x-auto">
                <table className="w-full min-w-[720px]">
                  <thead><tr><th className="th">Fecha</th><th className="th">Concepto</th><th className="th">Categoría</th><th className="th text-right">Valor</th><th className="th"><span className="sr-only">Acciones</span></th></tr></thead>
                  <tbody>
                    {lista.map((m) => (
                      <tr key={m.id} className={m.anulado ? 'bg-slate-50 text-slate-500' : ''}>
                        <td className="td whitespace-nowrap">{fecha(m.fecha)}</td>
                        <td className="td">
                          <p className={`font-bold ${m.anulado ? 'line-through' : 'text-slate-900'}`}>{m.concepto}</p>
                          <p className="text-xs text-pizarra">{m.registradoPor}{m.placa ? <> · <span className="placa">{m.placa}</span></> : null}</p>
                          {m.anulado && <p className="mt-0.5 text-xs font-semibold text-alerta-texto">Anulado por {m.anuladoPor}: {m.anulacionMotivo}</p>}
                        </td>
                        <td className="td text-xs font-semibold">{m.categoria}</td>
                        <td className={`td whitespace-nowrap text-right font-mono font-bold ${m.anulado ? 'line-through' : m.tipo === 'INGRESO' ? 'text-emerald-700' : 'text-red-700'}`}>{m.tipo === 'INGRESO' ? '+' : '−'} {pesos(m.monto)}</td>
                        <td className="td text-right">{!m.anulado && <button className="btn-outline btn-sm" onClick={() => { setAnular(m); setMotivo(''); setErrAnular('') }}><Ban className="h-4 w-4" aria-hidden="true" /> Anular</button>}</td>
                      </tr>
                    ))}
                    {lista.length === 0 && <tr><td className="td py-10 text-center text-pizarra" colSpan={5}>{datos.movimientos.length ? 'Ningún movimiento coincide con los filtros' : 'Aún no hay movimientos. Empieza registrando la asignación de la sede.'}</td></tr>}
                  </tbody>
                </table>
              </div>
            </section>

            <div className="space-y-6">
              <section className="card p-4" aria-labelledby="t-cat">
                <h2 id="t-cat" className="font-extrabold">Gastos por categoría</h2>
                {datos.gastosPorCategoria.length === 0 ? <p className="mt-2 text-sm text-pizarra">Sin gastos registrados.</p> : (
                  <ul className="mt-3 space-y-3">
                    {datos.gastosPorCategoria.map((c) => (
                      <li key={c.categoria}>
                        <div className="flex justify-between gap-2 text-sm"><span className="font-semibold">{c.categoria}</span><span className="font-mono text-xs font-bold">{pesos(c.total)}</span></div>
                        <div className="mt-1 h-2 overflow-hidden rounded-full bg-slate-100"><div className="h-full rounded-full bg-usco" style={{ width: `${(c.total / maxGasto) * 100}%` }} /></div>
                      </li>
                    ))}
                  </ul>
                )}
              </section>

              {resumen.datos && resumen.datos.sedes.length > 1 && (
                <section className="card p-4" aria-labelledby="t-sedes">
                  <h2 id="t-sedes" className="flex items-center gap-2 font-extrabold"><Building2 className="h-4 w-4 text-usco" aria-hidden="true" /> Todas las sedes</h2>
                  <ul className="mt-3 divide-y divide-slate-200">
                    {resumen.datos.sedes.map((s) => (
                      <li key={s.sede.slug} className={`flex items-center justify-between gap-2 py-2 text-sm ${s.sede.slug === sede?.slug ? 'font-bold' : ''}`}>
                        <span>{s.sede.nombre}</span><span className="font-mono text-xs">{pesos(s.saldo)}</span>
                      </li>
                    ))}
                    <li className="flex items-center justify-between gap-2 pt-2 text-sm font-extrabold"><span>Total universidad</span><span className="font-mono text-xs">{pesos(resumen.datos.saldo)}</span></li>
                  </ul>
                </section>
              )}
            </div>
          </div>
        </>
      )}

      <Modal abierto={!!nuevo} onCerrar={() => setNuevo(null)} titulo="Registrar movimiento" subtitulo={sede?.nombre} ancho="max-w-xl"
        pie={<>
          <button type="button" className="btn-outline" onClick={() => setNuevo(null)}>Cancelar</button>
          <button type="submit" form="form-mov" className="btn-primary" disabled={enviando}><Plus className="h-4 w-4" aria-hidden="true" /> {enviando ? 'Guardando…' : 'Registrar'}</button>
        </>}>
        {nuevo && (
          <form id="form-mov" onSubmit={registrar} noValidate className="grid gap-4 sm:grid-cols-2">
            <fieldset className="sm:col-span-2">
              <legend className="label">Tipo de movimiento</legend>
              <div className="grid grid-cols-2 gap-2">
                {[['INGRESO', 'Ingreso (suma)', ArrowUpCircle, 'border-emerald-600 bg-emerald-50 text-emerald-900'], ['GASTO', 'Gasto (resta)', ArrowDownCircle, 'border-red-600 bg-red-50 text-red-900']].map(([v, t, Ic, act]) => (
                  <label key={v} className={`flex min-h-[48px] cursor-pointer items-center justify-center gap-2 rounded-lg border-2 text-sm font-bold ${nuevo.tipo === v ? act : 'border-slate-200 text-pizarra'}`}>
                    <input type="radio" name="tipo" value={v} checked={nuevo.tipo === v} onChange={set('tipo')} className="sr-only" />
                    <Ic className="h-4 w-4" aria-hidden="true" /> {t}
                  </label>
                ))}
              </div>
            </fieldset>
            <div className="sm:col-span-2"><Campo etiqueta="Concepto" htmlFor="m-con" error={errores.concepto}><input id="m-con" className={`input ${errores.concepto ? 'input-error' : ''}`} value={nuevo.concepto} onChange={set('concepto')} maxLength={160} placeholder={nuevo.tipo === 'INGRESO' ? 'Ej. Asignación presupuestal 2026' : 'Ej. Compra de 2 osciloscopios'} /></Campo></div>
            <Campo etiqueta="Categoría" htmlFor="m-cat"><select id="m-cat" className="input" value={nuevo.categoria} onChange={set('categoria')}>{CATEGORIAS_PRESUPUESTO.map((c) => <option key={c}>{c}</option>)}</select></Campo>
            <Campo etiqueta="Valor (COP)" htmlFor="m-monto" error={errores.monto} ayuda={montoNum ? pesos(montoNum) : 'Sin puntos ni comas'}>
              <input id="m-monto" inputMode="numeric" className={`input font-mono ${errores.monto ? 'input-error' : ''}`} value={nuevo.monto} onChange={set('monto')} placeholder="2500000" />
            </Campo>
            <Campo etiqueta="Fecha" htmlFor="m-fecha" error={errores.fecha}><input id="m-fecha" type="date" max={hoyIso()} className={`input ${errores.fecha ? 'input-error' : ''}`} value={nuevo.fecha} onChange={set('fecha')} /></Campo>
            <Campo etiqueta="Placa del equipo (opcional)" htmlFor="m-placa" error={errores.placa}><input id="m-placa" className={`input font-mono uppercase ${errores.placa ? 'input-error' : ''}`} value={nuevo.placa} onChange={set('placa')} maxLength={11} placeholder="USCO-EQ-101" /></Campo>
            <div className="flex items-center justify-between rounded-lg bg-slate-100 px-4 py-3 text-sm sm:col-span-2">
              <span className="text-pizarra">Saldo después del movimiento</span>
              <span className={`font-mono font-extrabold ${saldoDespues < 0 ? 'text-alerta-borde' : 'text-slate-900'}`}>{pesos(saldoDespues)}</span>
            </div>
            <div className="sm:col-span-2"><ErrorCaja mensaje={errorSrv} /></div>
          </form>
        )}
      </Modal>

      <Modal abierto={!!anular} onCerrar={() => setAnular(null)} titulo="Anular movimiento" subtitulo={anular ? `${anular.concepto} · ${pesos(anular.monto)}` : undefined} ancho="max-w-md" acento="bg-alerta-borde"
        pie={<>
          <button className="btn-outline" onClick={() => setAnular(null)}>Cancelar</button>
          <button className="btn-danger" onClick={confirmarAnular}><Ban className="h-4 w-4" aria-hidden="true" /> Anular</button>
        </>}>
        <p className="mb-3 text-sm text-pizarra">El movimiento deja de afectar el saldo, pero se conserva en la lista con el motivo y quién lo anuló.</p>
        <Campo etiqueta="Motivo de la anulación" htmlFor="a-mot" error={errAnular}>
          <input id="a-mot" className={`input ${errAnular ? 'input-error' : ''}`} value={motivo} onChange={(e) => setMotivo(e.target.value)} maxLength={200} placeholder="Ej. Valor digitado por error" />
        </Campo>
      </Modal>
    </div>
  )
}
