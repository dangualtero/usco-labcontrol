package co.edu.usco.labcontrol.service;

import co.edu.usco.labcontrol.domain.Usuario;
import co.edu.usco.labcontrol.repo.UsuarioRepository;
import co.edu.usco.labcontrol.security.JwtService;
import co.edu.usco.labcontrol.web.ApiException;
import co.edu.usco.labcontrol.web.Dtos.*;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {
    public static final Pattern CORREO_USCO = Pattern.compile("^[A-Za-z0-9._%+-]+@usco\\.edu\\.co$");
    private static final Pattern CLAVE_VALIDA = Pattern.compile("^(?=.*[A-Za-z])(?=.*\\d).{8,72}$");
    private static final int MAX_INTENTOS = 5;
    private static final Duration BLOQUEO = Duration.ofMinutes(5);
    private static final String ERROR_GENERICO = "Credenciales inválidas";

    private record Intentos(int n, Instant desde) {}

    private final UsuarioRepository usuarios;
    private final PasswordEncoder encoder;
    private final JwtService jwt;
    private final Mapper mapper;
    private final Clock clock;
    private final Map<String, Intentos> fallos = new ConcurrentHashMap<>();

    public AuthService(UsuarioRepository usuarios, PasswordEncoder encoder, JwtService jwt, Mapper mapper, Clock clock) {
        this.usuarios = usuarios; this.encoder = encoder; this.jwt = jwt; this.mapper = mapper; this.clock = clock;
    }

    /** El rol NO lo elige el usuario: se obtiene de la cuenta almacenada en MySQL. */
    public LoginResponse login(LoginRequest r) {
        String correo = r.email() == null ? "" : r.email().trim().toLowerCase();
        if (correo.isEmpty()) throw ApiException.badRequest("El correo institucional es obligatorio");
        if (!CORREO_USCO.matcher(correo).matches()) throw ApiException.badRequest("Formato de correo no válido (@usco.edu.co)");

        Intentos i = fallos.get(correo);
        if (i != null && i.n() >= MAX_INTENTOS && Duration.between(i.desde(), clock.instant()).compareTo(BLOQUEO) < 0) {
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "Demasiados intentos. Espere unos minutos e intente de nuevo.");
        }

        // Mensaje único y genérico: no revela si el correo existe ni cuál dato falló (PS-08).
        Usuario u = usuarios.findByCorreoIgnoreCase(correo).filter(Usuario::isActivo).orElse(null);
        boolean ok = u != null && encoder.matches(r.password(), u.getPasswordHash());
        if (!ok) {
            fallos.merge(correo, new Intentos(1, clock.instant()),
                    (a, b) -> new Intentos(a.n() + 1, a.n() + 1 >= MAX_INTENTOS ? clock.instant() : a.desde()));
            throw new ApiException(HttpStatus.UNAUTHORIZED, ERROR_GENERICO);
        }
        fallos.remove(correo);
        verificarSede(u, r.sede());
        boolean recordar = Boolean.TRUE.equals(r.recordar());
        return new LoginResponse(jwt.generar(u, recordar), mapper.usuario(u), jwt.expiraEnSegundos(recordar));
    }

    /**
     * Cada sede tiene su propia URL (/neiva/login, /pitalito/login…). Estudiantes, docentes y auxiliares solo ingresan por
     * la de su sede; el Coordinador general ingresa por cualquiera. Se valida DESPUÉS de comprobar la contraseña, para
     * no revelar a terceros a qué sede pertenece una cuenta.
     */
    private static void verificarSede(Usuario u, String slugUrl) {
        if (u.getSede() == null) return;
        if (!u.getSede().isActiva()) {
            throw new ApiException(HttpStatus.FORBIDDEN, "La " + u.getSede().getNombre() + " está inactiva. Comunícate con la Coordinación de Laboratorios.");
        }
        if (slugUrl != null && !slugUrl.isBlank() && !u.getSede().getSlug().equalsIgnoreCase(slugUrl.trim())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Tu cuenta pertenece a la " + u.getSede().getNombre()
                    + ". Ingresa desde la dirección /" + u.getSede().getSlug());
        }
    }

    @Transactional(readOnly = true)
    public UsuarioDto yo(Long id) {
        return mapper.usuario(usuarios.findById(id).orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Sesión no válida o expirada")));
    }

    @Transactional
    public void cambiarClave(Long id, CambiarClaveRequest r) {
        Usuario u = usuarios.findById(id).orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Sesión no válida o expirada"));
        if (r.actual() == null || !encoder.matches(r.actual(), u.getPasswordHash())) throw ApiException.badRequest("La contraseña actual no es correcta");
        validarClave(r.nueva());
        if (encoder.matches(r.nueva(), u.getPasswordHash())) throw ApiException.badRequest("La nueva contraseña debe ser diferente de la actual");
        u.setPasswordHash(encoder.encode(r.nueva()));
    }

    public static void validarClave(String clave) {
        if (clave == null || !CLAVE_VALIDA.matcher(clave).matches()) {
            throw ApiException.badRequest("La contraseña debe tener entre 8 y 72 caracteres e incluir letras y números");
        }
    }
}
