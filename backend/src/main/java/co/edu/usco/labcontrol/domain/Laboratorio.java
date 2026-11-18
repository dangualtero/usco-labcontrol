package co.edu.usco.labcontrol.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Laboratorio de una sede con su caracterización. El Administrador lo crea; el Coordinador de Laboratorio actualiza
 * sus datos (área, capacidad, horario, servicios, normas de seguridad…) y carga sus documentos.
 */
@Entity
@Table(name = "laboratorios")
public class Laboratorio {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    private Sede sede;
    private String nombre;
    private String ubicacion;
    private String descripcion;
    private boolean activo = true;
    private LocalDateTime creadoEn;
    // ---- Caracterización ----
    private String codigo;
    @Column(name = "area_m2")
    private BigDecimal areaM2;
    private Integer capacidad;
    private String horario;
    private String telefono;
    private String correo;
    private String programas;
    private String servicios;
    private String normasSeguridad;
    private LocalDateTime actualizadoEn;
    private String actualizadoPor;

    protected Laboratorio() {}

    public Laboratorio(Sede sede, String nombre, String ubicacion, String descripcion, LocalDateTime creadoEn) {
        this.sede = sede; this.nombre = nombre; this.ubicacion = ubicacion; this.descripcion = descripcion; this.creadoEn = creadoEn;
    }

    public void actualizar(String nombre, String ubicacion, String descripcion) {
        this.nombre = nombre; this.ubicacion = ubicacion; this.descripcion = descripcion;
    }

    public void caracterizar(String codigo, BigDecimal areaM2, Integer capacidad, String horario, String telefono, String correo,
                             String programas, String servicios, String normasSeguridad, String ubicacion, String descripcion,
                             String quien, LocalDateTime cuando) {
        this.codigo = codigo; this.areaM2 = areaM2; this.capacidad = capacidad; this.horario = horario; this.telefono = telefono;
        this.correo = correo; this.programas = programas; this.servicios = servicios; this.normasSeguridad = normasSeguridad;
        this.ubicacion = ubicacion; this.descripcion = descripcion; this.actualizadoPor = quien; this.actualizadoEn = cuando;
    }

    public Long getId() { return id; }
    public Sede getSede() { return sede; }
    public String getNombre() { return nombre; }
    public String getUbicacion() { return ubicacion; }
    public String getDescripcion() { return descripcion; }
    public boolean isActivo() { return activo; }
    public void setActivo(boolean activo) { this.activo = activo; }
    public LocalDateTime getCreadoEn() { return creadoEn; }
    public String getCodigo() { return codigo; }
    public BigDecimal getAreaM2() { return areaM2; }
    public Integer getCapacidad() { return capacidad; }
    public String getHorario() { return horario; }
    public String getTelefono() { return telefono; }
    public String getCorreo() { return correo; }
    public String getProgramas() { return programas; }
    public String getServicios() { return servicios; }
    public String getNormasSeguridad() { return normasSeguridad; }
    public LocalDateTime getActualizadoEn() { return actualizadoEn; }
    public String getActualizadoPor() { return actualizadoPor; }

    @Override public boolean equals(Object o) {
        return this == o || (o instanceof Laboratorio l && id != null && id.equals(l.id));
    }
    @Override public int hashCode() { return Objects.hashCode(id); }
}
