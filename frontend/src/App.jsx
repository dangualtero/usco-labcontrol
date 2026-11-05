import { Navigate, Outlet, Route, Routes, useLocation } from 'react-router-dom'
import { Link } from 'react-router-dom'
import { LockKeyhole, Loader2 } from 'lucide-react'
import { useAuth } from './context/AuthContext.jsx'
import Shell from './components/Shell.jsx'
import Login from './pages/Login.jsx'
import Catalogo from './pages/usuario/Catalogo.jsx'
import MisReservas from './pages/usuario/MisReservas.jsx'
import MiHistorial from './pages/usuario/MiHistorial.jsx'
import Ventanilla from './pages/auxiliar/Ventanilla.jsx'
import CierreTurno from './pages/auxiliar/CierreTurno.jsx'
import Dashboard from './pages/coordinador/Dashboard.jsx'
import HistorialInmutable from './pages/coordinador/HistorialInmutable.jsx'
import Inventario from './pages/coordinador/Inventario.jsx'
import Usuarios from './pages/coordinador/Usuarios.jsx'
import InventarioAux from './pages/auxiliar/InventarioAux.jsx'
import ConexionBanner from './components/ConexionBanner.jsx'

const REQUERIDO = {
  USUARIO: 'Esta función es exclusiva de Estudiantes y Docentes',
  AUXILIAR: 'Esta función requiere rol Auxiliar',
  COORDINADOR: 'Disponible únicamente para Coordinador',
}

function RequiereSesion() {
  const { usuario, validando } = useAuth()
  const loc = useLocation()
  if (validando) {
    return (
      <div className="flex min-h-screen flex-col">
        <ConexionBanner />
        <div className="flex flex-1 items-center justify-center gap-2 text-pizarra" role="status"><Loader2 className="h-5 w-5 animate-spin" aria-hidden="true" /> Verificando sesión…</div>
      </div>
    )
  }
  if (!usuario) return <Navigate to="/login" replace state={{ desde: loc.pathname }} />
  return <Shell />
}

/** RBAC en el cliente (PS-02, PS-03). El servidor aplica la misma regla con Spring Security (RNF-03). */
function RequiereRol({ grupo }) {
  const { usuario, home } = useAuth()
  if (usuario.grupo === grupo) return <Outlet />
  return (
    <div className="card mx-auto mt-8 max-w-lg p-8 text-center aparecer" role="alert">
      <div className="mx-auto mb-4 flex h-14 w-14 items-center justify-center rounded-full bg-alerta-fondo"><LockKeyhole className="h-7 w-7 text-alerta-borde" aria-hidden="true" /></div>
      <h1 className="text-xl font-extrabold">Acceso restringido</h1>
      <p className="mt-2 font-semibold text-slate-700">{REQUERIDO[grupo]}</p>
      <p className="mt-1 text-sm text-pizarra">Su rol actual no tiene permiso para ver esta sección.</p>
      <Link to={home} className="btn-primary mt-6">Volver a mi panel principal</Link>
    </div>
  )
}

function Raiz() {
  const { usuario, home } = useAuth()
  return <Navigate to={usuario ? home : '/login'} replace />
}

export default function App() {
  return (
    <Routes>
      <Route path="/login" element={<Login />} />
      <Route path="/" element={<Raiz />} />
      <Route element={<RequiereSesion />}>
        <Route element={<RequiereRol grupo="USUARIO" />}>
          <Route path="/usuario" element={<Navigate to="/usuario/catalogo" replace />} />
          <Route path="/usuario/catalogo" element={<Catalogo />} />
          <Route path="/usuario/reservas" element={<MisReservas />} />
          <Route path="/usuario/historial" element={<MiHistorial />} />
        </Route>
        <Route element={<RequiereRol grupo="AUXILIAR" />}>
          <Route path="/auxiliar" element={<Ventanilla />} />
          <Route path="/auxiliar/turno" element={<CierreTurno />} />
          <Route path="/auxiliar/inventario" element={<InventarioAux />} />
        </Route>
        <Route element={<RequiereRol grupo="COORDINADOR" />}>
          <Route path="/coordinador" element={<Dashboard />} />
          <Route path="/coordinador/historial" element={<HistorialInmutable />} />
          <Route path="/coordinador/inventario" element={<Inventario />} />
          <Route path="/coordinador/usuarios" element={<Usuarios />} />
        </Route>
      </Route>
      <Route path="*" element={<Raiz />} />
    </Routes>
  )
}
