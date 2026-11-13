package co.edu.usco.labcontrol.config;

import co.edu.usco.labcontrol.domain.Laboratorio;
import co.edu.usco.labcontrol.domain.Rol;
import co.edu.usco.labcontrol.domain.Sede;
import co.edu.usco.labcontrol.repo.EquipoRepository;
import co.edu.usco.labcontrol.repo.LaboratorioRepository;
import co.edu.usco.labcontrol.service.CoordinadorService;
import co.edu.usco.labcontrol.repo.SedeRepository;
import co.edu.usco.labcontrol.repo.UsuarioRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Migración a sedes (se ejecuta en cada arranque y es idempotente):
 * <ol>
 *   <li>Si no existe ninguna sede, crea la Sede Neiva (sede principal).</li>
 *   <li>Los equipos y los usuarios (no coordinadores) que existían antes de las sedes quedan asignados a la sede principal.</li>
 *   <li>Laboratorios: cada nombre de laboratorio que usan los equipos de una sede queda registrado en esa sede, y la sede
 *       principal arranca con los 4 de siempre si no tiene ninguno.</li>
 * </ol>
 * Así una base de datos que ya tenía información sigue funcionando sin perder nada. Corre después de la carga inicial.
 */
@Component
@Order(10)
public class SedesIniciales implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(SedesIniciales.class);

    private final SedeRepository sedes;
    private final EquipoRepository equipos;
    private final UsuarioRepository usuarios;
    private final Clock clock;
    private final LaboratorioRepository laboratorios;

    public SedesIniciales(SedeRepository sedes, EquipoRepository equipos, UsuarioRepository usuarios, Clock clock,
                          LaboratorioRepository laboratorios) {
        this.sedes = sedes; this.equipos = equipos; this.usuarios = usuarios; this.clock = clock; this.laboratorios = laboratorios;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (sedes.count() == 0) {
            sedes.save(new Sede("neiva", "Sede Neiva", "Neiva", "Av. Pastrana Borrero, Cra. 1", LocalDateTime.now(clock)));
            log.info("Sedes: se creó la Sede Neiva (sede principal)");
        }
        Sede principal = sedes.findFirstByOrderByIdAsc().orElseThrow();
        var sinSedeEq = equipos.findBySedeIsNull();
        sinSedeEq.forEach(e -> e.setSede(principal));
        var sinSedeUs = usuarios.findBySedeIsNullAndRolNot(Rol.COORDINADOR);
        sinSedeUs.forEach(u -> u.setSede(principal));
        if (!sinSedeEq.isEmpty() || !sinSedeUs.isEmpty()) {
            log.info("Sedes: {} equipos y {} usuarios asignados a {}", sinSedeEq.size(), sinSedeUs.size(), principal.getNombre());
        }
        laboratorios();
    }

    private void laboratorios() {
        LocalDateTime ahora = LocalDateTime.now(clock);
        Sede principal = sedes.findFirstByOrderByIdAsc().orElseThrow();
        int creados = 0;
        if (laboratorios.findBySedeIdOrderByNombreAsc(principal.getId()).isEmpty()) {
            for (String n : CoordinadorService.LABORATORIOS) { laboratorios.save(new Laboratorio(principal, n, null, null, ahora)); creados++; }
        }
        for (Sede s : sedes.findAllByOrderByIdAsc()) {
            var usados = equipos.findBySedeIdOrderByLaboratorioAscPlacaAsc(s.getId()).stream()
                    .map(e -> e.getLaboratorio()).filter(n -> n != null && !n.isBlank()).distinct().toList();
            for (String n : usados) {
                if (laboratorios.findBySedeIdAndNombreIgnoreCase(s.getId(), n).isEmpty()) {
                    laboratorios.save(new Laboratorio(s, n, null, null, ahora));
                    creados++;
                }
            }
        }
        if (creados > 0) log.info("Laboratorios: se registraron {} laboratorios a partir del inventario", creados);
    }
}
