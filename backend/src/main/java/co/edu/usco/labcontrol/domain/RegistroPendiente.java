package co.edu.usco.labcontrol.domain;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * Solicitud de registro de un estudiante que todavía NO confirmó su correo. Mientras esté aquí la persona no existe en
 * {@code usuarios} y no puede iniciar sesión. Al ingresar el código de verificación se crea el usuario y se borra esta fila.
 * El código y la contraseña se guardan cifrados con BCrypt.
 */
@Entity
@Table(name = "registros_pendientes")
public class RegistroPendiente {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String correo;
    private String nombre;
    private String documento;
    private String codigoEstudiantil;
    private String programa;
    private String passwordHash;

    @ManyToOne
    private Sede sede;

    private String codigoHash;
    private LocalDateTime expiraEn;
    private int intentos;
    private LocalDateTime ultimoEnvio;
    /** Envíos dentro de la ventana actual de una hora (evita usar el registro para llenar de correos una bandeja ajena). */
    private int enviosVentana;
    private LocalDateTime ventanaDesde;
    private LocalDateTime creadoEn;

    protected RegistroPendiente() {}

    public RegistroPendiente(String correo, LocalDateTime ahora) {
        this.correo = correo; this.creadoEn = ahora; this.ventanaDesde = ahora; this.ultimoEnvio = ahora;
    }

    public void datos(String nombre, String documento, String codigoEstudiantil, String programa, String passwordHash, Sede sede) {
        this.nombre = nombre; this.documento = documento; this.codigoEstudiantil = codigoEstudiantil;
        this.programa = programa; this.passwordHash = passwordHash; this.sede = sede;
    }

    /** Nuevo código: reinicia los intentos y cuenta el envío dentro de la ventana de una hora. */
    public void nuevoCodigo(String codigoHash, LocalDateTime ahora, LocalDateTime expira) {
        if (ventanaDesde == null || ventanaDesde.plusHours(1).isBefore(ahora)) { ventanaDesde = ahora; enviosVentana = 0; }
        this.codigoHash = codigoHash; this.expiraEn = expira; this.intentos = 0; this.ultimoEnvio = ahora; this.enviosVentana++;
    }

    public void fallo() { intentos++; }

    public Long getId() { return id; }
    public String getCorreo() { return correo; }
    public String getNombre() { return nombre; }
    public String getDocumento() { return documento; }
    public String getCodigoEstudiantil() { return codigoEstudiantil; }
    public String getPrograma() { return programa; }
    public String getPasswordHash() { return passwordHash; }
    public Sede getSede() { return sede; }
    public String getCodigoHash() { return codigoHash; }
    public LocalDateTime getExpiraEn() { return expiraEn; }
    public int getIntentos() { return intentos; }
    public LocalDateTime getUltimoEnvio() { return ultimoEnvio; }
    public int getEnviosVentana() { return enviosVentana; }
    public LocalDateTime getVentanaDesde() { return ventanaDesde; }
}
