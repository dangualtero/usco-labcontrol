package co.edu.usco.labcontrol.domain;

import jakarta.persistence.*;

@Entity
@Table(name = "usuarios")
public class Usuario {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String nombre;

    private String correo;

    private String passwordHash;

    @Enumerated(EnumType.STRING)
    private Rol rol;

    private String documento;

    /** Código estudiantil (solo estudiantes); permite buscar en ventanilla por código o por documento. */
    private String codigoEstudiantil;

    private String programa;

    private boolean activo = true;

    /** Sede a la que pertenece. Estudiantes, docentes y auxiliares tienen una; el Coordinador (general) no tiene: administra todas. */
    @ManyToOne
    private Sede sede;

    protected Usuario() {}

    public Usuario(String nombre, String correo, String passwordHash, Rol rol, String documento,
                   String codigoEstudiantil, String programa) {
        this.nombre = nombre;
        this.correo = correo;
        this.passwordHash = passwordHash;
        this.rol = rol;
        this.documento = documento;
        this.codigoEstudiantil = codigoEstudiantil;
        this.programa = programa;
    }

    public Long getId() { return id; }
    public String getNombre() { return nombre; }
    public void setNombre(String nombre) { this.nombre = nombre; }
    public String getCorreo() { return correo; }
    public String getPasswordHash() { return passwordHash; }
    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }
    public Rol getRol() { return rol; }
    public void setRol(Rol rol) { this.rol = rol; }
    public String getDocumento() { return documento; }
    public String getCodigoEstudiantil() { return codigoEstudiantil; }
    public String getPrograma() { return programa; }
    public void setPrograma(String programa) { this.programa = programa; }
    public boolean isActivo() { return activo; }
    public void setActivo(boolean activo) { this.activo = activo; }
    public Sede getSede() { return sede; }
    public void setSede(Sede sede) { this.sede = sede; }
}
