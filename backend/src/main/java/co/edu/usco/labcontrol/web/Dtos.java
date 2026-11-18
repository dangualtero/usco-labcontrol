package co.edu.usco.labcontrol.web;

import jakarta.validation.constraints.NotBlank;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/** Contratos JSON de la API REST. */
public final class Dtos {
    private Dtos() {}

    // =====================================================================================================
    // Autenticación y usuarios
    // =====================================================================================================
    /** sede: slug de la URL desde la que se ingresa (/neiva/login → «neiva»). Opcional. */
    public record LoginRequest(@NotBlank String email, @NotBlank String password, Boolean recordar, String sede) {
        public LoginRequest(String email, String password, Boolean recordar) { this(email, password, recordar, null); }
    }
    public record LaboratorioRefDto(Long id, String nombre) {}
    /**
     * rol: ADMINISTRADOR, COORDINADOR_LAB o MONITOR. permisos: códigos de {@code Permiso} que tiene la cuenta.
     * laboratorios: los asignados (vacío para el Administrador, que ve todos). sede: null para el Administrador.
     */
    public record UsuarioDto(Long id, String nombre, String correo, String rol, String rolTexto, String documento, String programa,
                             String telefono, String sede, String sedeNombre, List<LaboratorioRefDto> laboratorios, List<String> permisos) {}
    public record LoginResponse(String token, UsuarioDto usuario, long expiraEnSegundos) {}
    public record CambiarClaveRequest(String actual, String nueva) {}

    public record UsuarioAdminDto(Long id, String nombre, String correo, String rol, String rolTexto, String documento, String programa,
                                  String telefono, boolean activo, String sede, String sedeNombre, List<LaboratorioRefDto> laboratorios) {}
    /** laboratorios: ids de los laboratorios asignados (obligatorio para COORDINADOR_LAB y MONITOR). */
    public record UsuarioRequest(String nombre, String correo, String rol, String documento, String programa, String telefono,
                                 String password, List<Long> laboratorios) {}
    public record UsuarioActualizarRequest(String nombre, String rol, String documento, String programa, String telefono, List<Long> laboratorios) {}
    public record ActivoRequest(boolean activo) {}
    public record ClaveRequest(String nueva) {}

    // =====================================================================================================
    // Parametrización (Tablero de control)
    // =====================================================================================================
    public record PermisoDto(String codigo, String modulo, String moduloTexto, String descripcion, boolean soloAdministrador,
                             boolean coordinador, boolean monitor, boolean coordinadorPorDefecto, boolean monitorPorDefecto) {}
    /** rol: COORDINADOR_LAB o MONITOR. */
    public record PermisoCambioRequest(String rol, String permiso, boolean habilitado) {}
    public record CatalogoDto(Long id, String tipo, String tipoTexto, String valor, int orden, boolean activo) {}
    public record CatalogoRequest(String tipo, String valor, Integer orden, Boolean activo) {}
    public record ParametroDto(String clave, String valor, String descripcion) {}
    public record ParametroRequest(String valor) {}
    public record PlantillaDto(String codigo, String nombre, String asunto, String cuerpo, boolean activa, List<String> variables) {}
    public record PlantillaRequest(String asunto, String cuerpo, Boolean activa) {}

    // =====================================================================================================
    // Sedes
    // =====================================================================================================
    public record SedeDto(Long id, String slug, String nombre, String ciudad, String direccion, boolean activa) {}
    /** Tarjeta de la sede en la vista de administración: con conteos y saldo del presupuesto. */
    public record SedeAdminDto(SedeDto sede, long equipos, long usuarios, long saldo) {}
    public record SedeRequest(String nombre, String ciudad, String direccion) {}

    // =====================================================================================================
    // Archivos
    // =====================================================================================================
    public record ArchivoDto(String id, String nombre, String tipoContenido, long tamano, String subidoPor, LocalDateTime subidoEn) {}
    /** url: ruta lista para guardar en fotoUrl (ej. /api/imagenes/3f9c…). */
    public record ImagenSubidaDto(String id, String url, long tamano, String tipo) {}

