package co.edu.usco.labcontrol.domain;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/** Documento o evidencia cargado a la caracterización de un laboratorio. */
@Entity
@Table(name = "laboratorio_documentos")
public class LaboratorioDocumento {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne
    private Laboratorio laboratorio;
    private String tipo;
    private String titulo;
    private String descripcion;
    @ManyToOne
    private ArchivoInfo archivo;
    private String subidoPor;
    private LocalDateTime subidoEn;

    protected LaboratorioDocumento() {}

    public LaboratorioDocumento(Laboratorio laboratorio, String tipo, String titulo, String descripcion, ArchivoInfo archivo,
                                String subidoPor, LocalDateTime subidoEn) {
        this.laboratorio = laboratorio; this.tipo = tipo; this.titulo = titulo; this.descripcion = descripcion;
        this.archivo = archivo; this.subidoPor = subidoPor; this.subidoEn = subidoEn;
    }

    public Long getId() { return id; }
    public Laboratorio getLaboratorio() { return laboratorio; }
    public String getTipo() { return tipo; }
    public String getTitulo() { return titulo; }
    public String getDescripcion() { return descripcion; }
    public ArchivoInfo getArchivo() { return archivo; }
    public String getSubidoPor() { return subidoPor; }
    public LocalDateTime getSubidoEn() { return subidoEn; }
}
