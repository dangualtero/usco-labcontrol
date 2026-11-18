package co.edu.usco.labcontrol.domain;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import org.hibernate.annotations.Immutable;

/** Trazabilidad de una solicitud: cada acción queda registrada y no se edita. */
@Entity
@Immutable
@Table(name = "solicitud_eventos")
public class SolicitudEvento {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne
    private Solicitud solicitud;
    private LocalDateTime fecha;
    private String autor;
    /** CREADA, EDITADA, ENVIADA_REVISION, ENVIADA, DEVUELTA, PRIORIZADA, APROBADA, RECHAZADA, CERRADA, CANCELADA, PRESUPUESTO, CONTRATACION, ADJUNTO, COMENTARIO */
    private String accion;
    @Enumerated(EnumType.STRING)
    private Solicitud.Estado estadoAnterior;
    @Enumerated(EnumType.STRING)
    private Solicitud.Estado estadoNuevo;
    private String comentario;

    protected SolicitudEvento() {}

    public SolicitudEvento(Solicitud solicitud, LocalDateTime fecha, String autor, String accion, Solicitud.Estado anterior,
                           Solicitud.Estado nuevo, String comentario) {
        this.solicitud = solicitud; this.fecha = fecha; this.autor = autor; this.accion = accion; this.estadoAnterior = anterior;
        this.estadoNuevo = nuevo; this.comentario = comentario;
    }

    public Long getId() { return id; }
    public Solicitud getSolicitud() { return solicitud; }
    public LocalDateTime getFecha() { return fecha; }
    public String getAutor() { return autor; }
    public String getAccion() { return accion; }
    public Solicitud.Estado getEstadoAnterior() { return estadoAnterior; }
    public Solicitud.Estado getEstadoNuevo() { return estadoNuevo; }
    public String getComentario() { return comentario; }
}
