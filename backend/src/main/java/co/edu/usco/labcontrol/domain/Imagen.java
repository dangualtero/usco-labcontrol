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
    @Id
    private String id;

    /** image/jpeg, image/png o image/webp (verificado por la firma del archivo, no por la extensión). */
    private String tipoContenido;

    @Lob
    private byte[] datos;

    private long tamano;
    private String nombreOriginal;
    private String subidaPor;
    private LocalDateTime subidaEn;

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
