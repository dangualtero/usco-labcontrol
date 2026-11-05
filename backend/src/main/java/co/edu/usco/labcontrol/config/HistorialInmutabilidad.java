package co.edu.usco.labcontrol.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Refuerza en MySQL la inmutabilidad del historial (RF-03): triggers que rechazan cualquier UPDATE o DELETE sobre
 * {@code historial_eventos}. Es un mecanismo adicional al nivel de aplicación (entidad {@code @Immutable} y repositorio
 * sin operaciones de borrado). Se crea de forma idempotente tras generarse el esquema; en otros motores (H2 de pruebas)
 * no se aplica.
 *
 * <p>Alcance: impide modificar o borrar filas con el usuario de la aplicación. No protege frente a un administrador que
 * elimine los triggers o ejecute TRUNCATE; eso se controla con permisos restrictivos y auditoría del motor.
 */
@Component
@Order(Ordered.LOWEST_PRECEDENCE)
public class HistorialInmutabilidad implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(HistorialInmutabilidad.class);
    private final JdbcTemplate jdbc;

    public HistorialInmutabilidad(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Override
    public void run(ApplicationArguments args) {
        String motor = jdbc.execute((java.sql.Connection c) -> c.getMetaData().getDatabaseProductName());
        if (motor == null || !motor.toLowerCase().contains("mysql")) return;
        try {
        jdbc.execute("CREATE TRIGGER IF NOT EXISTS historial_no_update BEFORE UPDATE ON historial_eventos "
                + "FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'El historial es inmutable: no se permite UPDATE'");
        jdbc.execute("CREATE TRIGGER IF NOT EXISTS historial_no_delete BEFORE DELETE ON historial_eventos "
                + "FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'El historial es inmutable: no se permite DELETE'");
        log.info("Historial inmutable: triggers de solo-inserción activos en MySQL");
        } catch (org.springframework.dao.DataAccessException e) {
            // Con log_bin activo, MySQL exige SUPER o log_bin_trust_function_creators=1 para crear triggers.
            log.warn("No se pudieron crear los triggers de inmutabilidad ({}). La inmutabilidad queda garantizada solo a nivel de "
                    + "aplicación; para activarla en MySQL inicie el servidor con --log-bin-trust-function-creators=1 o use backend/db/inmutabilidad.sql "
                    + "con un usuario administrador.", e.getMostSpecificCause().getMessage());
        }
    }
}