    // =====================================================================================================
    // Laboratorios: caracterización y documentación
    // =====================================================================================================
    public record LaboratorioDto(Long id, String nombre, String ubicacion, String descripcion, boolean activo, long equipos, long insumos,
                                 List<String> coordinadores, List<String> monitores) {}
    public record LaboratorioRequest(String nombre, String ubicacion, String descripcion) {}
    public record DocumentoLabDto(Long id, String tipo, String titulo, String descripcion, ArchivoDto archivo, String subidoPor,
                                  LocalDateTime subidoEn) {}
    public record CaracterizacionDto(LaboratorioDto laboratorio, String codigo, BigDecimal areaM2, Integer capacidad, String horario,
                                     String telefono, String correo, String programas, String servicios, String normasSeguridad,
                                     LocalDateTime actualizadoEn, String actualizadoPor, List<DocumentoLabDto> documentos,
                                     boolean puedeEditar) {}
    public record CaracterizacionRequest(String codigo, BigDecimal areaM2, Integer capacidad, String horario, String telefono, String correo,
                                         String programas, String servicios, String normasSeguridad, String ubicacion, String descripcion) {}

    // =====================================================================================================
    // Inventario y hoja de vida
    // =====================================================================================================
    /**
     * tipo: EQUIPO o INSUMO. cantidad + unidad: existencias (UNIDAD, ML, L, MG, G, KG, CM, M).
     * porcentaje: existencias respecto a cantidadReferencia (null si no aplica). estado: DISPONIBLE, MANTENIMIENTO o FUERA_SERVICIO.
     */
    public record EquipoDto(String placa, String nombre, String marca, String modelo, String numeroSerie,
                            String categoria, String laboratorio, String ubicacion, String descripcion,
                            List<String> specs, List<String> accesorios, String estadoFisico, String fotoUrl,
                            String estado, String tipo, BigDecimal cantidad, String unidad, BigDecimal cantidadReferencia,
                            Integer porcentaje, String observaciones) {}
    public record EquipoAdminDto(EquipoDto equipo, boolean activo, String bajaMotivo, LocalDateTime bajaFecha) {}
    /**
     * Alta y edición de equipos e insumos. El tipo lo define la placa (USCO-EQ-000 equipo, USCO-IN-000 insumo).
     * cantidad/unidad/cantidadReferencia son opcionales para equipos (1 unidad).
     */
    public record EquipoRequest(String placa, String nombre, String marca, String modelo, String numeroSerie,
                                String categoria, String laboratorio, String ubicacion, String descripcion,
                                List<String> specs, List<String> accesorios, String estadoFisico, String fotoUrl,
                                String estado, BigDecimal cantidad, String unidad, BigDecimal cantidadReferencia) {
        public EquipoRequest(String placa, String nombre, String marca, String modelo, String numeroSerie,
                             String categoria, String laboratorio, String ubicacion, String descripcion,
                             List<String> specs, List<String> accesorios, String estadoFisico, String fotoUrl, String estado) {
            this(placa, nombre, marca, modelo, numeroSerie, categoria, laboratorio, ubicacion, descripcion, specs, accesorios,
                    estadoFisico, fotoUrl, estado, null, null, null);
        }
    }
    /** Información operativa que actualizan coordinador y monitor. */
    public record OperativoRequest(String ubicacion, String estadoFisico, String observaciones) {}
    /** estado: DISPONIBLE, MANTENIMIENTO o FUERA_SERVICIO. */
    public record EstadoEquipoRequest(String estado, String motivo) {}
    public record BajaRequest(String motivo) {}
    public record HistorialDto(Long id, LocalDateTime fecha, String tipo, String placa, String equipo, String laboratorio,
                               String referencia, String involucrado, String actor, String detalle) {}
    public record HojaVidaDto(EquipoAdminDto equipo, List<MantenimientoDto> mantenimientos, List<NovedadDto> novedades,
                              List<HistorialDto> eventos) {}

