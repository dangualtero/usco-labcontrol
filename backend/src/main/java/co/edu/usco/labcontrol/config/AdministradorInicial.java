package co.edu.usco.labcontrol.config;

import co.edu.usco.labcontrol.domain.Rol;
import co.edu.usco.labcontrol.domain.Usuario;
import co.edu.usco.labcontrol.repo.UsuarioRepository;
import co.edu.usco.labcontrol.service.AuthService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Primer acceso sin usuarios quemados en el código: si la base no tiene ningún Administrador general activo, crea uno con los
 * datos de las variables de entorno LABCONTROL_ADMIN_CORREO y LABCONTROL_ADMIN_CLAVE (y opcionalmente
 * LABCONTROL_ADMIN_NOMBRE y LABCONTROL_ADMIN_DOCUMENTO). Si ya hay un Administrador, no hace nada.
 * Después del primer ingreso, el resto de cuentas se crean desde «Usuarios» (con su rol y laboratorios).
 */
@Component
@Order(20)
public class AdministradorInicial implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(AdministradorInicial.class);

    private final UsuarioRepository usuarios;
    private final PasswordEncoder encoder;
    private final String correo;
    private final String clave;
    private final String nombre;
    private final String documento;

    public AdministradorInicial(UsuarioRepository usuarios, PasswordEncoder encoder,
                                @Value("${labcontrol.admin-inicial.correo:}") String correo,
                                @Value("${labcontrol.admin-inicial.clave:}") String clave,
                                @Value("${labcontrol.admin-inicial.nombre:Administrador LabControl}") String nombre,
                                @Value("${labcontrol.admin-inicial.documento:1000000001}") String documento) {
        this.usuarios = usuarios; this.encoder = encoder;
        this.correo = correo == null ? "" : correo.trim().toLowerCase();
        this.clave = clave == null ? "" : clave;
        this.nombre = nombre == null || nombre.isBlank() ? "Administrador LabControl" : nombre.trim();
        this.documento = documento == null ? "" : documento.trim();
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (!usuarios.findByRolAndActivoTrue(Rol.ADMINISTRADOR).isEmpty()) return;
        if (correo.isEmpty() || clave.isEmpty()) {
            log.warn("No hay ningún Administrador general activo. Para el primer ingreso define LABCONTROL_ADMIN_CORREO y "
                    + "LABCONTROL_ADMIN_CLAVE y reinicia el backend.");
            return;
        }
        if (!AuthService.CORREO_USCO.matcher(correo).matches()) {
            log.error("LABCONTROL_ADMIN_CORREO debe ser un correo @usco.edu.co; no se creó el administrador.");
            return;
        }
        if (!documento.matches("\\d{5,15}")) {
            log.error("LABCONTROL_ADMIN_DOCUMENTO debe tener entre 5 y 15 dígitos; no se creó el administrador.");
            return;
        }
        try {
            AuthService.validarClave(clave);
        } catch (RuntimeException e) {
            log.error("LABCONTROL_ADMIN_CLAVE no es válida ({}); no se creó el administrador.", e.getMessage());
            return;
        }
        var existente = usuarios.findByCorreoIgnoreCase(correo);
        if (existente.isPresent()) {
            log.error("Ya existe una cuenta con {} pero no es un Administrador activo; actívala o usa otro correo.", correo);
            return;
        }
        if (usuarios.existsByDocumento(documento)) {
            log.error("Ya existe un usuario con el documento {}; define otro en LABCONTROL_ADMIN_DOCUMENTO.", documento);
            return;
        }
        usuarios.save(new Usuario(nombre, correo, encoder.encode(clave), Rol.ADMINISTRADOR, documento, "Coordinación de Laboratorios"));
        log.info("Se creó el Administrador general inicial {}. Ya puedes quitar LABCONTROL_ADMIN_CLAVE de la configuración.", correo);
    }
}
