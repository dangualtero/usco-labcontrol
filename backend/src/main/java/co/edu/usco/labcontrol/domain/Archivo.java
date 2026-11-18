package co.edu.usco.labcontrol.domain;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/** Archivo privado (documentos, cotizaciones, fotografías, evidencias). Solo se descarga con sesión iniciada. */
@Entity
@Table(name = "archivos")
public class Archivo {
    @Id
    private String id;
    private String nombre;
    private String tipoContenido;
    private long tamano;
    @Lob
    private byte[] datos;
    private String subidoPor;
    private LocalDateTime subidoEn;

    protected Archivo() {}

    public Archivo(String id, String nombre, String tipoContenido, byte[] datos, String subidoPor, LocalDateTime subidoEn) {
        this.id = id; this.nombre = nombre; this.tipoContenido = tipoContenido; this.datos = datos; this.tamano = datos.length;
        this.subidoPor = subidoPor; this.subidoEn = subidoEn;
    }

    public String getId() { return id; }
    public String getNombre() { return nombre; }
    public String getTipoContenido() { return tipoContenido; }
    public long getTamano() { return tamano; }
    public byte[] getDatos() { return datos; }
    public String getSubidoPor() { return subidoPor; }
    public LocalDateTime getSubidoEn() { return subidoEn; }
}
