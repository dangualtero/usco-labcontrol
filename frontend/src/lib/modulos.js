import {
  Bell, Building2, ClipboardList, FlaskConical, Gauge, History, LayoutDashboard, ListChecks, Package,
  Settings2, Siren, Users, Wallet, Wrench,
} from 'lucide-react'
import { puede } from './constantes.js'

/**
 * Los módulos de USCO LabControl agrupados como en la hoja de roles. Cada pantalla se muestra solo si la cuenta tiene
 * alguno de sus permisos (la matriz de permisos se configura en Parametrización; el servidor aplica la misma regla).
 */
export const MODULOS = [
  {
    grupo: 'Tablero de control',
    items: [
      { to: '/tablero', texto: 'Tablero', icono: LayoutDashboard, permisos: ['TABLERO_VER'] },
      { to: '/alertas', texto: 'Alertas', icono: Siren, permisos: ['ALERTAS_VER'] },
    ],
  },
  {
    grupo: 'Laboratorios',
    items: [
      { to: '/laboratorios', texto: 'Caracterización', icono: FlaskConical, permisos: ['CARACTERIZACION_CONSULTAR', 'LABORATORIOS_GESTIONAR'] },
    ],
  },
  {
    grupo: 'Inventarios y mantenimientos',
    items: [
      { to: '/inventario', texto: 'Inventario', icono: Package, permisos: ['INVENTARIO_CONSULTAR'] },
      { to: '/novedades', texto: 'Novedades', icono: Bell, permisos: ['NOVEDAD_REPORTAR', 'NOVEDAD_VALIDAR'] },
      { to: '/mantenimientos', texto: 'Mantenimientos', icono: Wrench, permisos: ['INVENTARIO_CONSULTAR'] },
    ],
  },
  {
    grupo: 'Solicitudes y requerimientos',
    items: [
      { to: '/solicitudes', texto: 'Solicitudes', icono: ClipboardList, permisos: ['SOLICITUD_CREAR', 'SOLICITUD_VALIDAR', 'SOLICITUD_REVISAR'] },
    ],
  },
  {
    grupo: 'Presupuestos y contratación',
    items: [
      { to: '/presupuesto', texto: 'Presupuesto', icono: Wallet, permisos: ['PRESUPUESTO_GESTIONAR'] },
    ],
  },
  {
    grupo: 'Reportes y seguimiento',
    items: [
      { to: '/seguimiento', texto: 'Compromisos y avances', icono: ListChecks, permisos: ['COMPROMISOS_GESTIONAR', 'AVANCES_REGISTRAR', 'AVANCES_VERIFICAR', 'REPORTES_GENERAR'] },
      { to: '/reportes', texto: 'Indicadores y reportes', icono: Gauge, permisos: ['REPORTES_GENERAR'] },
      { to: '/historial', texto: 'Historial', icono: History, permisos: ['HISTORIAL_VER'] },
    ],
  },
  {
    grupo: 'Administración',
    items: [
      { to: '/usuarios', texto: 'Usuarios y roles', icono: Users, permisos: ['USUARIOS_GESTIONAR'] },
      { to: '/parametrizacion', texto: 'Parametrización', icono: Settings2, permisos: ['PARAMETRIZAR'] },
      { to: '/sedes', texto: 'Sedes', icono: Building2, permisos: ['SEDES_GESTIONAR'] },
    ],
  },
]

/** Módulos visibles para la cuenta (grupos vacíos se omiten). */
export const modulosDe = (usuario) => MODULOS
  .map((g) => ({ ...g, items: g.items.filter((i) => puede(usuario, ...i.permisos)) }))
  .filter((g) => g.items.length > 0)

/** Primera pantalla a la que puede entrar la cuenta (normalmente el Tablero). */
export const inicioDe = (usuario) => modulosDe(usuario)[0]?.items[0]?.to ?? '/sin-acceso'

export const PERMISOS_DE_RUTA = Object.fromEntries(MODULOS.flatMap((g) => g.items.map((i) => [i.to, i.permisos])))

