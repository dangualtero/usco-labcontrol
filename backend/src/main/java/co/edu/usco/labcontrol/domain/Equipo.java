package co.edu.usco.labcontrol.domain;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "equipos")
public class Equipo {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Placa de inventario, formato USCO-EQ-000 (PU-07, PA-09). */
    @Column(nullable = false, unique = true, length = 20)
    private String placa;

    @Column(nullable = false, length = 120) private String nombre;
    @Column(length = 60) private String marca;
    @Column(length = 60) private String modelo;
    @Column(name = "numero_serie", length = 60) private String numeroSerie;
    @Column(nullable = false, length = 60) private String categoria;
    @Column(nullable = false, length = 40) private String laboratorio;
    /** Ubicación física exacta dentro del laboratorio (ej. «Bloque B · Lab. Electrónica · Mesa 3»). */
    @Column(length = 160) private String ubicacion;
    @Column(length = 500) private String descripcion;
    /** Especificaciones separadas por el caracter pipe. */
    @Column(length = 600) private String specs;
    /** Accesorios incluidos en el préstamo, separados por el caracter pipe. */
    @Column(length = 600) private String accesorios;
    /** Condición física: Excelente, Bueno, Regular o Deteriorado. */
    @Column(name = "estado_fisico", length = 30) private String estadoFisico = "Bueno";
    /** Ruta local (/images/equipos/...) o URL de la fotografía. */
    @Column(name = "foto_url", length = 400) private String fotoUrl;

    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20)
    private EstadoEquipo estado = EstadoEquipo.DISPONIBLE;

    /** Soft delete (PS-08): el equipo dado de baja se conserva para auditoría. */
    @Column(nullable = false)
    private boolean activo = true;
    @Column(name = "baja_motivo", length = 300) private String bajaMotivo;
    @Column(name = "baja_fecha") private LocalDateTime bajaFecha;

    /** Sede dueña del equipo: solo la ven y lo prestan los usuarios y auxiliares de esa sede. */
    @ManyToOne(fetch = FetchType.EAGER) @JoinColumn(name = "sede_id")
    private Sede sede;

    protected Equipo() {}

    public Equipo(String placa, String nombre, String marca, String modelo, String numeroSerie, String categoria,
                  String laboratorio, String ubicacion, String descripcion, String specs, String accesorios,
                  String estadoFisico, String fotoUrl, EstadoEquipo estado) {
        this.placa = placa; this.nombre = nombre; this.marca = marca; this.modelo = modelo;
        this.numeroSerie = numeroSerie; this.categoria = categoria; this.laboratorio = laboratorio;
        this.ubicacion = ubicacion; this.descripcion = descripcion; this.specs = specs;
        this.accesorios = accesorios; this.estadoFisico = estadoFisico; this.fotoUrl = fotoUrl; this.estado = estado;
    }

    public void actualizar(String nombre, String marca, String modelo, String numeroSerie, String categoria,
                           String laboratorio, String ubicacion, String descripcion, String specs, String accesorios,
                           String estadoFisico, String fotoUrl) {
        this.nombre = nombre; this.marca = marca; this.modelo = modelo; this.numeroSerie = numeroSerie;
        this.categoria = categoria; this.laboratorio = laboratorio; this.ubicacion = ubicacion;
        this.descripcion = descripcion; this.specs = specs; this.accesorios = accesorios;
        this.estadoFisico = estadoFisico; this.fotoUrl = fotoUrl;
    }

    public void darDeBaja(String motivo, LocalDateTime cuando) {
        this.activo = false;
        this.bajaMotivo = motivo;
        this.bajaFecha = cuando;
    }

    public Long getId() { return id; }
    public String getPlaca() { return placa; }
    public String getNombre() { return nombre; }
    public String getMarca() { return marca; }
    public String getModelo() { return modelo; }
    public String getNumeroSerie() { return numeroSerie; }
    public String getCategoria() { return categoria; }
    public String getLaboratorio() { return laboratorio; }
    public String getUbicacion() { return ubicacion; }
    public String getDescripcion() { return descripcion; }
    public String getSpecs() { return specs; }
    public String getAccesorios() { return accesorios; }
    public String getEstadoFisico() { return estadoFisico; }
    public String getFotoUrl() { return fotoUrl; }
    public EstadoEquipo getEstado() { return estado; }
    public void setEstado(EstadoEquipo estado) { this.estado = estado; }
    public boolean isActivo() { return activo; }
    public String getBajaMotivo() { return bajaMotivo; }
    public LocalDateTime getBajaFecha() { return bajaFecha; }
    public Sede getSede() { return sede; }
    public void setSede(Sede sede) { this.sede = sede; }
}
