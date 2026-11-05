package co.edu.usco.labcontrol.domain;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * Fotografía subida desde un computador o un celular. Se guarda en MySQL (no en una carpeta del servidor) para que
 * viaje con los respaldos de la base de datos y funcione igual en cualquier equipo donde corra la API.
 * Se sirve en {@code /api/imagenes/{id}}; esa ruta es la que queda en {@code Equipo.fotoUrl}.
 */
@Entity
@Table(name = "imagenes")
public class Imagen {
    /** Identificador aleatorio (UUID sin guiones): no se puede adivinar ni recorrer en orden. */
    @Id @Column(length = 32)
    private String id;

    /** image/jpeg, image/png o image/webp (verificado por la firma del archivo, no por la extensión). */
    @Column(name = "tipo_contenido", nullable = false, length = 20)
    private String tipoContenido;

    @Lob @Basic(fetch = FetchType.LAZY)
    @Column(nullable = false, columnDefinition = "LONGBLOB")
    private byte[] datos;

    @Column(nullable = false) private long tamano;
    @Column(name = "nombre_original", length = 160) private String nombreOriginal;
    @Column(name = "subida_por", nullable = false, length = 120) private String subidaPor;
    @Column(name = "subida_en", nullable = false) private LocalDateTime subidaEn;

    protected Imagen() {}

    public Imagen(String id, String tipoContenido, byte[] datos, String nombreOriginal, String subidaPor, LocalDateTime subidaEn) {
        this.id = id; this.tipoContenido = tipoContenido; this.datos = datos; this.tamano = datos.length;
        this.nombreOriginal = nombreOriginal; this.subidaPor = subidaPor; this.subidaEn = subidaEn;
    }

    public String getId() { return id; }
    public String getTipoContenido() { return tipoContenido; }
    public byte[] getDatos() { return datos; }
    public long getTamano() { return tamano; }
    public String getNombreOriginal() { return nombreOriginal; }
    public String getSubidaPor() { return subidaPor; }
    public LocalDateTime getSubidaEn() { return subidaEn; }
}
