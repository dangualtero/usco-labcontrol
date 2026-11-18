package co.edu.usco.labcontrol.domain;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/** Cotización, fotografía, soporte o contrato adjunto a una solicitud. */
@Entity
@Table(name = "solicitud_archivos")
public class SolicitudArchivo {
    public enum Tipo {
        COTIZACION("Cotización"), FOTOGRAFIA("Fotografía"), SOPORTE("Soporte"), CONTRATO("Contrato");
        private final String texto;
        Tipo(String texto) { this.texto = texto; }
        public String texto() { return texto; }
    }

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne
    private Solicitud solicitud;
    @ManyToOne
    private ArchivoInfo archivo;
    @Enumerated(EnumType.STRING)
    private Tipo tipo;
    private String agregadoPor;
    private LocalDateTime agregadoEn;

    protected SolicitudArchivo() {}

    public SolicitudArchivo(Solicitud solicitud, ArchivoInfo archivo, Tipo tipo, String agregadoPor, LocalDateTime agregadoEn) {
        this.solicitud = solicitud; this.archivo = archivo; this.tipo = tipo; this.agregadoPor = agregadoPor; this.agregadoEn = agregadoEn;
    }

    public Long getId() { return id; }
    public Solicitud getSolicitud() { return solicitud; }
    public ArchivoInfo getArchivo() { return archivo; }
    public Tipo getTipo() { return tipo; }
    public String getAgregadoPor() { return agregadoPor; }
    public LocalDateTime getAgregadoEn() { return agregadoEn; }
}
