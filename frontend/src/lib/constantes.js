/** Laboratorios iniciales de la sede principal. Los de cada sede vienen de la API (useLaboratorios). */
export const LABORATORIOS = ['Electrónica', 'Petróleos', 'Civil', 'Agrícola']
export const LIMITE_EQUIPOS = 3
export const REGEX_CORREO = /^[A-Za-z0-9._%+-]+@usco\.edu\.co$/
/** Placa de un equipo prestable. */
export const REGEX_PLACA = /^USCO-EQ-\d{3}$/
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

export const GRUPOS = {
  USUARIO: { etiqueta: 'Estudiante / Docente', home: '/usuario/catalogo' },
  AUXILIAR: { etiqueta: 'Auxiliar de laboratorio', home: '/auxiliar' },
  COORDINADOR: { etiqueta: 'Coordinador de laboratorios', home: '/coordinador' },
}

/** Ruta de inicio del rol dentro de una sede: homeDe('AUXILIAR', 'pitalito') → '/pitalito/auxiliar'. */
export const homeDe = (grupo, sede) => (sede ? `/${sede}` : '') + (GRUPOS[grupo]?.home ?? '/login')

/** Categorías del presupuesto (las mismas que valida el servidor). */
export const CATEGORIAS_PRESUPUESTO = ['Asignación', 'Compra de equipos', 'Mantenimiento', 'Calibración', 'Insumos y repuestos', 'Otro']

const COP = new Intl.NumberFormat('es-CO', { style: 'currency', currency: 'COP', maximumFractionDigits: 0 })
export const pesos = (v) => COP.format(v ?? 0)

/** «La Plata» → «la-plata» (igual que el servidor). */
export const slugDe = (t) => norm(t).replace(/[^a-z0-9]+/g, '-').replace(/(^-|-$)/g, '')

/** Franjas de reserva (PF-06): 2 horas cada una. */
export const FRANJAS = [
  { inicio: '07:00', fin: '09:00' },
  { inicio: '09:00', fin: '11:00' },
  { inicio: '11:00', fin: '13:00' },
  { inicio: '14:00', fin: '16:00' },
  { inicio: '16:00', fin: '18:00' },
  { inicio: '18:00', fin: '20:00' },
]

export const norm = (s) =>
  (s ?? '').toString().normalize('NFD').replace(/\p{M}/gu, '').toLowerCase().trim()
