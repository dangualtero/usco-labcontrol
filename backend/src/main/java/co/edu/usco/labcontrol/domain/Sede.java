package co.edu.usco.labcontrol.domain;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * Sede de la Universidad Surcolombiana (Neiva, Garzón, Pitalito, La Plata…). Cada sede tiene su propio inventario,
 * sus estudiantes, docentes y auxiliares, y su propio presupuesto. El Coordinador es general: administra todas.
 * El {@code slug} es el segmento de la URL del frontend: /neiva, /pitalito, /garzon…
 */
@Entity
@Table(name = "sedes")
public class Sede {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Identificador para la URL: minúsculas, sin tildes ni espacios (ej. «pitalito», «la-plata»). */
    private String slug;

    /** Nombre visible, ej. «Sede Pitalito». */
    private String nombre;

    private String ciudad;
    private String direccion;

    private boolean activa = true;
    private LocalDateTime creadaEn;

    protected Sede() {}

    public Sede(String slug, String nombre, String ciudad, String direccion, LocalDateTime creadaEn) {
        this.slug = slug; this.nombre = nombre; this.ciudad = ciudad; this.direccion = direccion; this.creadaEn = creadaEn;
    }

    public void actualizar(String nombre, String ciudad, String direccion) {
        this.nombre = nombre; this.ciudad = ciudad; this.direccion = direccion;
    }

    public Long getId() { return id; }
    public String getSlug() { return slug; }
    public String getNombre() { return nombre; }
    public String getCiudad() { return ciudad; }
    public String getDireccion() { return direccion; }
    public boolean isActiva() { return activa; }
    public void setActiva(boolean activa) { this.activa = activa; }
    public LocalDateTime getCreadaEn() { return creadaEn; }

    /** Compara por identificador (las entidades pueden venir de sesiones JPA distintas). */
    public boolean es(Sede otra) { return otra != null && id != null && id.equals(otra.getId()); }
}
