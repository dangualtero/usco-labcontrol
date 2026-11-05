package co.edu.usco.labcontrol.web;

import jakarta.validation.constraints.NotBlank;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** Contratos JSON de la API REST. */
public final class Dtos {
    private Dtos() {}

    // ---- Autenticación ----
    /** sede: slug de la URL desde la que se ingresa (/neiva/login → «neiva»). Opcional. */
    public record LoginRequest(@NotBlank String email, @NotBlank String password, Boolean recordar, String sede) {
        public LoginRequest(String email, String password, Boolean recordar) { this(email, password, recordar, null); }
    }
    /** sede/sedeNombre: null para el Coordinador general (administra todas las sedes). */
    public record UsuarioDto(Long id, String nombre, String correo, String rol, String grupo, String documento,
                             String programa, String codigoEstudiantil, String sede, String sedeNombre) {}
    public record LoginResponse(String token, UsuarioDto usuario, long expiraEnSegundos) {}
    public record CambiarClaveRequest(String actual, String nueva) {}

    // ---- Catálogo (vista para usuarios: NUNCA incluye quién tiene el equipo, PS-05) ----
    public record EquipoDto(String placa, String nombre, String marca, String modelo, String numeroSerie,
                            String categoria, String laboratorio, String ubicacion, String descripcion,
                            List<String> specs, List<String> accesorios, String estadoFisico, String fotoUrl,
                            String estado) {}
    public record UsoPublicoDto(String idAnonimo, LocalDateTime salida, LocalDateTime entrada) {}
    public record EquipoDetalleDto(EquipoDto equipo, List<UsoPublicoDto> historialPublico) {}

    // ---- Préstamos ----
    public record ReservaRequest(String placa, String asignatura, String docente, LocalDateTime inicio, LocalDateTime fin) {}

    public record PrestamoDto(Long id, String codigo, String placa, String equipoNombre, String laboratorio, String fotoUrl,
                              String solicitanteNombre, String solicitanteDocumento, String asignatura, String docente,
                              String estado, boolean vencido,
                              LocalDateTime franjaInicio, LocalDateTime franjaFin, LocalDateTime fechaSolicitud,
                              LocalDateTime fechaSalida, LocalDateTime fechaLimite, LocalDateTime fechaEntrada,
                              String estadoEntrada, String observaciones, String incidencia,
                              String auxiliarSalida, String auxiliarEntrada, String custodio) {}

    // ---- Ventanilla (Auxiliar) ----
    public record ResumenDto(long disponibles, long prestados, long mantenimiento, long vencidos) {}
    public record PanelDto(ResumenDto resumen, List<PrestamoDto> reservas, List<PrestamoDto> activos) {}
    public record SolicitudesUsuarioDto(String nombre, String documento, String codigoEstudiantil, String programa,
                                        long activos, int limite, List<PrestamoDto> reservas) {}
    public record SalidaRequest(String placa, String documento, Integer horas) {}
    public record EntradaRequest(Long prestamoId, boolean buenEstado, boolean danos, boolean accesoriosIncompletos,
                                 boolean danosMenoresPreexistentes, String observaciones) {}
    public record EntradaResponse(PrestamoDto prestamo, EquipoDto equipo, String incidencia) {}
    public record RechazoRequest(String motivo) {}
    public record AuxiliarDto(Long id, String nombre) {}
    public record CierreTurnoDto(List<PrestamoDto> pendientes, List<AuxiliarDto> auxiliares) {}
    public record TransferenciaRequest(Long auxiliarDestinoId) {}
    public record TransferenciaResponse(int transferidos, String destino) {}
    /** estado: DISPONIBLE o MANTENIMIENTO. */
    public record EstadoEquipoRequest(String estado, String motivo) {}

