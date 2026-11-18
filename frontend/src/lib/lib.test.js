import { describe, expect, it } from 'vitest'
import { fmtDuracion, fmtFechaHora, fmtFranja, fmtVencimiento, toIso } from './fecha.js'
import { REGEX_CORREO, REGEX_PLACA_INVENTARIO, convertir, fmtCantidad, homeDe, norm, puede, unidadesCompatibles } from './constantes.js'
import { inicioDe, modulosDe } from './modulos.js'

describe('validaciones de formulario (PV-01/02, PU-07)', () => {
  it('acepta solo correos @usco.edu.co', () => {
    expect(REGEX_CORREO.test('laura.gomez@usco.edu.co')).toBe(true)
    expect(REGEX_CORREO.test('usuario123')).toBe(false)
    expect(REGEX_CORREO.test('alguien@gmail.com')).toBe(false)
    expect(REGEX_CORREO.test('alguien@usco.edu.co.evil.com')).toBe(false)
  })
  it('valida las placas de equipos (USCO-EQ-000) e insumos (USCO-IN-000)', () => {
    expect(REGEX_PLACA_INVENTARIO.test('USCO-IN-001')).toBe(true)
    expect(REGEX_PLACA_INVENTARIO.test('USCO-EQ-102')).toBe(true)
    expect(REGEX_PLACA_INVENTARIO.test('EQ-99999')).toBe(false)
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
  it('indica cuánto falta o cuánto pasó desde una fecha límite', () => {
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

describe('roles y permisos en el cliente', () => {
  const monitor = { rol: 'MONITOR', permisos: ['TABLERO_VER', 'INVENTARIO_CONSULTAR', 'NOVEDAD_REPORTAR', 'SOLICITUD_CREAR', 'AVANCES_REGISTRAR', 'ALERTAS_VER', 'CARACTERIZACION_CONSULTAR', 'REPORTES_GENERAR'] }
  const admin = { rol: 'ADMINISTRADOR', permisos: ['TABLERO_VER', 'USUARIOS_GESTIONAR', 'PARAMETRIZAR', 'SEDES_GESTIONAR', 'PRESUPUESTO_GESTIONAR', 'HISTORIAL_VER'] }
  it('muestra solo los módulos permitidos', () => {
    const rutasMonitor = modulosDe(monitor).flatMap((g) => g.items.map((i) => i.to))
    expect(rutasMonitor).toContain('/novedades')
    expect(rutasMonitor).toContain('/solicitudes')
    expect(rutasMonitor).not.toContain('/usuarios')
    expect(rutasMonitor).not.toContain('/presupuesto')
    expect(modulosDe(admin).map((g) => g.grupo)).toContain('Administración')
  })
  it('lleva a cada cuenta a su primera pantalla', () => {
    expect(inicioDe(monitor)).toBe('/tablero')
    expect(inicioDe({ permisos: ['INVENTARIO_CONSULTAR'] })).toBe('/inventario')
    expect(homeDe('neiva', '/tablero')).toBe('/neiva/tablero')
  })
  it('comprueba permisos', () => {
    expect(puede(monitor, 'NOVEDAD_VALIDAR')).toBe(false)
    expect(puede(monitor, 'NOVEDAD_VALIDAR', 'NOVEDAD_REPORTAR')).toBe(true)
    expect(puede(null, 'TABLERO_VER')).toBe(false)
  })
})
