import { describe, expect, it } from 'vitest'
import { fmtDuracion, fmtFechaHora, fmtFranja, fmtVencimiento, toIso } from './fecha.js'
import { REGEX_CORREO, REGEX_PLACA, norm } from './constantes.js'

describe('validaciones de formulario (PV-01/02, PU-07)', () => {
  it('acepta solo correos @usco.edu.co', () => {
    expect(REGEX_CORREO.test('laura.gomez@usco.edu.co')).toBe(true)
    expect(REGEX_CORREO.test('usuario123')).toBe(false)
    expect(REGEX_CORREO.test('alguien@gmail.com')).toBe(false)
    expect(REGEX_CORREO.test('alguien@usco.edu.co.evil.com')).toBe(false)
  })
  it('valida el formato de placa USCO-EQ-000', () => {
    expect(REGEX_PLACA.test('USCO-EQ-102')).toBe(true)
    expect(REGEX_PLACA.test('EQ-99999')).toBe(false)
  })
  it('normaliza tildes para la búsqueda', () => {
    expect(norm('Multímetro')).toBe('multimetro')
  })
})

describe('formato de fechas (PA-09)', () => {
  it('formatea fecha y hora uniformemente', () => {
    expect(fmtFechaHora('2026-10-20T17:45:00')).toBe('20 oct 2026 · 17:45')
  })
  it('formatea franjas y duraciones', () => {
    expect(fmtFranja('2026-09-23T14:00:00', '2026-09-23T16:00:00')).toBe('miércoles 23 sep · 14:00–16:00')
    expect(fmtDuracion(95)).toBe('1 h 35 min')
    expect(fmtDuracion(30)).toBe('30 min')
  })
  it('indica si un préstamo está vencido', () => {
    const ahora = new Date('2026-10-20T12:00:00')
    expect(fmtVencimiento('2026-10-20T10:25:00', ahora)).toBe('venció hace 1 h 35 min')
    expect(fmtVencimiento('2026-10-20T13:05:00', ahora)).toBe('vence en 1 h 05 min')
  })
  it('convierte a ISO local', () => {
    expect(toIso(new Date(2026, 9, 20, 8, 5, 3))).toBe('2026-10-20T08:05:03')
  })
})
