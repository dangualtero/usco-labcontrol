package co.edu.usco.labcontrol.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

/**
 * Novedad (registro preliminar) sobre un equipo o insumo: falla, daño, pérdida, conteo de existencias o consumo.
 * La que reporta un Monitor queda PENDIENTE hasta que el Coordinador de su laboratorio la valida; al validarla se aplica
 * (estado del equipo, existencias) y se dispara el aviso por correo (agente de IA en n8n o plantilla).
 */
@Entity
@Table(name = "reportes_novedad")
public class ReporteNovedad {
    public enum Tipo {
        AGOTADO("Agotado / sin existencias"), DANADO("Dañado"), PERDIDO("Perdido o extraviado"),
        FALLA("Falla de funcionamiento"), OTRO("Otra novedad"), EXISTENCIAS("Conteo de existencias"), CONSUMO("Consumo de insumo");
        private final String texto;
        Tipo(String texto) { this.texto = texto; }
        public String texto() { return texto; }
    }

    /**
     * PENDIENTE → ENVIADA_IA (n8n) | ENVIADA_PLANTILLA (respaldo SMTP) | SIN_CONFIGURAR | FALLIDA.
     * Solo la prioridad ALTA se envía al instante; MEDIA y BAJA quedan en PENDIENTE_RESUMEN y salen en el
     * resumen diario (ENVIADA_RESUMEN).
     */
    public enum Notificacion { EN_ESPERA, PENDIENTE, ENVIADA_IA, ENVIADA_PLANTILLA, SIN_CONFIGURAR, FALLIDA, PENDIENTE_RESUMEN, ENVIADA_RESUMEN, NO_APLICA }

    /** Validación del Coordinador (las que reporta el coordinador o el administrador quedan validadas de una vez). */
    public enum Validacion {
        PENDIENTE("Pendiente de validación"), VALIDADA("Validada"), RECHAZADA("Rechazada");
        private final String texto;
        Validacion(String texto) { this.texto = texto; }
        public String texto() { return texto; }
    }

    /** Seguimiento del reporte. */
    public enum Estado {
        ABIERTO("Abierto"), EN_PROCESO("En proceso"), RESUELTO("Resuelto");
        private final String texto;
        Estado(String texto) { this.texto = texto; }
        public String texto() { return texto; }
    }

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    private Sede sede;
    @ManyToOne
    private Equipo equipo;

    @Enumerated(EnumType.STRING) private Tipo tipo;
    private String descripcion;
    private String reportadoPor;
    private String reportadoPorCorreo;
    private LocalDateTime creadoEn;
    /** ¿Se pide pasar el equipo a mantenimiento al validar? */
    private boolean enMantenimiento;
    /** Conteo (EXISTENCIAS) o cantidad usada (CONSUMO), en {@link #unidad}. */
    private BigDecimal cantidad;
    @Enumerated(EnumType.STRING) private Unidad unidad;
    @Enumerated(EnumType.STRING) private Validacion validacion = Validacion.PENDIENTE;
    private String validadoPor;
    private LocalDateTime validadoEn;
    private String observacionValidacion;
    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(name = "novedad_archivos", joinColumns = @JoinColumn(name = "novedad_id"), inverseJoinColumns = @JoinColumn(name = "archivo_id"))
    private Set<ArchivoInfo> archivos = new HashSet<>();

    // ---- Resultado del agente de IA / del aviso ----
    private String prioridad;
    private String asuntoCorreo;
    private String resumenIa;
    private String accionSugerida;
    @Enumerated(EnumType.STRING) private Notificacion notificacion = Notificacion.EN_ESPERA;
    private String notificacionDetalle;
    private String destinatario;
    private LocalDateTime notificadoEn;
    private int intentosNotificacion;

    // ---- Seguimiento ----
    @Enumerated(EnumType.STRING) private Estado estado = Estado.ABIERTO;
    private LocalDateTime actualizadoEn;

    protected ReporteNovedad() {}

    public ReporteNovedad(Sede sede, Equipo equipo, Tipo tipo, String descripcion, String reportadoPor, String reportadoPorCorreo,
                          LocalDateTime creadoEn, boolean enMantenimiento) {
        this.sede = sede; this.equipo = equipo; this.tipo = tipo; this.descripcion = descripcion; this.reportadoPor = reportadoPor;
        this.reportadoPorCorreo = reportadoPorCorreo; this.creadoEn = creadoEn; this.enMantenimiento = enMantenimiento;
    }

