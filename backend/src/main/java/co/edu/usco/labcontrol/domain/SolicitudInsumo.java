package co.edu.usco.labcontrol.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Pedido de un insumo: el estudiante o docente indica cuánto necesita (en cualquier unidad compatible) y el auxiliar
 * lo aprueba —se descuenta del inventario— o lo rechaza.
 */
@Entity
@Table(name = "solicitudes_insumo")
public class SolicitudInsumo {
    public enum Estado { PENDIENTE, APROBADA, RECHAZADA, CANCELADA }

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String codigo;
    @ManyToOne
    private Sede sede;
    /** El insumo (en la tabla equipos, con tipo INSUMO). */
    @ManyToOne
    private Equipo equipo;
    @ManyToOne
    private Usuario solicitante;
    private BigDecimal cantidad;
    @Enumerated(EnumType.STRING)
    private Unidad unidad;
    /** Lo pedido convertido a la unidad en que se guarda el insumo. */
    private BigDecimal cantidadEquivalente;
    private String motivo;
    private String asignatura;
    @Enumerated(EnumType.STRING)
    private Estado estado = Estado.PENDIENTE;
    private LocalDateTime creadoEn;
    private LocalDateTime resueltoEn;
    private String resueltoPor;
    private String respuesta;

    protected SolicitudInsumo() {}

    public SolicitudInsumo(String codigo, Sede sede, Equipo equipo, Usuario solicitante, BigDecimal cantidad, Unidad unidad,
                           BigDecimal cantidadEquivalente, String motivo, String asignatura, LocalDateTime creadoEn) {
        this.codigo = codigo; this.sede = sede; this.equipo = equipo; this.solicitante = solicitante; this.cantidad = cantidad;
        this.unidad = unidad; this.cantidadEquivalente = cantidadEquivalente; this.motivo = motivo; this.asignatura = asignatura;
        this.creadoEn = creadoEn;
    }

    public void resolver(Estado estado, String quien, String respuesta, LocalDateTime cuando) {
        this.estado = estado; this.resueltoPor = quien; this.respuesta = respuesta; this.resueltoEn = cuando;
    }

    public Long getId() { return id; }
    public String getCodigo() { return codigo; }
    public Sede getSede() { return sede; }
    public Equipo getEquipo() { return equipo; }
    public Usuario getSolicitante() { return solicitante; }
    public BigDecimal getCantidad() { return cantidad; }
    public Unidad getUnidad() { return unidad; }
    public BigDecimal getCantidadEquivalente() { return cantidadEquivalente; }
    public String getMotivo() { return motivo; }
    public String getAsignatura() { return asignatura; }
    public Estado getEstado() { return estado; }
    public LocalDateTime getCreadoEn() { return creadoEn; }
    public LocalDateTime getResueltoEn() { return resueltoEn; }
    public String getResueltoPor() { return resueltoPor; }
    public String getRespuesta() { return respuesta; }
}
