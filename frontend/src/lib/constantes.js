export const LABORATORIOS = ['Electrónica', 'Petróleos', 'Civil', 'Agrícola']
export const LIMITE_EQUIPOS = 3
export const REGEX_CORREO = /^[A-Za-z0-9._%+-]+@usco\.edu\.co$/
export const REGEX_PLACA = /^USCO-EQ-\d{3}$/

export const GRUPOS = {
  USUARIO: { etiqueta: 'Estudiante / Docente', home: '/usuario/catalogo' },
  AUXILIAR: { etiqueta: 'Auxiliar de laboratorio', home: '/auxiliar' },
  COORDINADOR: { etiqueta: 'Coordinador de laboratorios', home: '/coordinador' },
}

export const homeDe = (grupo) => GRUPOS[grupo]?.home ?? '/login'

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