    public void cantidad(BigDecimal cantidad, Unidad unidad) { this.cantidad = cantidad; this.unidad = unidad; }

    public void validar(Validacion v, String quien, String observacion, LocalDateTime cuando) {
        this.validacion = v; this.validadoPor = quien; this.observacionValidacion = observacion; this.validadoEn = cuando;
        if (v == Validacion.RECHAZADA) { this.notificacion = Notificacion.NO_APLICA; this.estado = Estado.RESUELTO; this.actualizadoEn = cuando; }
    }

    /** Lista para avisar (se dispara al validar). */
    public void porNotificar() { this.notificacion = Notificacion.PENDIENTE; }

    /** Novedades que no generan correo (conteos de existencias y consumos): se aplican y quedan resueltas. */
    public void sinAviso(String detalle, LocalDateTime cuando) {
        this.notificacion = Notificacion.NO_APLICA; this.notificacionDetalle = detalle; this.estado = Estado.RESUELTO; this.actualizadoEn = cuando;
    }

    public void resultado(Notificacion n, String prioridad, String asunto, String resumen, String accion, String detalle,
                          String destinatario, LocalDateTime cuando) {
        this.notificacion = n; this.prioridad = prioridad; this.asuntoCorreo = corta(asunto, 200); this.resumenIa = corta(resumen, 1000);
        this.accionSugerida = corta(accion, 600); this.notificacionDetalle = corta(detalle, 300); this.destinatario = destinatario;
        this.notificadoEn = cuando; this.intentosNotificacion++;
    }

    /** MEDIA o BAJA: no se envía ahora; va en el resumen diario. */
    public void paraResumen(String prioridad, String asunto, String resumen, String accion, String detalle, String destinatario) {
        this.notificacion = Notificacion.PENDIENTE_RESUMEN; this.prioridad = prioridad; this.asuntoCorreo = corta(asunto, 200);
        this.resumenIa = corta(resumen, 1000); this.accionSugerida = corta(accion, 600); this.notificacionDetalle = corta(detalle, 300);
        this.destinatario = destinatario; this.intentosNotificacion++;
    }

    public void incluidoEnResumen(String destinatario, LocalDateTime cuando) {
        this.notificacion = Notificacion.ENVIADA_RESUMEN; this.destinatario = destinatario; this.notificadoEn = cuando;
        this.notificacionDetalle = "Incluido en el resumen diario";
    }

    public void cambiarEstado(Estado nuevo, LocalDateTime cuando) { this.estado = nuevo; this.actualizadoEn = cuando; }

    public void reintentando() { this.notificacion = Notificacion.PENDIENTE; this.notificacionDetalle = "Reenvío solicitado"; }

    private static String corta(String s, int n) { return s == null ? null : s.length() <= n ? s : s.substring(0, n - 1) + "…"; }

    public Long getId() { return id; }
    public Sede getSede() { return sede; }
    public Equipo getEquipo() { return equipo; }
    public Tipo getTipo() { return tipo; }
    public String getDescripcion() { return descripcion; }
    public String getReportadoPor() { return reportadoPor; }
    public String getReportadoPorCorreo() { return reportadoPorCorreo; }
    public LocalDateTime getCreadoEn() { return creadoEn; }
    public boolean isEnMantenimiento() { return enMantenimiento; }
    public String getPrioridad() { return prioridad; }
    public String getAsuntoCorreo() { return asuntoCorreo; }
    public String getResumenIa() { return resumenIa; }
    public String getAccionSugerida() { return accionSugerida; }
    public Notificacion getNotificacion() { return notificacion; }
    public String getNotificacionDetalle() { return notificacionDetalle; }
    public String getDestinatario() { return destinatario; }
    public LocalDateTime getNotificadoEn() { return notificadoEn; }
    public int getIntentosNotificacion() { return intentosNotificacion; }
    public Estado getEstado() { return estado == null ? Estado.ABIERTO : estado; }
    public LocalDateTime getActualizadoEn() { return actualizadoEn; }
    public BigDecimal getCantidad() { return cantidad; }
    public Unidad getUnidad() { return unidad; }
    public Validacion getValidacion() { return validacion == null ? Validacion.VALIDADA : validacion; }
    public String getValidadoPor() { return validadoPor; }
    public LocalDateTime getValidadoEn() { return validadoEn; }
    public String getObservacionValidacion() { return observacionValidacion; }
    public Set<ArchivoInfo> getArchivos() { return archivos; }
}
