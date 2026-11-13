package co.edu.usco.labcontrol.domain;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/** Laboratorio de una sede. Cada sede tiene los suyos; el Coordinador los crea desde «Laboratorios». */
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

    protected Laboratorio() {}

    public Laboratorio(Sede sede, String nombre, String ubicacion, String descripcion, LocalDateTime creadoEn) {
        this.sede = sede; this.nombre = nombre; this.ubicacion = ubicacion; this.descripcion = descripcion; this.creadoEn = creadoEn;
    }

    public void actualizar(String nombre, String ubicacion, String descripcion) {
        this.nombre = nombre; this.ubicacion = ubicacion; this.descripcion = descripcion;
    }

    public Long getId() { return id; }
    public Sede getSede() { return sede; }
    public String getNombre() { return nombre; }
    public String getUbicacion() { return ubicacion; }
    public String getDescripcion() { return descripcion; }
    public boolean isActivo() { return activo; }
    public void setActivo(boolean activo) { this.activo = activo; }
    public LocalDateTime getCreadoEn() { return creadoEn; }
}
