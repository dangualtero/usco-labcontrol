package co.edu.usco.labcontrol.service;

import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Permite crear usuarios directamente en MySQL escribiendo la contraseña NORMAL en la columna {@code password_hash}:
 *
 * <pre>INSERT INTO usuarios (..., password_hash, ...) VALUES (..., 'Usco#2026', ...);</pre>
 *
 * Al arrancar y luego cada pocos segundos, el backend busca contraseñas que todavía no tienen formato BCrypt
 * (no empiezan por {@code $2}) y las reemplaza por su hash. Si la persona inicia sesión antes de esa revisión,
 * {@link AuthService} también la cifra en ese momento. La contraseña en texto normal solo existe en la base de datos
 * durante esos segundos.
 */
@Component
public class CifradoContrasenas {
    private static final Logger log = LoggerFactory.getLogger(CifradoContrasenas.class);

    private final JdbcTemplate jdbc;
    private final PasswordEncoder encoder;

    public CifradoContrasenas(JdbcTemplate jdbc, PasswordEncoder encoder) { this.jdbc = jdbc; this.encoder = encoder; }

    /** true si el valor guardado ya es un hash BCrypt ($2a$, $2b$ o $2y$). */
    public static boolean esHash(String valor) {
        return valor != null && valor.length() == 60 && valor.startsWith("$2");
    }

    @Scheduled(initialDelayString = "${labcontrol.seguridad.cifrado-inicial-ms:3000}",
               fixedDelayString = "${labcontrol.seguridad.cifrado-cada-ms:10000}")
    public void cifrarPendientes() {
        int n = cifrar();
        if (n > 0) log.info("Se cifraron {} contraseña(s) escritas en texto normal en la base de datos", n);
    }

    /** @return cuántas contraseñas se cifraron. */
    public int cifrar() {
        List<Map<String, Object>> pendientes;
        try {
            pendientes = jdbc.queryForList(
                    "SELECT id, correo, password_hash FROM usuarios WHERE password_hash NOT LIKE '$2%' OR CHAR_LENGTH(password_hash) <> 60");
        } catch (Exception e) {
            log.debug("No se pudo revisar las contraseñas: {}", e.getMessage());
            return 0;
        }
        int cifradas = 0;
        for (Map<String, Object> fila : pendientes) {
            String plana = (String) fila.get("password_hash");
            if (plana == null || plana.isEmpty() || esHash(plana)) continue;
            if (plana.length() < 8) {
                log.warn("La contraseña inicial de {} tiene menos de 8 caracteres: se cifra igual, pero pídele que la cambie en «Mi cuenta»", fila.get("correo"));
            }
            // Solo se reemplaza si nadie la cambió entretanto (misma contraseña plana).
            cifradas += jdbc.update("UPDATE usuarios SET password_hash = ? WHERE id = ? AND password_hash = ?",
                    encoder.encode(plana), fila.get("id"), plana);
        }
        return cifradas;
    }
}