    // =====================================================================================================
    // Mantenimientos
    // =====================================================================================================
    public record MantenimientoDto(Long id, String codigo, String placa, String equipo, String laboratorio, String tipo, String tipoTexto,
                                   String estado, String estadoTexto, String descripcion, LocalDate fechaProgramada, LocalDate fechaRealizada,
                                   String responsable, Long costo, String resultado, Long novedadId, String registradoPor,
                                   LocalDateTime creadoEn, boolean vencido) {}
    /** tipo: PREVENTIVO, CORRECTIVO, CALIBRACION o VERIFICACION. Con fechaProgramada queda PROGRAMADO; sin ella, REQUERIDO. */
    public record MantenimientoRequest(String placa, String tipo, String descripcion, LocalDate fechaProgramada, String responsable, Long costo) {}
    /** estado: PROGRAMADO, EN_EJECUCION, REALIZADO o CANCELADO. equipoOperativo: al realizarlo, ¿el equipo vuelve a operar? */
    public record MantenimientoEstadoRequest(String estado, LocalDate fechaRealizada, String resultado, Boolean equipoOperativo) {}

    // =====================================================================================================
    // Novedades (registro preliminar y validación)
    // =====================================================================================================
    /**
     * tipo: AGOTADO, DANADO, PERDIDO, FALLA, OTRO, EXISTENCIAS (conteo físico) o CONSUMO (cantidad usada).
     * mantenimiento: pedir que el equipo pase a mantenimiento al validar. cantidad + unidad: para EXISTENCIAS y CONSUMO.
     */
    public record NovedadRequest(String tipo, String descripcion, Boolean mantenimiento, BigDecimal cantidad, String unidad) {
        public NovedadRequest(String tipo, String descripcion, Boolean mantenimiento) { this(tipo, descripcion, mantenimiento, null, null); }
    }
    public record NovedadDto(Long id, String sede, String placa, String equipoNombre, String laboratorio, String tipo, String tipoTexto,
                             String descripcion, String reportadoPor, LocalDateTime creadoEn, boolean enMantenimiento, String prioridad,
                             String asunto, String resumenIa, String accionSugerida, String notificacion, String notificacionDetalle,
                             String destinatario, LocalDateTime notificadoEn, String estado, String estadoTexto,
                             LocalDateTime actualizadoEn, String equipoTipo, String validacion, String validacionTexto, String validadoPor,
                             LocalDateTime validadoEn, String observacionValidacion, BigDecimal cantidad, String unidad,
                             List<ArchivoDto> archivos) {}
    public record ValidarNovedadRequest(String observacion) {}
    public record SeguimientoDto(Long id, LocalDateTime fecha, String autor, String estadoAnterior, String estadoNuevo, String comentario) {}
    /** estado: ABIERTO, EN_PROCESO o RESUELTO (null = solo comentario). */
    public record SeguimientoRequest(String estado, String comentario) {}
    /** Huella de las novedades de la sede: si cambia, hay novedades nuevas o actualizadas (para el «tiempo real»). */
    public record VersionReportesDto(String version, long total) {}
    public record ResumenEnviadoDto(boolean enviado, int reportes, int alertasInventario, String destinatario, String detalle) {}

