// Cliente de la API REST de USCO LabControl (Spring Boot + MySQL). No existe modo de respaldo: si el servidor
// no responde, la aplicación lo informa y no muestra datos inventados.

export const MSG_CONEXION =
  'Error de conexión con el servidor USCO LabControl - Verifique que la API Spring Boot esté activa'

export class ApiError extends Error {
  constructor(message, status = 400) { super(message); this.status = status }
}

let token = null
export const setToken = (t) => { token = t }

// ---- Estado de conexión con la API (lo consume el banner global) ----
let conectado = true
const oyentes = new Set()
function marcar(ok) {
  if (conectado === ok) return
  conectado = ok
  oyentes.forEach((f) => f(ok))
}
export const estaConectado = () => conectado
export const suscribirConexion = (f) => { oyentes.add(f); return () => oyentes.delete(f) }
/** Pide a todas las pantallas que vuelvan a consultar la API. */
export const reintentarTodo = () => window.dispatchEvent(new Event('labcontrol:reintentar'))

async function pedir(metodo, ruta, { cuerpo, params, texto } = {}) {
  const qs = params
    ? '?' + new URLSearchParams(Object.entries(params).filter(([, v]) => v !== undefined && v !== null && v !== '')).toString()
    : ''
  let res
  try {
    res = await fetch(`/api${ruta}${qs}`, {
      method: metodo,
      headers: { ...(cuerpo !== undefined ? { 'Content-Type': 'application/json' } : {}), ...(token ? { Authorization: `Bearer ${token}` } : {}) },
      body: cuerpo !== undefined ? JSON.stringify(cuerpo) : undefined,
    })
  } catch {
    marcar(false)
    throw new ApiError(MSG_CONEXION, 0)
  }
  // El proxy de desarrollo responde 5xx sin cuerpo JSON cuando la API está apagada.
  const esJson = (res.headers.get('content-type') ?? '').includes('json')
  if (!res.ok && !esJson && res.status >= 500) {
    marcar(false)
    throw new ApiError(MSG_CONEXION, 0)
  }
  marcar(true)
  if (!res.ok) {
    let msg = 'Ocurrió un error inesperado. Intente nuevamente.'
    try { msg = (await res.json()).error ?? msg } catch { /* cuerpo no JSON */ }
    throw new ApiError(msg, res.status)
  }
  return texto ? res.text() : res.json()
}

const get = (r, params) => pedir('GET', r, { params })
const post = (r, cuerpo = {}) => pedir('POST', r, { cuerpo })
const put = (r, cuerpo = {}) => pedir('PUT', r, { cuerpo })
const todos = (v) => (v === 'todos' ? '' : v)

export const api = {
  // Autenticación
  salud: () => get('/salud'),
  login: ({ email, password, recordar }) => post('/auth/login', { email, password, recordar: !!recordar }),
  yo: () => get('/auth/yo'),
  cambiarClave: (actual, nueva) => post('/auth/cambiar-clave', { actual, nueva }),

  // Catálogo
  equipos: (f = {}) => get('/equipos', { laboratorio: todos(f.laboratorio), categoria: todos(f.categoria), q: f.q }),
  equipoDetalle: (placa) => get(`/equipos/${encodeURIComponent(placa)}`),

  // Estudiante / Docente
  reservar: (r) => post('/prestamos/reservas', r),
  cancelarReserva: (id) => pedir('DELETE', `/prestamos/reservas/${id}`),
  misPrestamos: () => get('/prestamos/mis'),

  // Auxiliar (ventanilla)
  panel: () => get('/ventanilla/panel'),
  solicitudesUsuario: (criterio) => get(`/ventanilla/usuarios/${encodeURIComponent(criterio)}/solicitudes`),
  salida: (r) => post('/ventanilla/salida', r),
  entrada: (r) => post('/ventanilla/entrada', r),
  rechazarReserva: (id, motivo) => post(`/ventanilla/reservas/${id}/rechazar`, { motivo }),
  cierreTurno: () => get('/ventanilla/cierre-turno'),
  transferirCustodia: (id) => post('/ventanilla/cierre-turno', { auxiliarDestinoId: id }),
  estadoEquipoAux: (placa, estado, motivo) => post(`/ventanilla/equipos/${encodeURIComponent(placa)}/estado`, { estado, motivo }),

  // Coordinador
  kpis: (f = {}) => get('/coordinador/kpis', f),
  historial: (f = {}) => get('/coordinador/historial', { ...f, laboratorio: todos(f.laboratorio) }),
  reporteCsv: (f = {}) => pedir('GET', '/coordinador/reporte.csv', { params: { ...f, laboratorio: todos(f.laboratorio) }, texto: true }),
  equiposAdmin: () => get('/coordinador/equipos'),
  crearEquipo: (r) => post('/coordinador/equipos', r),
  actualizarEquipo: (placa, r) => put(`/coordinador/equipos/${encodeURIComponent(placa)}`, r),
  estadoEquipo: (placa, estado, motivo) => post(`/coordinador/equipos/${encodeURIComponent(placa)}/estado`, { estado, motivo }),
  baja: (placa, motivo) => post(`/coordinador/equipos/${encodeURIComponent(placa)}/baja`, { motivo }),
  usuarios: () => get('/coordinador/usuarios'),
  crearUsuario: (r) => post('/coordinador/usuarios', r),
  usuarioActivo: (id, activo) => put(`/coordinador/usuarios/${id}/activo`, { activo }),
  usuarioClave: (id, nueva) => post(`/coordinador/usuarios/${id}/clave`, { nueva }),
}
