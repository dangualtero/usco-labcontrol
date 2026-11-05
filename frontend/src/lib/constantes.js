export const LABORATORIOS = ['Electrónica', 'Petróleos', 'Civil', 'Agrícola']
export const LIMITE_EQUIPOS = 3
export const REGEX_CORREO = /^[A-Za-z0-9._%+-]+@usco\.edu\.co$/
export const REGEX_PLACA = /^USCO-EQ-\d{3}$/

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
