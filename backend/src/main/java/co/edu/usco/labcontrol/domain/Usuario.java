package co.edu.usco.labcontrol.domain;

import jakarta.persistence.*;
import java.util.HashSet;
import java.util.Set;

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

    /** Cargo, programa o dependencia. */
    private String programa;

    private String telefono;

    private boolean activo = true;

    /** Sede a la que pertenece. Coordinadores y monitores tienen una; el Administrador (general) no tiene: administra todas. */
    @ManyToOne
    private Sede sede;

    /** Laboratorios asignados (Coordinador de Laboratorio y Monitor): uno o varios. */
    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(name = "usuario_laboratorios", joinColumns = @JoinColumn(name = "usuario_id"),
               inverseJoinColumns = @JoinColumn(name = "laboratorio_id"))
    private Set<Laboratorio> laboratorios = new HashSet<>();

    protected Usuario() {}

    public Usuario(String nombre, String correo, String passwordHash, Rol rol, String documento, String programa) {
        this.nombre = nombre;
        this.correo = correo;
        this.passwordHash = passwordHash;
        this.rol = rol;
        this.documento = documento;
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
    public String getTelefono() { return telefono; }
    public void setTelefono(String telefono) { this.telefono = telefono; }
    public void setDocumento(String documento) { this.documento = documento; }
    public Set<Laboratorio> getLaboratorios() { return laboratorios; }
    public boolean esAdministrador() { return rol == Rol.ADMINISTRADOR; }
    public String getPrograma() { return programa; }
    public void setPrograma(String programa) { this.programa = programa; }
    public boolean isActivo() { return activo; }
    public void setActivo(boolean activo) { this.activo = activo; }
    public Sede getSede() { return sede; }
    public void setSede(Sede sede) { this.sede = sede; }
}
