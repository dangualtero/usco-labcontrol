package co.edu.usco.labcontrol.domain;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * Novedad reportada por un auxiliar sobre un equipo (agotado, dañado, perdido, falla…). Al guardarse se dispara un aviso
 * al correo institucional configurado: lo redacta y prioriza un agente de IA en n8n, o, si n8n no responde, se envía
 * un correo con plantilla. El resultado del aviso queda registrado aquí.
 */
@Entity
@Table(name = "reportes_novedad")
public class ReporteNovedad {
    public enum Tipo {
        AGOTADO("Agotado / sin existencias"), DANADO("Dañado"), PERDIDO("Perdido o extraviado"),
        FALLA("Falla de funcionamiento"), OTRO("Otra novedad");
        private final String texto;
        Tipo(String texto) { this.texto = texto; }
        public String texto() { return texto; }
    }

    /**
     * PENDIENTE → ENVIADA_IA (n8n) | ENVIADA_PLANTILLA (respaldo SMTP) | SIN_CONFIGURAR | FALLIDA.
     * Solo la prioridad ALTA se envía al instante; MEDIA y BAJA quedan en PENDIENTE_RESUMEN y salen en el
     * resumen diario (ENVIADA_RESUMEN).
     */
    public enum Notificacion { PENDIENTE, ENVIADA_IA, ENVIADA_PLANTILLA, SIN_CONFIGURAR, FALLIDA, PENDIENTE_RESUMEN, ENVIADA_RESUMEN }

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
    /** ¿El equipo se pasó a mantenimiento al reportar (para que no se preste)? */
    private boolean enMantenimiento;

    // ---- Resultado del agente de IA / del aviso ----
    private String prioridad;
    private String asuntoCorreo;
    private String resumenIa;
    private String accionSugerida;
    @Enumerated(EnumType.STRING) private Notificacion notificacion = Notificacion.PENDIENTE;
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
}