    // =====================================================================================================
    // Solicitudes y requerimientos
    // =====================================================================================================
    /** prioridadPropuesta: ALTA, MEDIA o BAJA. valorEstimado en pesos colombianos. */
    public record SolicitudRequest(Long laboratorioId, String tipo, String titulo, String justificacion, String especificaciones,
                                   BigDecimal cantidad, String unidadMedida, String prioridadPropuesta, Long valorEstimado) {}
    public record SolicitudResumenDto(Long id, String codigo, Long laboratorioId, String laboratorio, String tipo, String titulo,
                                      String prioridadPropuesta, String prioridadAsignada, Integer ordenPrioridad, Long valorEstimado,
                                      Long valorAprobado, String estado, String estadoTexto, String creadoPor, String creadoPorRol,
                                      LocalDateTime creadoEn, LocalDateTime actualizadoEn, LocalDateTime enviadoEn,
                                      String estadoContractual, String estadoContractualTexto) {}
    public record AdjuntoDto(Long id, String tipo, String tipoTexto, ArchivoDto archivo, String agregadoPor, LocalDateTime agregadoEn) {}
    public record EventoSolicitudDto(Long id, LocalDateTime fecha, String autor, String accion, String estadoAnterior, String estadoNuevo,
                                     String comentario) {}
    /** Datos presupuestales y contractuales (null si la cuenta no tiene permiso para verlos). */
    public record PresupuestoSolicitudDto(String rubro, String fuenteFinanciacion, String cdpNumero, LocalDate cdpFecha, Long valorAprobado,
                                          String modalidadContratacion, String contratoNumero, String contratista, Long valorContratado,
                                          LocalDate fechaContrato, String estadoContractual, String estadoContractualTexto,
                                          String observacionContractual, List<MovimientoDto> movimientos) {}
    /** Qué puede hacer la cuenta con esta solicitud (para mostrar los botones). */
    public record AccionesSolicitudDto(boolean editar, boolean enviar, String enviarTexto, boolean devolver, boolean priorizar,
                                       boolean decidir, boolean cerrar, boolean cancelar, boolean presupuesto, boolean adjuntar,
                                       boolean registrarAvance) {}
    public record SolicitudDetalleDto(SolicitudResumenDto resumen, String justificacion, String especificaciones, BigDecimal cantidad,
                                      String unidadMedida, String observacionDevolucion, String decisionMotivo, String decididoPor,
                                      LocalDateTime decididoEn, String cierreNota, LocalDateTime cerradoEn, PresupuestoSolicitudDto presupuesto,
                                      List<AdjuntoDto> adjuntos, List<EventoSolicitudDto> eventos, List<AvanceDto> avances,
                                      AccionesSolicitudDto acciones) {}
    public record TextoRequest(String texto) {}
    /** prioridad: ALTA, MEDIA o BAJA. orden: posición en la lista de priorización (1 = primera). */
    public record PriorizarRequest(String prioridad, Integer orden, String comentario) {}
    /** decision: APROBADA o RECHAZADA. */
    public record DecisionRequest(String decision, String motivo) {}
    public record PresupuestoRequest(String rubro, String fuenteFinanciacion, String cdpNumero, LocalDate cdpFecha, Long valorAprobado) {}
    /**
     * estadoContractual: SIN_INICIAR, EN_PROCESO, CONTRATADO, EN_EJECUCION o LIQUIDADO.
     * registrarGasto: además crea el gasto en el presupuesto de la sede por el valor contratado (una sola vez).
     */
    public record ContratacionRequest(String modalidad, String contratoNumero, String contratista, Long valorContratado,
                                      LocalDate fechaContrato, String estadoContractual, String observacion, Boolean registrarGasto,
                                      String categoriaGasto) {}

    // =====================================================================================================
    // Presupuesto (por sede)
    // =====================================================================================================
    public record MovimientoDto(Long id, String tipo, String concepto, String categoria, long monto, LocalDate fecha,
                                String placa, String registradoPor, LocalDateTime registradoEn, boolean anulado,
                                String anulacionMotivo, String anuladoPor, LocalDateTime anuladoEn, String solicitudCodigo) {}
    public record CategoriaTotalDto(String categoria, long total) {}
    public record PresupuestoDto(SedeDto sede, long ingresos, long gastos, long saldo,
                                 List<CategoriaTotalDto> gastosPorCategoria, List<MovimientoDto> movimientos) {}
    public record PresupuestoSedeDto(SedeDto sede, long ingresos, long gastos, long saldo, long movimientos) {}
    public record ResumenPresupuestoDto(List<PresupuestoSedeDto> sedes, long ingresos, long gastos, long saldo) {}
    /** tipo: INGRESO (suma) o GASTO (resta). monto en pesos colombianos, sin decimales. */
    public record MovimientoRequest(String tipo, String concepto, String categoria, Long monto, LocalDate fecha, String placa) {}
    public record AnularRequest(String motivo) {}