    // ---- Coordinador ----
    public record UsoLaboratorioDto(String laboratorio, long total) {}
    public record SerieDiaDto(String fecha, long total) {}
    public record KpisDto(long totalEquipos, long disponibles, long prestados, long mantenimiento,
                          long activos, long vencidos, long prestamosPeriodo, long devueltosPeriodo,
                          long incidenciasPeriodo, long tiempoPromedioMin, long devolucionesATiempoPct,
                          List<UsoLaboratorioDto> usoPorLaboratorio, List<SerieDiaDto> serieDiaria) {}
    public record HistorialDto(Long id, LocalDateTime fecha, String tipo, String placa, String equipo,
                               String laboratorio, String prestamo, String usuario, String auxiliar, String detalle) {}
    public record EquipoAdminDto(EquipoDto equipo, boolean activo, String bajaMotivo, LocalDateTime bajaFecha,
                                 String ultimoResponsable) {}
    public record BajaRequest(String motivo) {}
    /** Alta y edición de equipos. accesorios y specs se envían como listas. */
    public record EquipoRequest(String placa, String nombre, String marca, String modelo, String numeroSerie,
                                String categoria, String laboratorio, String ubicacion, String descripcion,
                                List<String> specs, List<String> accesorios, String estadoFisico, String fotoUrl,
                                String estado) {}
    public record UsuarioAdminDto(Long id, String nombre, String correo, String rol, String documento,
                                  String codigoEstudiantil, String programa, boolean activo, String sede, String sedeNombre) {}
    public record UsuarioRequest(String nombre, String correo, String rol, String documento,
                                 String codigoEstudiantil, String programa, String password) {}
    public record ActivoRequest(boolean activo) {}
    public record ClaveRequest(String nueva) {}

    // ---- Sedes ----
    public record SedeDto(Long id, String slug, String nombre, String ciudad, String direccion, boolean activa) {}
    /** Tarjeta de la sede en la vista de administración: con conteos y saldo del presupuesto. */
    public record SedeAdminDto(SedeDto sede, long equipos, long usuarios, long saldo) {}
    public record SedeRequest(String nombre, String ciudad, String direccion) {}

    // ---- Presupuesto (por sede) ----
    public record MovimientoDto(Long id, String tipo, String concepto, String categoria, long monto, LocalDate fecha,
                                String placa, String registradoPor, LocalDateTime registradoEn, boolean anulado,
                                String anulacionMotivo, String anuladoPor, LocalDateTime anuladoEn) {}
    public record CategoriaTotalDto(String categoria, long total) {}
    public record PresupuestoDto(SedeDto sede, long ingresos, long gastos, long saldo,
                                 List<CategoriaTotalDto> gastosPorCategoria, List<MovimientoDto> movimientos) {}
    public record PresupuestoSedeDto(SedeDto sede, long ingresos, long gastos, long saldo, long movimientos) {}
    public record ResumenPresupuestoDto(List<PresupuestoSedeDto> sedes, long ingresos, long gastos, long saldo) {}
    /** tipo: INGRESO (suma) o GASTO (resta). monto en pesos colombianos, sin decimales. */
    public record MovimientoRequest(String tipo, String concepto, String categoria, Long monto, LocalDate fecha, String placa) {}
    public record AnularRequest(String motivo) {}

    // ---- Imágenes ----
    /** url: ruta lista para guardar en fotoUrl (ej. /api/imagenes/3f9c…). */
    public record ImagenSubidaDto(String id, String url, long tamano, String tipo) {}

    // ---- Registro de estudiantes con código al correo ----
    public record RegistroRequest(String nombre, String correo, String documento, String codigoEstudiantil, String programa,
                                  String password, String sede) {}
    /** correoEnviado = false cuando el SMTP no está configurado: el código quedó en la consola del backend (modo local). */
    public record RegistroIniciadoDto(String correo, int minutosVigencia, int reenviarEnSegundos, boolean correoEnviado) {}
    public record VerificarRegistroRequest(String correo, String codigo) {}
    public record ReenviarCodigoRequest(String correo) {}

    // ---- Novedades de equipos (aviso por agente de IA en n8n) ----
    /** tipo: AGOTADO, DANADO, PERDIDO, FALLA u OTRO. mantenimiento = retirar el equipo del préstamo mientras se revisa. */
    public record NovedadRequest(String tipo, String descripcion, Boolean mantenimiento) {}
    public record NovedadDto(Long id, String sede, String placa, String equipoNombre, String laboratorio, String tipo, String tipoTexto,
                             String descripcion, String reportadoPor, LocalDateTime creadoEn, boolean enMantenimiento, String prioridad,
                             String asunto, String resumenIa, String accionSugerida, String notificacion, String notificacionDetalle,
                             String destinatario, LocalDateTime notificadoEn) {}
}
