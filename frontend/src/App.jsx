import { Navigate, Outlet, Route, Routes, useLocation, Link } from 'react-router-dom'
import { LockKeyhole, Loader2 } from 'lucide-react'
import { useAuth } from './context/AuthContext.jsx'
import { SedeLayout, useSede } from './context/SedeContext.jsx'
import Shell from './components/Shell.jsx'
import ConexionBanner from './components/ConexionBanner.jsx'
import Login from './pages/Login.jsx'
import ElegirSede from './pages/ElegirSede.jsx'
import Tablero from './pages/tablero/Tablero.jsx'
import Alertas from './pages/tablero/Alertas.jsx'
import Laboratorios from './pages/laboratorios/Laboratorios.jsx'
import LaboratorioDetalle from './pages/laboratorios/LaboratorioDetalle.jsx'
import Inventario from './pages/inventario/Inventario.jsx'
import HojaVida from './pages/inventario/HojaVida.jsx'
import Novedades from './pages/inventario/Novedades.jsx'
import Mantenimientos from './pages/inventario/Mantenimientos.jsx'
import Solicitudes from './pages/solicitudes/Solicitudes.jsx'
import SolicitudDetalle from './pages/solicitudes/SolicitudDetalle.jsx'
import Presupuesto from './pages/presupuesto/Presupuesto.jsx'
import Seguimiento from './pages/seguimiento/Seguimiento.jsx'
import Reportes from './pages/seguimiento/Reportes.jsx'
import Historial from './pages/seguimiento/Historial.jsx'
import Usuarios from './pages/admin/Usuarios.jsx'
import Parametrizacion from './pages/admin/Parametrizacion.jsx'
import Sedes from './pages/admin/Sedes.jsx'
import { puede } from './lib/constantes.js'
import { PERMISOS_DE_RUTA, modulosDe } from './lib/modulos.js'

/**
 * Exige sesión dentro de /:sede. Coordinadores y monitores solo pueden estar en la URL de SU sede: si abren la de otra
 * se les lleva a la misma pantalla en su sede. El Administrador general navega por todas.
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

/** Control de acceso en el cliente según la matriz de permisos. El servidor aplica la misma regla en cada acción. */
function RequierePermiso({ modulo }) {
  const { usuario } = useAuth()
  const { inicio } = useSede()
  if (puede(usuario, ...(PERMISOS_DE_RUTA[modulo] ?? []))) return <Outlet />
  return (
    <div className="card mx-auto mt-8 max-w-lg p-8 text-center aparecer" role="alert">
      <div className="mx-auto mb-4 flex h-14 w-14 items-center justify-center rounded-full bg-alerta-fondo"><LockKeyhole className="h-7 w-7 text-alerta-borde" aria-hidden="true" /></div>
      <h1 className="text-xl font-extrabold">Acceso restringido</h1>
      <p className="mt-2 font-semibold text-slate-700">Tu rol ({usuario?.rolTexto}) no tiene permiso para este módulo.</p>
      <p className="mt-1 text-sm text-pizarra">Si lo necesitas, pídele al Administrador general que lo habilite en Parametrización → Permisos.</p>
      <Link to={inicio(usuario)} className="btn-primary mt-6">Volver al inicio</Link>
    </div>
  )
}

/** /:sede sin más ruta → inicio de la cuenta o ingreso de esa sede. */
function RaizSede() {
  const { usuario, cerrar } = useAuth()
  const { ruta, inicio } = useSede()
  if (usuario && modulosDe(usuario).length === 0) {
    return (
      <div className="flex min-h-screen items-center justify-center p-6">
        <div className="card max-w-md p-8 text-center" role="alert">
          <h1 className="text-xl font-extrabold">Sin módulos habilitados</h1>
          <p className="mt-2 text-sm text-pizarra">Tu rol no tiene ningún permiso activo. Pídele al Administrador general que revise la matriz de permisos.</p>
          <button className="btn-primary mt-6" onClick={() => cerrar('manual')}>Cerrar sesión</button>
        </div>
      </div>
    )
  }
  return <Navigate to={usuario ? inicio(usuario) : ruta('/login')} replace />
}

/** /login (dirección antigua) → ingreso de la sede del usuario, o «Elige tu sede». */
function LoginSinSede() {
  const { usuario, home } = useAuth()
  return <Navigate to={usuario ? home : '/'} replace />
}

const RUTAS = [
  ['/tablero', [['tablero', <Tablero key="t" />]]],
  ['/alertas', [['alertas', <Alertas key="a" />]]],
  ['/laboratorios', [['laboratorios', <Laboratorios key="l" />], ['laboratorios/:id', <LaboratorioDetalle key="ld" />]]],
  ['/inventario', [['inventario', <Inventario key="i" />], ['inventario/:placa', <HojaVida key="hv" />]]],
  ['/novedades', [['novedades', <Novedades key="n" />]]],
  ['/mantenimientos', [['mantenimientos', <Mantenimientos key="m" />]]],
  ['/solicitudes', [['solicitudes', <Solicitudes key="s" />], ['solicitudes/:id', <SolicitudDetalle key="sd" />]]],
  ['/presupuesto', [['presupuesto', <Presupuesto key="p" />]]],
  ['/seguimiento', [['seguimiento', <Seguimiento key="sg" />]]],
  ['/reportes', [['reportes', <Reportes key="r" />]]],
  ['/historial', [['historial', <Historial key="h" />]]],
  ['/usuarios', [['usuarios', <Usuarios key="u" />]]],
  ['/parametrizacion', [['parametrizacion', <Parametrizacion key="pz" />]]],
  ['/sedes', [['sedes', <Sedes key="sd2" />]]],
]

export default function App() {
  return (
    <Routes>
      <Route path="/" element={<ElegirSede />} />
      <Route path="/login" element={<LoginSinSede />} />
      {/* Cada sede tiene su propia dirección: /neiva, /pitalito, /garzon… */}
      <Route path="/:sede" element={<SedeLayout />}>
        <Route index element={<RaizSede />} />
        <Route path="login" element={<Login />} />
        <Route element={<RequiereSesion />}>
          {RUTAS.map(([modulo, rutas]) => (
            <Route key={modulo} element={<RequierePermiso modulo={modulo} />}>
              {rutas.map(([path, el]) => <Route key={path} path={path} element={el} />)}
            </Route>
          ))}
        </Route>
        <Route path="*" element={<RaizSede />} />
      </Route>
      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  )
}
