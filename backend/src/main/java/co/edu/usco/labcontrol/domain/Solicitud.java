package co.edu.usco.labcontrol.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Solicitud o requerimiento de un laboratorio (compra, insumos, mantenimiento, servicio…).
 * BORRADOR → EN_REVISION (la envió un monitor; la valida el coordinador) → ENVIADA (al Administrador) → DEVUELTA (para
 * ajuste) → APROBADA o RECHAZADA → CERRADA. Se puede CANCELAR mientras está en borrador, revisión o devuelta.
 */
@Entity
@Table(name = "solicitudes")
public class Solicitud {
    public enum Estado {
        BORRADOR("Borrador"), EN_REVISION("En revisión del coordinador"), ENVIADA("Enviada al Administrador"),
        DEVUELTA("Devuelta para ajuste"), APROBADA("Aprobada"), RECHAZADA("Rechazada"), CERRADA("Cerrada"), CANCELADA("Cancelada");
        private final String texto;
        Estado(String texto) { this.texto = texto; }
        public String texto() { return texto; }
        /** ¿Se puede editar el contenido? */
        public boolean editable() { return this == BORRADOR || this == EN_REVISION || this == DEVUELTA; }
    }
    public enum EstadoContractual {
        SIN_INICIAR("Sin iniciar"), EN_PROCESO("En proceso de contratación"), CONTRATADO("Contratado"),
        EN_EJECUCION("En ejecución"), LIQUIDADO("Liquidado");
        private final String texto;
        EstadoContractual(String texto) { this.texto = texto; }
        public String texto() { return texto; }
    }

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String codigo;
    @ManyToOne
    private Sede sede;
    @ManyToOne
    private Laboratorio laboratorio;
    private String tipo;
    private String titulo;
    private String justificacion;
    private String especificaciones;
    private BigDecimal cantidad;
    private String unidadMedida;
    private String prioridadPropuesta;
    private Long valorEstimado;
    @Enumerated(EnumType.STRING)
    private Estado estado = Estado.BORRADOR;
    @ManyToOne
    private Usuario creadoPor;
    private LocalDateTime creadoEn;
    private LocalDateTime actualizadoEn;
    private LocalDateTime enviadoEn;
    private String observacionDevolucion;
    private String prioridadAsignada;
    private Integer ordenPrioridad;
    private String decisionMotivo;
    private String decididoPor;
    private LocalDateTime decididoEn;
    private String cierreNota;
    private LocalDateTime cerradoEn;
    // ---- Presupuesto ----
    private String rubro;
    private String fuenteFinanciacion;
    private String cdpNumero;
    private LocalDate cdpFecha;
    private Long valorAprobado;
    // ---- Contratación ----
    private String modalidadContratacion;
    private String contratoNumero;
    private String contratista;
    private Long valorContratado;
    private LocalDate fechaContrato;
    @Enumerated(EnumType.STRING)
    private EstadoContractual estadoContractual = EstadoContractual.SIN_INICIAR;
    private String observacionContractual;

    protected Solicitud() {}

    public Solicitud(String codigo, Sede sede, Laboratorio laboratorio, Usuario creadoPor, LocalDateTime creadoEn) {
        this.codigo = codigo; this.sede = sede; this.laboratorio = laboratorio; this.creadoPor = creadoPor; this.creadoEn = creadoEn;
    }

    public void contenido(Laboratorio laboratorio, String tipo, String titulo, String justificacion, String especificaciones,
                          BigDecimal cantidad, String unidadMedida, String prioridadPropuesta, Long valorEstimado, LocalDateTime cuando) {
        this.laboratorio = laboratorio; this.tipo = tipo; this.titulo = titulo; this.justificacion = justificacion;
        this.especificaciones = especificaciones; this.cantidad = cantidad; this.unidadMedida = unidadMedida;
        this.prioridadPropuesta = prioridadPropuesta; this.valorEstimado = valorEstimado; this.actualizadoEn = cuando;
    }

