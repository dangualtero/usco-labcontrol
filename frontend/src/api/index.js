// Cliente de la API REST de USCO LabControl (Spring Boot + MySQL). No existe modo de respaldo: si el servidor
// no responde, la aplicación lo informa y no muestra datos inventados.

export const MSG_CONEXION =
  'Error de conexión con el servidor USCO LabControl - Verifique que la API Spring Boot esté activa'

export class ApiError extends Error {
  constructor(message, status = 400) { super(message); this.status = status }
}

let token = null
export const setToken = (t) => { token = t }

// Sede activa (segmento de la URL: /neiva, /pitalito…). Se envía en cada petición en la cabecera X-Sede.
// El servidor solo la usa para el Administrador general; a coordinadores y monitores siempre les aplica su propia sede.
let sede = null
export const setSede = (s) => { sede = s || null }
export const getSede = () => sede

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

async function pedir(metodo, ruta, { cuerpo, params, texto, form } = {}) {
  const qs = params
    ? '?' + new URLSearchParams(Object.entries(params).filter(([, v]) => v !== undefined && v !== null && v !== '')).toString()
    : ''
  let res
  try {
    res = await fetch(`/api${ruta}${qs}`, {
      method: metodo,
      headers: {
        ...(cuerpo !== undefined ? { 'Content-Type': 'application/json' } : {}),
        ...(token ? { Authorization: `Bearer ${token}` } : {}),
        ...(sede ? { 'X-Sede': sede } : {}),
        // Evita que el túnel gratuito de ngrok responda con su página de aviso en lugar de los datos
        'ngrok-skip-browser-warning': '1',
      },
      // FormData: el navegador pone el Content-Type multipart con su boundary
      body: form ?? (cuerpo !== undefined ? JSON.stringify(cuerpo) : undefined),
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
const del = (r) => pedir('DELETE', r)
const todos = (v) => (v === 'todos' ? '' : v)
const enc = encodeURIComponent

/** Arma un FormData: los arreglos de archivos se agregan con el mismo nombre; null/undefined se omiten. */
const formulario = (campos) => {
  const f = new FormData()
  Object.entries(campos).forEach(([k, v]) => {
    if (v === undefined || v === null || v === '') return
    if (Array.isArray(v)) v.forEach((a) => f.append(k, a, a.name))
    else if (v instanceof Blob) f.append(k, v, v.name || 'archivo')
    else f.append(k, String(v))
  })
  return f
}
const enviarForm = (ruta, campos) => pedir('POST', ruta, { form: formulario(campos) })

/** Descarga un archivo protegido (con el token de la sesión) y devuelve un Blob. */
export async function descargarProtegido(ruta) {
  let res
  try {
    res = await fetch(`/api${ruta}`, {
      headers: { ...(token ? { Authorization: `Bearer ${token}` } : {}), ...(sede ? { 'X-Sede': sede } : {}), 'ngrok-skip-browser-warning': '1' },
    })
  } catch { marcar(false); throw new ApiError(MSG_CONEXION, 0) }
  if (!res.ok) {
    let msg = 'No se pudo descargar el archivo'
    try { msg = (await res.json()).error ?? msg } catch { /* sin cuerpo JSON */ }
    throw new ApiError(msg, res.status)
  }
  return res.blob()
}

export const api = {
  // ---- Sesión
  salud: () => get('/salud'),
  login: ({ email, password, recordar }) => post('/auth/login', { email, password, recordar: !!recordar, sede }),
  sedes: () => get('/sedes'),
  yo: () => get('/auth/yo'),
  cambiarClave: (actual, nueva) => post('/auth/cambiar-clave', { actual, nueva }),

  // ---- Tablero, alertas, indicadores y reportes
  tablero: () => get('/tablero'),
  alertas: () => get('/alertas'),
  indicadores: () => get('/reportes/indicadores'),
  reporteCsv: (tipo, f = {}) => pedir('GET', `/reportes/${tipo}.csv`, { params: { ...f, laboratorio: todos(f.laboratorio) }, texto: true }),
  historial: (f = {}) => get('/historial', { ...f, laboratorio: todos(f.laboratorio) }),

  // ---- Catálogos (valores activos, para formularios)
  catalogos: () => get('/catalogos'),

  // ---- Laboratorios: caracterización y documentación
  laboratorios: () => get('/laboratorios'),
  laboratoriosRef: () => get('/laboratorios/referencias'),
  laboratorio: (id) => get(`/laboratorios/${id}`),
  crearLaboratorio: (r) => post('/laboratorios', r),
  actualizarLaboratorio: (id, r) => put(`/laboratorios/${id}`, r),
  activarLaboratorio: (id, activo) => put(`/laboratorios/${id}/activo`, { activo }),
  caracterizar: (id, r) => put(`/laboratorios/${id}/caracterizacion`, r),
  subirDocumento: (id, { tipo, titulo, descripcion, archivo }) => enviarForm(`/laboratorios/${id}/documentos`, { tipo, titulo, descripcion, archivo }),
  quitarDocumento: (id, docId) => del(`/laboratorios/${id}/documentos/${docId}`),

  // ---- Inventario
  inventario: (f = {}) => get('/inventario', { tipo: todos(f.tipo), laboratorio: todos(f.laboratorio), q: f.q, bajas: f.bajas ? true : undefined }),
  hojaVida: (placa) => get(`/inventario/${enc(placa)}`),
  crearEquipo: (r) => post('/inventario', r),
  actualizarEquipo: (placa, r) => put(`/inventario/${enc(placa)}`, r),
  operativo: (placa, r) => put(`/inventario/${enc(placa)}/operativo`, r),
  estadoEquipo: (placa, estado, motivo) => post(`/inventario/${enc(placa)}/estado`, { estado, motivo }),
  baja: (placa, motivo) => post(`/inventario/${enc(placa)}/baja`, { motivo }),
  subirImagen: (archivo) => enviarForm('/inventario/imagenes', { archivo }),

  // ---- Mantenimientos
  mantenimientos: (f = {}) => get('/mantenimientos', { estado: todos(f.estado), laboratorio: todos(f.laboratorio), placa: f.placa }),
  crearMantenimiento: (r) => post('/mantenimientos', r),
  actualizarMantenimiento: (id, r) => put(`/mantenimientos/${id}`, r),
  estadoMantenimiento: (id, r) => post(`/mantenimientos/${id}/estado`, r),

  // ---- Novedades: registro preliminar y validación
  novedades: (f = {}) => get('/novedades', { validacion: todos(f.validacion), laboratorio: todos(f.laboratorio), placa: f.placa }),
  novedadesVersion: () => get('/novedades/version'),
  reportarNovedad: (placa, { tipo, descripcion, mantenimiento, cantidad, unidad, archivos }) =>
    enviarForm(`/novedades/${enc(placa)}`, { tipo, descripcion, mantenimiento, cantidad, unidad, archivos }),
  validarNovedad: (id, observacion) => post(`/novedades/${id}/validar`, { observacion }),
  rechazarNovedad: (id, observacion) => post(`/novedades/${id}/rechazar`, { observacion }),
  reenviarNovedad: (id, ahora = false) => pedir('POST', `/novedades/${id}/reenviar`, { cuerpo: {}, params: ahora ? { ahora: true } : undefined }),
  seguimientoNovedad: (id) => get(`/novedades/${id}/seguimiento`),
  seguirNovedad: (id, r) => post(`/novedades/${id}/seguimiento`, r),

  // ---- Solicitudes y requerimientos
  solicitudes: (f = {}) => get('/solicitudes', { estado: todos(f.estado), laboratorioId: todos(f.laboratorioId), q: f.q }),
  solicitud: (id) => get(`/solicitudes/${id}`),
  crearSolicitud: (r) => post('/solicitudes', r),
  actualizarSolicitud: (id, r) => put(`/solicitudes/${id}`, r),
  adjuntar: (id, tipo, archivo) => enviarForm(`/solicitudes/${id}/adjuntos`, { tipo, archivo }),
  quitarAdjunto: (id, adjuntoId) => del(`/solicitudes/${id}/adjuntos/${adjuntoId}`),
  enviarSolicitud: (id, texto) => post(`/solicitudes/${id}/enviar`, { texto }),
  devolverSolicitud: (id, texto) => post(`/solicitudes/${id}/devolver`, { texto }),
  priorizar: (id, r) => post(`/solicitudes/${id}/priorizar`, r),
  decidir: (id, decision, motivo) => post(`/solicitudes/${id}/decidir`, { decision, motivo }),
  cerrarSolicitud: (id, texto) => post(`/solicitudes/${id}/cerrar`, { texto }),
  cancelarSolicitud: (id, texto) => post(`/solicitudes/${id}/cancelar`, { texto }),
  presupuestoSolicitud: (id, r) => put(`/solicitudes/${id}/presupuesto`, r),
  contratacion: (id, r) => put(`/solicitudes/${id}/contratacion`, r),

  // ---- Presupuesto de la sede
  presupuesto: () => get('/presupuesto'),
  presupuestoResumen: () => get('/presupuesto/resumen'),
  registrarMovimiento: (r) => post('/presupuesto/movimientos', r),
  anularMovimiento: (id, motivo) => post(`/presupuesto/movimientos/${id}/anular`, { motivo }),

  // ---- Seguimiento: compromisos y avances
  compromisos: (f = {}) => get('/seguimiento/compromisos', { estado: todos(f.estado), laboratorioId: todos(f.laboratorioId) }),
  crearCompromiso: (r) => post('/seguimiento/compromisos', r),
  actualizarCompromiso: (id, r) => put(`/seguimiento/compromisos/${id}`, r),
  estadoCompromiso: (id, estado) => post(`/seguimiento/compromisos/${id}/estado`, { estado }),
  personas: (laboratorioId) => get('/seguimiento/personas', { laboratorioId }),
  avances: (f = {}) => get('/seguimiento/avances', f),
  registrarAvance: ({ laboratorioId, compromisoId, solicitudId, descripcion, porcentaje, evidencias }) =>
    enviarForm('/seguimiento/avances', { laboratorioId, compromisoId, solicitudId, descripcion, porcentaje, evidencias }),
  verificarAvance: (id, resultado, observacion) => post(`/seguimiento/avances/${id}/verificar`, { resultado, observacion }),

  // ---- Archivos privados
  archivo: (id) => descargarProtegido(`/archivos/${id}`),

  // ---- Administración (Administrador general)
  usuarios: () => get('/admin/usuarios'),
  crearUsuario: (r) => post('/admin/usuarios', r),
  actualizarUsuario: (id, r) => put(`/admin/usuarios/${id}`, r),
  usuarioActivo: (id, activo) => put(`/admin/usuarios/${id}/activo`, { activo }),
  usuarioClave: (id, nueva) => post(`/admin/usuarios/${id}/clave`, { nueva }),
  sedesAdmin: () => get('/admin/sedes'),
  crearSede: (r) => post('/admin/sedes', r),
  actualizarSede: (slug, r) => put(`/admin/sedes/${enc(slug)}`, r),
  activarSede: (slug, activo) => put(`/admin/sedes/${enc(slug)}/activa`, { activo }),
  permisos: () => get('/admin/permisos'),
  cambiarPermiso: (rol, permiso, habilitado) => put('/admin/permisos', { rol, permiso, habilitado }),
  restablecerPermisos: () => post('/admin/permisos/restablecer'),
  catalogosAdmin: (tipo) => get('/admin/catalogos', { tipo }),
  crearCatalogo: (r) => post('/admin/catalogos', r),
  actualizarCatalogo: (id, r) => put(`/admin/catalogos/${id}`, r),
  parametros: () => get('/admin/parametros'),
  cambiarParametro: (clave, valor) => put(`/admin/parametros/${enc(clave)}`, { valor }),
  plantillas: () => get('/admin/plantillas'),
  cambiarPlantilla: (codigo, r) => put(`/admin/plantillas/${enc(codigo)}`, r),
  enviarResumen: () => post('/admin/resumen'),
}