    // =====================================================================================================
    // Reportes y seguimiento: compromisos, avances y evidencias
    // =====================================================================================================
    public record CompromisoDto(Long id, Long laboratorioId, String laboratorio, String titulo, String descripcion, Long responsableId,
                                String responsable, LocalDate fechaLimite, String estado, String estadoTexto, boolean vencido,
                                long avances, Integer ultimoPorcentaje, String creadoPor, LocalDateTime creadoEn) {}
    /** laboratorioId null = compromiso de toda la sede (solo el Administrador). */
    public record CompromisoRequest(Long laboratorioId, String titulo, String descripcion, Long responsableId, LocalDate fechaLimite) {}
    /** estado: PENDIENTE, EN_CURSO, CUMPLIDO o CANCELADO. */
    public record EstadoRequest(String estado) {}
    public record AvanceDto(Long id, Long laboratorioId, String laboratorio, Long compromisoId, String compromiso, Long solicitudId,
                            String solicitud, String descripcion, Integer porcentaje, String autor, LocalDateTime creadoEn,
                            String verificacion, String verificacionTexto, String verificadoPor, LocalDateTime verificadoEn,
                            String observacionVerificacion, List<ArchivoDto> evidencias) {}
    public record AvanceRequest(Long laboratorioId, Long compromisoId, Long solicitudId, String descripcion, Integer porcentaje) {}
    /** resultado: VERIFICADO u OBSERVADO. */
    public record VerificarRequest(String resultado, String observacion) {}
    public record PersonaDto(Long id, String nombre, String rol) {}

    /** Indicadores de un laboratorio (o el total, con laboratorioId null). */
    public record IndicadorLabDto(Long laboratorioId, String laboratorio, long equipos, long insumos, long insumosEnAlerta,
                                  long equiposFueraServicio, long mantenimientosAbiertos, long mantenimientosVencidos,
                                  long mantenimientosRealizados, long novedadesPorValidar, long novedadesAbiertas,
                                  long solicitudesEnTramite, long solicitudesAprobadas, long solicitudesRechazadas,
                                  long valorSolicitado, long valorAprobado, long compromisos, long compromisosCumplidos,
                                  long compromisosVencidos, long avancesPendientes, long documentos) {}
    public record IndicadoresDto(List<IndicadorLabDto> laboratorios, IndicadorLabDto total) {}

    /** Tablero: indicadores y lo que requiere atención. */
    public record TableroDto(IndicadoresDto indicadores, Map<String, Long> solicitudesPorEstado, List<MantenimientoDto> proximosMantenimientos,
                             List<SolicitudResumenDto> solicitudesRecientes, List<NovedadDto> novedadesPorValidar,
                             List<CompromisoDto> compromisosProximos) {}

    // =====================================================================================================
    // Alertas (se calculan al consultar)
    // =====================================================================================================
    /**
     * tipo: AGOTADO, STOCK_BAJO, NOVEDAD_POR_VALIDAR, NOVEDAD, MANTENIMIENTO_VENCIDO, MANTENIMIENTO_PROXIMO, FUERA_SERVICIO,
     * SOLICITUD_POR_VALIDAR, SOLICITUD_DEVUELTA, SOLICITUD_SIN_DECISION o COMPROMISO_VENCIDO.
     * nivel: CRITICA, ADVERTENCIA o INFO. enlace: ruta del frontend (sin la sede) para atenderla.
     */
    public record AlertaDto(String id, String tipo, String nivel, String titulo, String detalle, String placa, String elemento,
                            String elementoTipo, String laboratorio, Integer porcentaje, LocalDateTime fecha, String enlace) {}
}
