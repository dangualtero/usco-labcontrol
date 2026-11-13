import { describe, expect, it } from 'vitest'
import { fmtDuracion, fmtFechaHora, fmtFranja, fmtVencimiento, toIso } from './fecha.js'
import { REGEX_CORREO, REGEX_PLACA, REGEX_PLACA_INVENTARIO, convertir, fmtCantidad, norm, unidadesCompatibles } from './constantes.js'

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
  it('acepta placas de insumos en el inventario, pero no para préstamos', () => {
    expect(REGEX_PLACA_INVENTARIO.test('USCO-IN-001')).toBe(true)
    expect(REGEX_PLACA_INVENTARIO.test('USCO-EQ-102')).toBe(true)
    expect(REGEX_PLACA.test('USCO-IN-001')).toBe(false)
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

describe('cantidades y unidades de medida', () => {
  it('convierte entre unidades de la misma familia', () => {
    expect(convertir(250, 'ML', 'L')).toBe(0.25)
    expect(convertir(1.5, 'KG', 'G')).toBe(1500)
    expect(convertir(150, 'CM', 'M')).toBe(1.5)
  })
  it('ofrece solo unidades compatibles', () => {
    expect(unidadesCompatibles('L').map((u) => u.valor)).toEqual(['ML', 'L'])
    expect(unidadesCompatibles('UNIDAD').map((u) => u.valor)).toEqual(['UNIDAD'])
  })
  it('formatea cantidades con el símbolo de la unidad', () => {
    expect(fmtCantidad(1.5, 'L')).toBe('1,5 L')
    expect(fmtCantidad(3, 'UNIDAD')).toBe('3 und')
  })
})
