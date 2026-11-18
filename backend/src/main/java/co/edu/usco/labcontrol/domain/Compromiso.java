package co.edu.usco.labcontrol.domain;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** Compromiso de un laboratorio (o de toda la sede) con responsable y fecha límite. */
@Entity
@Table(name = "compromisos")
public class Compromiso {
    public enum Estado {
        PENDIENTE("Pendiente"), EN_CURSO("En curso"), CUMPLIDO("Cumplido"), CANCELADO("Cancelado");
        private final String texto;
        Estado(String texto) { this.texto = texto; }
        public String texto() { return texto; }
    }

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne
    private Sede sede;
    @ManyToOne
    private Laboratorio laboratorio;
    private String titulo;
    private String descripcion;
    @ManyToOne
    private Usuario responsable;
    private LocalDate fechaLimite;
    @Enumerated(EnumType.STRING)
    private Estado estado = Estado.PENDIENTE;
    private String creadoPor;
    private LocalDateTime creadoEn;
    private LocalDateTime actualizadoEn;

    protected Compromiso() {}

    public Compromiso(Sede sede, Laboratorio laboratorio, String titulo, String descripcion, Usuario responsable, LocalDate fechaLimite,
                      String creadoPor, LocalDateTime creadoEn) {
        this.sede = sede; this.laboratorio = laboratorio; this.titulo = titulo; this.descripcion = descripcion;
        this.responsable = responsable; this.fechaLimite = fechaLimite; this.creadoPor = creadoPor; this.creadoEn = creadoEn;
    }

    public void actualizar(String titulo, String descripcion, Usuario responsable, LocalDate fechaLimite, LocalDateTime cuando) {
        this.titulo = titulo; this.descripcion = descripcion; this.responsable = responsable; this.fechaLimite = fechaLimite;
        this.actualizadoEn = cuando;
    }

    public void cambiarEstado(Estado e, LocalDateTime cuando) { this.estado = e; this.actualizadoEn = cuando; }

    public boolean vencido(LocalDate hoy) {
        return (estado == Estado.PENDIENTE || estado == Estado.EN_CURSO) && fechaLimite.isBefore(hoy);
    }

    public Long getId() { return id; }
    public Sede getSede() { return sede; }
    public Laboratorio getLaboratorio() { return laboratorio; }
    public String getTitulo() { return titulo; }
    public String getDescripcion() { return descripcion; }
    public Usuario getResponsable() { return responsable; }
    public LocalDate getFechaLimite() { return fechaLimite; }
    public Estado getEstado() { return estado; }
    public String getCreadoPor() { return creadoPor; }
    public LocalDateTime getCreadoEn() { return creadoEn; }
    public LocalDateTime getActualizadoEn() { return actualizadoEn; }
}
