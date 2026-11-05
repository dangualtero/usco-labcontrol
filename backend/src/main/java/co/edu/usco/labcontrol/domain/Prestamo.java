package co.edu.usco.labcontrol.domain;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "prestamos", indexes = {
        @Index(name = "idx_prestamo_equipo_estado", columnList = "equipo_id,estado"),
        @Index(name = "idx_prestamo_solicitante", columnList = "solicitante_id,estado")
})
public class Prestamo {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Código de reserva / préstamo visible al usuario (ej. RES-48213). */
    @Column(nullable = false, unique = true, length = 20)
    private String codigo;

    @ManyToOne(fetch = FetchType.EAGER, optional = false) @JoinColumn(name = "equipo_id")
    private Equipo equipo;

    @ManyToOne(fetch = FetchType.EAGER, optional = false) @JoinColumn(name = "solicitante_id")
    private Usuario solicitante;

    @ManyToOne(fetch = FetchType.EAGER) @JoinColumn(name = "auxiliar_salida_id")
    private Usuario auxiliarSalida;

    @ManyToOne(fetch = FetchType.EAGER) @JoinColumn(name = "auxiliar_entrada_id")
    private Usuario auxiliarEntrada;

    /** Auxiliar que tiene la custodia vigente (cambia con el Cierre de Turno, PF-12). */
    @ManyToOne(fetch = FetchType.EAGER) @JoinColumn(name = "custodio_id")
    private Usuario custodio;

    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20)
    private EstadoPrestamo estado;

    @Column(length = 120) private String asignatura;
    /** Docente de la asignatura (motivo académico). Opcional. */
    @Column(length = 120) private String docente;

    private LocalDateTime franjaInicio;
    private LocalDateTime franjaFin;
    @Column(nullable = false) private LocalDateTime fechaSolicitud;
    private LocalDateTime fechaSalida;
    private LocalDateTime fechaLimite;
    private LocalDateTime fechaEntrada;

    /** BUEN_ESTADO | CON_NOVEDAD */
    @Column(length = 20) private String estadoEntrada;
    @Column(length = 600) private String observaciones;
    @Column(length = 20) private String incidencia;

    protected Prestamo() {}

    public Prestamo(String codigo, Equipo equipo, Usuario solicitante, EstadoPrestamo estado, String asignatura,
                    LocalDateTime franjaInicio, LocalDateTime franjaFin, LocalDateTime fechaSolicitud) {
        this.codigo = codigo; this.equipo = equipo; this.solicitante = solicitante; this.estado = estado;
        this.asignatura = asignatura; this.franjaInicio = franjaInicio; this.franjaFin = franjaFin;
        this.fechaSolicitud = fechaSolicitud;
    }

    public boolean estaVencido(LocalDateTime ahora) {
        return estado == EstadoPrestamo.ACTIVO && fechaLimite != null && fechaLimite.isBefore(ahora);
    }

    public Long getId() { return id; }
    public String getCodigo() { return codigo; }
    public Equipo getEquipo() { return equipo; }
    public Usuario getSolicitante() { return solicitante; }
    public Usuario getAuxiliarSalida() { return auxiliarSalida; }
    public void setAuxiliarSalida(Usuario u) { this.auxiliarSalida = u; }
    public Usuario getAuxiliarEntrada() { return auxiliarEntrada; }
    public void setAuxiliarEntrada(Usuario u) { this.auxiliarEntrada = u; }
    public Usuario getCustodio() { return custodio; }
    public void setCustodio(Usuario u) { this.custodio = u; }
    public EstadoPrestamo getEstado() { return estado; }
    public void setEstado(EstadoPrestamo estado) { this.estado = estado; }
    public String getAsignatura() { return asignatura; }
    public String getDocente() { return docente; }
    public void setDocente(String docente) { this.docente = docente; }
    public LocalDateTime getFranjaInicio() { return franjaInicio; }
    public LocalDateTime getFranjaFin() { return franjaFin; }
    public LocalDateTime getFechaSolicitud() { return fechaSolicitud; }
    public LocalDateTime getFechaSalida() { return fechaSalida; }
    public void setFechaSalida(LocalDateTime v) { this.fechaSalida = v; }
    public LocalDateTime getFechaLimite() { return fechaLimite; }
    public void setFechaLimite(LocalDateTime v) { this.fechaLimite = v; }
    public LocalDateTime getFechaEntrada() { return fechaEntrada; }
    public void setFechaEntrada(LocalDateTime v) { this.fechaEntrada = v; }
    public String getEstadoEntrada() { return estadoEntrada; }
    public void setEstadoEntrada(String v) { this.estadoEntrada = v; }
    public String getObservaciones() { return observaciones; }
    public void setObservaciones(String v) { this.observaciones = v; }
    public String getIncidencia() { return incidencia; }
    public void setIncidencia(String v) { this.incidencia = v; }
}
