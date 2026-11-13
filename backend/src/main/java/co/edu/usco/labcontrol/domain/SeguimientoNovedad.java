package co.edu.usco.labcontrol.domain;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import org.hibernate.annotations.Immutable;

/** Entrada del seguimiento de un reporte: cambio de estado y/o comentario. Solo se agregan; no se editan. */
@Entity
@Immutable
@Table(name = "seguimiento_novedad")
public class SeguimientoNovedad {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne
    private ReporteNovedad reporte;
    private LocalDateTime fecha;
    private String autor;
    @Enumerated(EnumType.STRING)
    private ReporteNovedad.Estado estadoAnterior;
    @Enumerated(EnumType.STRING)
    private ReporteNovedad.Estado estadoNuevo;
    private String comentario;

    protected SeguimientoNovedad() {}

    public SeguimientoNovedad(ReporteNovedad reporte, LocalDateTime fecha, String autor, ReporteNovedad.Estado anterior,
                              ReporteNovedad.Estado nuevo, String comentario) {
        this.reporte = reporte; this.fecha = fecha; this.autor = autor; this.estadoAnterior = anterior;
        this.estadoNuevo = nuevo; this.comentario = comentario;
    }

    public Long getId() { return id; }
    public ReporteNovedad getReporte() { return reporte; }
    public LocalDateTime getFecha() { return fecha; }
    public String getAutor() { return autor; }
    public ReporteNovedad.Estado getEstadoAnterior() { return estadoAnterior; }
    public ReporteNovedad.Estado getEstadoNuevo() { return estadoNuevo; }
    public String getComentario() { return comentario; }
}
