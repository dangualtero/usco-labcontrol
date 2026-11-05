package co.edu.usco.labcontrol.config;

import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Comprueba al arrancar que MySQL tenga los triggers que hacen inmutable el historial (RF-03).
 * Los triggers se crean con {@code backend/db/02_inmutabilidad.sql}: el backend ya no modifica la estructura de la base
 * de datos, solo avisa en la consola si faltan. En otros motores (H2 de las pruebas) no hace nada.
 */
@Component
@Order(Ordered.LOWEST_PRECEDENCE)
public class HistorialInmutabilidad implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(HistorialInmutabilidad.class);
    private final JdbcTemplate jdbc;

    public HistorialInmutabilidad(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Override
    public void run(ApplicationArguments args) {
        try {
            String motor = jdbc.execute((java.sql.Connection c) -> c.getMetaData().getDatabaseProductName());
            if (motor == null || !motor.toLowerCase().contains("mysql")) return;
            List<String> triggers = jdbc.queryForList(
                    "SELECT trigger_name FROM information_schema.triggers WHERE trigger_schema = DATABASE() AND event_object_table = 'historial_eventos'",
                    String.class);
            if (triggers.size() >= 2) log.info("Historial inmutable: triggers de solo-inserción activos en MySQL");
            else log.warn("Faltan los triggers del historial inmutable. Ejecuta backend/db/02_inmutabilidad.sql como root.");
        } catch (Exception e) {
            log.warn("No se pudo verificar el historial inmutable: {}", e.getMessage());
        }
    }
}
