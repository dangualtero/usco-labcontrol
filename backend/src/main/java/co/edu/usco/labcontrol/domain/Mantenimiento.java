package co.edu.usco.labcontrol.domain;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** Mantenimiento de un equipo: requerimiento → programación → ejecución → resultado. */
@Entity
@Table(name = "mantenimientos")
public class Mantenimiento {
    public enum Tipo {
        PREVENTIVO("Preventivo"), CORRECTIVO("Correctivo"), CALIBRACION("Calibración"), VERIFICACION("Verificación");
        private final String texto;
        Tipo(String texto) { this.texto = texto; }
        public String texto() { return texto; }
    }
    public enum Estado {
        REQUERIDO("Requerido"), PROGRAMADO("Programado"), EN_EJECUCION("En ejecución"), REALIZADO("Realizado"), CANCELADO("Cancelado");
        private final String texto;
        Estado(String texto) { this.texto = texto; }
        public String texto() { return texto; }
        public boolean abierto() { return this == REQUERIDO || this == PROGRAMADO || this == EN_EJECUCION; }
    }

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String codigo;
    @ManyToOne
    private Sede sede;
    @ManyToOne
    private Equipo equipo;
    @Enumerated(EnumType.STRING)
    private Tipo tipo;
    @Enumerated(EnumType.STRING)
    private Estado estado = Estado.REQUERIDO;
    private String descripcion;
    private LocalDate fechaProgramada;
    private LocalDate fechaRealizada;
    private String responsable;
    private Long costo;
    private String resultado;
    @ManyToOne
    private ReporteNovedad novedad;
    private String registradoPor;
    private LocalDateTime creadoEn;
    private LocalDateTime actualizadoEn;

    protected Mantenimiento() {}

    public Mantenimiento(String codigo, Sede sede, Equipo equipo, Tipo tipo, String descripcion, LocalDate fechaProgramada,
                         ReporteNovedad novedad, String registradoPor, LocalDateTime creadoEn) {
        this.codigo = codigo; this.sede = sede; this.equipo = equipo; this.tipo = tipo; this.descripcion = descripcion;
        this.fechaProgramada = fechaProgramada; this.novedad = novedad; this.registradoPor = registradoPor; this.creadoEn = creadoEn;
        if (fechaProgramada != null) this.estado = Estado.PROGRAMADO;
    }

    public void actualizar(Tipo tipo, String descripcion, LocalDate fechaProgramada, String responsable, Long costo,
                           LocalDateTime cuando) {
        this.tipo = tipo; this.descripcion = descripcion; this.fechaProgramada = fechaProgramada; this.responsable = responsable;
        this.costo = costo; this.actualizadoEn = cuando;
    }

    public void cambiarEstado(Estado nuevo, LocalDate fechaRealizada, String resultado, LocalDateTime cuando) {
        this.estado = nuevo; this.actualizadoEn = cuando;
        if (fechaRealizada != null) this.fechaRealizada = fechaRealizada;
        if (resultado != null) this.resultado = resultado;
    }

    public Long getId() { return id; }
    public String getCodigo() { return codigo; }
    public Sede getSede() { return sede; }
    public Equipo getEquipo() { return equipo; }
    public Tipo getTipo() { return tipo; }
    public Estado getEstado() { return estado; }
    public String getDescripcion() { return descripcion; }
    public LocalDate getFechaProgramada() { return fechaProgramada; }
    public LocalDate getFechaRealizada() { return fechaRealizada; }
    public String getResponsable() { return responsable; }
    public Long getCosto() { return costo; }
    public String getResultado() { return resultado; }
    public ReporteNovedad getNovedad() { return novedad; }
    public String getRegistradoPor() { return registradoPor; }
    public LocalDateTime getCreadoEn() { return creadoEn; }
    public LocalDateTime getActualizadoEn() { return actualizadoEn; }
}
