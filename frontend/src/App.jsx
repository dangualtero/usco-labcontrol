import { Navigate, Outlet, Route, Routes, useLocation } from 'react-router-dom'
import { Link } from 'react-router-dom'
import { LockKeyhole, Loader2 } from 'lucide-react'
import { useAuth } from './context/AuthContext.jsx'
import { SedeLayout, useSede } from './context/SedeContext.jsx'
import Shell from './components/Shell.jsx'
import Login from './pages/Login.jsx'
import ElegirSede from './pages/ElegirSede.jsx'
import Registro from './pages/Registro.jsx'
import Catalogo from './pages/usuario/Catalogo.jsx'
import MisReservas from './pages/usuario/MisReservas.jsx'
import MiHistorial from './pages/usuario/MiHistorial.jsx'
import Ventanilla from './pages/auxiliar/Ventanilla.jsx'
import CierreTurno from './pages/auxiliar/CierreTurno.jsx'
import Dashboard from './pages/coordinador/Dashboard.jsx'
import HistorialInmutable from './pages/coordinador/HistorialInmutable.jsx'
import Inventario from './pages/coordinador/Inventario.jsx'
import Usuarios from './pages/coordinador/Usuarios.jsx'
import Presupuesto from './pages/coordinador/Presupuesto.jsx'
import Sedes from './pages/coordinador/Sedes.jsx'
import InventarioAux from './pages/auxiliar/InventarioAux.jsx'
import SolicitudesInsumo from './pages/auxiliar/SolicitudesInsumo.jsx'
import Laboratorios from './pages/coordinador/Laboratorios.jsx'
import Alertas from './pages/compartido/Alertas.jsx'
import Reportes from './pages/compartido/Reportes.jsx'
import { api } from './api/index.js'
import ConexionBanner from './components/ConexionBanner.jsx'

const REQUERIDO = {
  USUARIO: 'Esta función es exclusiva de Estudiantes y Docentes',
  AUXILIAR: 'Esta función requiere rol Auxiliar',
  COORDINADOR: 'Disponible únicamente para Coordinador',
}

/**
 * Exige sesión dentro de /:sede. Un estudiante, docente o auxiliar solo puede estar en la URL de SU sede: si abre la de
 * otra (por ejemplo /pitalito siendo de Neiva) se le lleva a la misma pantalla en su sede. El Coordinador general
 * puede navegar por todas.
 */
function RequiereSesion() {
  const { usuario, validando } = useAuth()
  const { slug, ruta } = useSede()
  const loc = useLocation()
  if (validando) {
    return (
      <div className="flex min-h-screen flex-col">
        <ConexionBanner />
        <div className="flex flex-1 items-center justify-center gap-2 text-pizarra" role="status"><Loader2 className="h-5 w-5 animate-spin" aria-hidden="true" /> Verificando sesión…</div>
      </div>
    )
  }
  if (!usuario) return <Navigate to={ruta('/login')} replace state={{ desde: loc.pathname }} />
  if (usuario.sede && usuario.sede !== slug) {
    return <Navigate to={`/${usuario.sede}${loc.pathname.replace(/^\/[^/]+/, '')}`} replace />
  }
  return <Shell />
}

/** RBAC en el cliente (PS-02, PS-03). El servidor aplica la misma regla con Spring Security (RNF-03). */
function RequiereRol({ grupo }) {
  const { usuario } = useAuth()
  const { inicio } = useSede()
  if (usuario.grupo === grupo) return <Outlet />
  return (
    <div className="card mx-auto mt-8 max-w-lg p-8 text-center aparecer" role="alert">
      <div className="mx-auto mb-4 flex h-14 w-14 items-center justify-center rounded-full bg-alerta-fondo"><LockKeyhole className="h-7 w-7 text-alerta-borde" aria-hidden="true" /></div>
      <h1 className="text-xl font-extrabold">Acceso restringido</h1>
      <p className="mt-2 font-semibold text-slate-700">{REQUERIDO[grupo]}</p>
      <p className="mt-1 text-sm text-pizarra">Su rol actual no tiene permiso para ver esta sección.</p>
      <Link to={inicio(usuario.grupo)} className="btn-primary mt-6">Volver a mi panel principal</Link>
    </div>
  )
}

