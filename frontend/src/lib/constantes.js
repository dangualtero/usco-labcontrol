export const REGEX_CORREO = /^[A-Za-z0-9._%+-]+@usco\.edu\.co$/
/** Placa de inventario: equipo (USCO-EQ-000) o insumo (USCO-IN-000). */
export const REGEX_PLACA_INVENTARIO = /^USCO-(EQ|IN)-\d{3}$/

/**
 * Unidades de medida (las mismas que valida el servidor). factor = cuántas unidades base de su familia contiene.
 * Implementos: unidades (1, 2, 3…) · Líquidos: mL / L · Sólidos: mg / g / kg · Longitud: cm / m.
 */
export const UNIDADES = [
  { valor: 'UNIDAD', simbolo: 'und', texto: 'Unidades (1, 2, 3…)', familia: 'CONTEO', factor: 1 },
  { valor: 'ML', simbolo: 'mL', texto: 'Mililitros (mL)', familia: 'VOLUMEN', factor: 1 },
  { valor: 'L', simbolo: 'L', texto: 'Litros (L)', familia: 'VOLUMEN', factor: 1000 },
  { valor: 'MG', simbolo: 'mg', texto: 'Miligramos (mg)', familia: 'MASA', factor: 1 },
  { valor: 'G', simbolo: 'g', texto: 'Gramos (g)', familia: 'MASA', factor: 1000 },
  { valor: 'KG', simbolo: 'kg', texto: 'Kilogramos (kg)', familia: 'MASA', factor: 1000000 },
  { valor: 'CM', simbolo: 'cm', texto: 'Centímetros (cm)', familia: 'LONGITUD', factor: 1 },
  { valor: 'M', simbolo: 'm', texto: 'Metros (m)', familia: 'LONGITUD', factor: 100 },
]
export const FAMILIAS = { CONTEO: 'Implementos (unidades)', VOLUMEN: 'Líquidos', MASA: 'Sólidos', LONGITUD: 'Longitud' }
export const unidad = (v) => UNIDADES.find((u) => u.valor === v) ?? UNIDADES[0]
export const unidadesCompatibles = (v) => UNIDADES.filter((u) => u.familia === unidad(v).familia)
/** Convierte entre unidades de la misma familia: convertir(250, 'ML', 'L') → 0.25 */
export const convertir = (cantidad, desde, hacia) => Math.round((Number(cantidad) * unidad(desde).factor / unidad(hacia).factor) * 1000) / 1000
const NUM = new Intl.NumberFormat('es-CO', { maximumFractionDigits: 3 })
/** fmtCantidad(1.5, 'L') → «1,5 L» */
export const fmtCantidad = (c, u) => (c === null || c === undefined ? '—' : `${NUM.format(Number(c))} ${unidad(u).simbolo}`)
/** Porcentaje con el que un insumo entra en alerta. */
export const UMBRAL_ALERTA = 25

/** Los tres roles de la hoja de requisitos. */
export const ROLES = {
  ADMINISTRADOR: { etiqueta: 'Administrador general', corta: 'Administrador' },
  COORDINADOR_LAB: { etiqueta: 'Coordinador de Laboratorio', corta: 'Coordinador' },
  MONITOR: { etiqueta: 'Monitor de Laboratorio', corta: 'Monitor' },
}

/** ¿La cuenta tiene alguno de estos permisos? (el servidor aplica la misma regla en cada acción). */
export const puede = (usuario, ...permisos) => permisos.some((p) => usuario?.permisos?.includes(p))

/** Inicio dentro de una sede: homeDe('pitalito') → '/pitalito/tablero'. */
export const homeDe = (sede, ruta = '/tablero') => (sede ? `/${sede}` : '') + ruta

/** Categorías del presupuesto (las mismas que valida el servidor). */
export const CATEGORIAS_PRESUPUESTO = ['Asignación', 'Compra de equipos', 'Mantenimiento', 'Calibración', 'Insumos y repuestos', 'Otro']

const COP = new Intl.NumberFormat('es-CO', { style: 'currency', currency: 'COP', maximumFractionDigits: 0 })
export const pesos = (v) => COP.format(v ?? 0)

/** «La Plata» → «la-plata» (igual que el servidor). */
export const slugDe = (t) => norm(t).replace(/[^a-z0-9]+/g, '-').replace(/(^-|-$)/g, '')

export const norm = (s) =>
  (s ?? '').toString().normalize('NFD').replace(/\p{M}/gu, '').toLowerCase().trim()

/** Prioridades (propuesta del laboratorio y asignada por el Administrador). */
export const PRIORIDADES = ['ALTA', 'MEDIA', 'BAJA']
export const PRIORIDAD_CLASE = {
  ALTA: 'border-red-600 bg-red-50 text-red-900',
  MEDIA: 'border-amber-600 bg-amber-50 text-amber-900',
  BAJA: 'border-emerald-600 bg-emerald-50 text-emerald-900',
}
