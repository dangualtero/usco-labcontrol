package co.edu.usco.labcontrol.domain;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import org.hibernate.annotations.Immutable;

/**
 * Historial inmutable (RF-03 / PF-14). Solo se inserta: @Immutable impide UPDATE desde JPA y el
 * repositorio no expone borrado. En MySQL se recomienda además revocar UPDATE/DELETE sobre la tabla.
 */
@Entity
@Immutable
@Table(name = "historial_eventos")
public class HistorialEvento {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private LocalDateTime fecha;
    /** RESERVA, CANCELACION, RECHAZO, SALIDA, ENTRADA, INCIDENCIA, CUSTODIA, ALTA, ESTADO, BAJA */
    private String tipo;
    private String placa;
    private String equipoNombre;
    private String laboratorio;
    private String prestamoCodigo;
    private String usuarioNombre;
    private String auxiliarNombre;
    private String detalle;
    /** Slug de la sede del equipo. Los eventos anteriores a las sedes quedan en null y pertenecen a la sede principal. */
    private String sede;

    protected HistorialEvento() {}

    public HistorialEvento(LocalDateTime fecha, String tipo, Equipo e, String prestamoCodigo,
                           String usuarioNombre, String auxiliarNombre, String detalle) {
        this.fecha = fecha; this.tipo = tipo;
        if (e != null) {
            this.placa = e.getPlaca(); this.equipoNombre = e.getNombre(); this.laboratorio = e.getLaboratorio();
            this.sede = e.getSede() == null ? null : e.getSede().getSlug();
        }
        this.prestamoCodigo = prestamoCodigo; this.usuarioNombre = usuarioNombre;
        this.auxiliarNombre = auxiliarNombre; this.detalle = detalle;
    }

    public Long getId() { return id; }
    public LocalDateTime getFecha() { return fecha; }
    public String getTipo() { return tipo; }
    public String getPlaca() { return placa; }
    public String getEquipoNombre() { return equipoNombre; }
    public String getLaboratorio() { return laboratorio; }
    public String getPrestamoCodigo() { return prestamoCodigo; }
    public String getUsuarioNombre() { return usuarioNombre; }
    public String getAuxiliarNombre() { return auxiliarNombre; }
    public String getDetalle() { return detalle; }
    public String getSede() { return sede; }
}