// Vistas compartidas, configuradas para cada rol
const AlertasAux = () => <Alertas cargar={api.alertasAux} enlaces={{ SOLICITUD_PENDIENTE: '/auxiliar/insumos', NOVEDAD: '/auxiliar/reportes', PRESTAMO_VENCIDO: '/auxiliar', AGOTADO: '/auxiliar/inventario', STOCK_BAJO: '/auxiliar/inventario', MANTENIMIENTO: '/auxiliar/inventario' }} />
const AlertasCoord = () => <Alertas cargar={api.alertasCoord} enlaces={{ NOVEDAD: '/coordinador/reportes', AGOTADO: '/coordinador/inventario', STOCK_BAJO: '/coordinador/inventario', MANTENIMIENTO: '/coordinador/inventario', PRESTAMO_VENCIDO: '/coordinador/historial', SOLICITUD_PENDIENTE: '/coordinador/insumos' }} />
const ReportesAux = () => <Reportes cargar={api.novedadesAux} seguimiento={api.seguimientoAux} seguir={api.seguirAux} />
const ReportesCoord = () => <Reportes cargar={api.reportesTodos} version={api.reportesVersion} seguimiento={api.seguimientoCoord} seguir={api.seguirCoord}
  reenviar={api.reenviarNovedad} enviarResumen={api.enviarResumen} />
const InsumosCoord = () => <SolicitudesInsumo soloLectura cargar={api.solicitudesInsumoCoord} />

/** /:sede sin más ruta → panel del rol o ingreso de esa sede. */
function RaizSede() {
  const { usuario } = useAuth()
  const { ruta, inicio } = useSede()
  return <Navigate to={usuario ? inicio(usuario.grupo) : ruta('/login')} replace />
}

/** /login (dirección antigua) → ingreso de la sede del usuario, o «Elige tu sede». */
function LoginSinSede() {
  const { usuario, home } = useAuth()
  return <Navigate to={usuario ? home : '/'} replace />
}

export default function App() {
  return (
    <Routes>
      <Route path="/" element={<ElegirSede />} />
      <Route path="/login" element={<LoginSinSede />} />
      {/* Cada sede tiene su propia dirección: /neiva, /pitalito, /garzon… */}
      <Route path="/:sede" element={<SedeLayout />}>
        <Route index element={<RaizSede />} />
        <Route path="login" element={<Login />} />
        <Route path="registro" element={<Registro />} />
        <Route element={<RequiereSesion />}>
          <Route element={<RequiereRol grupo="USUARIO" />}>
            <Route path="usuario" element={<Navigate to="catalogo" replace />} />
            <Route path="usuario/catalogo" element={<Catalogo />} />
            <Route path="usuario/reservas" element={<MisReservas />} />
            <Route path="usuario/historial" element={<MiHistorial />} />
          </Route>
          <Route element={<RequiereRol grupo="AUXILIAR" />}>
            <Route path="auxiliar" element={<Ventanilla />} />
            <Route path="auxiliar/turno" element={<CierreTurno />} />
            <Route path="auxiliar/inventario" element={<InventarioAux />} />
            <Route path="auxiliar/insumos" element={<SolicitudesInsumo />} />
            <Route path="auxiliar/reportes" element={<ReportesAux />} />
            <Route path="auxiliar/alertas" element={<AlertasAux />} />
          </Route>
          <Route element={<RequiereRol grupo="COORDINADOR" />}>
            <Route path="coordinador" element={<Dashboard />} />
            <Route path="coordinador/historial" element={<HistorialInmutable />} />
            <Route path="coordinador/inventario" element={<Inventario />} />
            <Route path="coordinador/usuarios" element={<Usuarios />} />
            <Route path="coordinador/presupuesto" element={<Presupuesto />} />
            <Route path="coordinador/sedes" element={<Sedes />} />
            <Route path="coordinador/reportes" element={<ReportesCoord />} />
            <Route path="coordinador/alertas" element={<AlertasCoord />} />
            <Route path="coordinador/laboratorios" element={<Laboratorios />} />
            <Route path="coordinador/insumos" element={<InsumosCoord />} />
          </Route>
        </Route>
        <Route path="*" element={<RaizSede />} />
      </Route>
      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  )
}
