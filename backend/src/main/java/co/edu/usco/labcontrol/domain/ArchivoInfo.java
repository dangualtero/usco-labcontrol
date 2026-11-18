package co.edu.usco.labcontrol.domain;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import org.hibernate.annotations.Immutable;

/** Datos de un archivo SIN su contenido (para listas y relaciones; el contenido se lee con {@link Archivo}). */
@Entity
@Immutable
@Table(name = "archivos")
public class ArchivoInfo {
    @Id
    private String id;
    private String nombre;
    private String tipoContenido;
    private long tamano;
    private String subidoPor;
    private LocalDateTime subidoEn;

    protected ArchivoInfo() {}

    public String getId() { return id; }
    public String getNombre() { return nombre; }
    public String getTipoContenido() { return tipoContenido; }
    public long getTamano() { return tamano; }
    public String getSubidoPor() { return subidoPor; }
    public LocalDateTime getSubidoEn() { return subidoEn; }
}
