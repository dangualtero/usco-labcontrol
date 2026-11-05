package co.edu.usco.labcontrol.domain;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "prestamos")
public class Prestamo {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Código de reserva / préstamo visible al usuario (ej. RES-48213). */
    private String codigo;

    @ManyToOne
    private Equipo equipo;

    @ManyToOne
    private Usuario solicitante;

    @ManyToOne
    private Usuario auxiliarSalida;

    @ManyToOne
    private Usuario auxiliarEntrada;

    /** Auxiliar que tiene la custodia vigente (cambia con el Cierre de Turno, PF-12). */
    @ManyToOne
    private Usuario custodio;

    @Enumerated(EnumType.STRING)
    private EstadoPrestamo estado;

    private String asignatura;
    /** Docente de la asignatura (motivo académico). Opcional. */
    private String docente;

    private LocalDateTime franjaInicio;
    private LocalDateTime franjaFin;
    private LocalDateTime fechaSolicitud;
    private LocalDateTime fechaSalida;
    private LocalDateTime fechaLimite;
    private LocalDateTime fechaEntrada;

    /** BUEN_ESTADO | CON_NOVEDAD */
    private String estadoEntrada;
    private String observaciones;
    private String incidencia;

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