    public void pasarA(Estado nuevo, LocalDateTime cuando) {
        this.estado = nuevo; this.actualizadoEn = cuando;
        if (nuevo == Estado.ENVIADA) this.enviadoEn = cuando;
        if (nuevo == Estado.CERRADA) this.cerradoEn = cuando;
    }

    public void devolver(String observacion, LocalDateTime cuando) { this.observacionDevolucion = observacion; pasarA(Estado.DEVUELTA, cuando); }
    public void priorizar(String prioridad, Integer orden, LocalDateTime cuando) { this.prioridadAsignada = prioridad; this.ordenPrioridad = orden; this.actualizadoEn = cuando; }
    public void decidir(Estado decision, String motivo, String quien, LocalDateTime cuando) {
        this.decisionMotivo = motivo; this.decididoPor = quien; this.decididoEn = cuando; pasarA(decision, cuando);
    }
    public void cerrar(String nota, LocalDateTime cuando) { this.cierreNota = nota; pasarA(Estado.CERRADA, cuando); }

    public void presupuesto(String rubro, String fuente, String cdpNumero, LocalDate cdpFecha, Long valorAprobado, LocalDateTime cuando) {
        this.rubro = rubro; this.fuenteFinanciacion = fuente; this.cdpNumero = cdpNumero; this.cdpFecha = cdpFecha;
        this.valorAprobado = valorAprobado; this.actualizadoEn = cuando;
    }

    public void contratacion(String modalidad, String numero, String contratista, Long valor, LocalDate fecha,
                             EstadoContractual estado, String observacion, LocalDateTime cuando) {
        this.modalidadContratacion = modalidad; this.contratoNumero = numero; this.contratista = contratista;
        this.valorContratado = valor; this.fechaContrato = fecha; this.estadoContractual = estado;
        this.observacionContractual = observacion; this.actualizadoEn = cuando;
    }

    public Long getId() { return id; }
    public String getCodigo() { return codigo; }
    public Sede getSede() { return sede; }
    public Laboratorio getLaboratorio() { return laboratorio; }
    public String getTipo() { return tipo; }
    public String getTitulo() { return titulo; }
    public String getJustificacion() { return justificacion; }
    public String getEspecificaciones() { return especificaciones; }
    public BigDecimal getCantidad() { return cantidad; }
    public String getUnidadMedida() { return unidadMedida; }
    public String getPrioridadPropuesta() { return prioridadPropuesta; }
    public Long getValorEstimado() { return valorEstimado; }
    public Estado getEstado() { return estado; }
    public Usuario getCreadoPor() { return creadoPor; }
    public LocalDateTime getCreadoEn() { return creadoEn; }
    public LocalDateTime getActualizadoEn() { return actualizadoEn; }
    public LocalDateTime getEnviadoEn() { return enviadoEn; }
    public String getObservacionDevolucion() { return observacionDevolucion; }
    public String getPrioridadAsignada() { return prioridadAsignada; }
    public Integer getOrdenPrioridad() { return ordenPrioridad; }
    public String getDecisionMotivo() { return decisionMotivo; }
    public String getDecididoPor() { return decididoPor; }
    public LocalDateTime getDecididoEn() { return decididoEn; }
    public String getCierreNota() { return cierreNota; }
    public LocalDateTime getCerradoEn() { return cerradoEn; }
    public String getRubro() { return rubro; }
    public String getFuenteFinanciacion() { return fuenteFinanciacion; }
    public String getCdpNumero() { return cdpNumero; }
    public LocalDate getCdpFecha() { return cdpFecha; }
    public Long getValorAprobado() { return valorAprobado; }
    public String getModalidadContratacion() { return modalidadContratacion; }
    public String getContratoNumero() { return contratoNumero; }
    public String getContratista() { return contratista; }
    public Long getValorContratado() { return valorContratado; }
    public LocalDate getFechaContrato() { return fechaContrato; }
    public EstadoContractual getEstadoContractual() { return estadoContractual == null ? EstadoContractual.SIN_INICIAR : estadoContractual; }
    public String getObservacionContractual() { return observacionContractual; }
}
