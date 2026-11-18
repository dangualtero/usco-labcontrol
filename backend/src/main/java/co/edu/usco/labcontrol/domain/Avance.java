package co.edu.usco.labcontrol.domain;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

/** Avance registrado por un coordinador o monitor, con sus evidencias. El Administrador lo verifica. */
@Entity
@Table(name = "avances")
public class Avance {
    public enum Verificacion {
        PENDIENTE("Pendiente de verificar"), VERIFICADO("Verificado"), OBSERVADO("Con observaciones");
        private final String texto;
        Verificacion(String texto) { this.texto = texto; }
        public String texto() { return texto; }
    }

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne
    private Sede sede;
    @ManyToOne
    private Laboratorio laboratorio;
    @ManyToOne
    private Compromiso compromiso;
    @ManyToOne
    private Solicitud solicitud;
    private String descripcion;
    private Integer porcentaje;
    private String autor;
    private LocalDateTime creadoEn;
    @Enumerated(EnumType.STRING)
    private Verificacion verificacion = Verificacion.PENDIENTE;
    private String verificadoPor;
    private LocalDateTime verificadoEn;
    private String observacionVerificacion;
    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(name = "avance_archivos", joinColumns = @JoinColumn(name = "avance_id"), inverseJoinColumns = @JoinColumn(name = "archivo_id"))
    private Set<ArchivoInfo> evidencias = new HashSet<>();

    protected Avance() {}

    public Avance(Sede sede, Laboratorio laboratorio, Compromiso compromiso, Solicitud solicitud, String descripcion, Integer porcentaje,
                  String autor, LocalDateTime creadoEn) {
        this.sede = sede; this.laboratorio = laboratorio; this.compromiso = compromiso; this.solicitud = solicitud;
        this.descripcion = descripcion; this.porcentaje = porcentaje; this.autor = autor; this.creadoEn = creadoEn;
    }

    public void verificar(Verificacion v, String quien, String observacion, LocalDateTime cuando) {
        this.verificacion = v; this.verificadoPor = quien; this.observacionVerificacion = observacion; this.verificadoEn = cuando;
    }

    public Long getId() { return id; }
    public Sede getSede() { return sede; }
    public Laboratorio getLaboratorio() { return laboratorio; }
    public Compromiso getCompromiso() { return compromiso; }
    public Solicitud getSolicitud() { return solicitud; }
    public String getDescripcion() { return descripcion; }
    public Integer getPorcentaje() { return porcentaje; }
    public String getAutor() { return autor; }
    public LocalDateTime getCreadoEn() { return creadoEn; }
    public Verificacion getVerificacion() { return verificacion; }
    public String getVerificadoPor() { return verificadoPor; }
    public LocalDateTime getVerificadoEn() { return verificadoEn; }
    public String getObservacionVerificacion() { return observacionVerificacion; }
    public Set<ArchivoInfo> getEvidencias() { return evidencias; }
}
