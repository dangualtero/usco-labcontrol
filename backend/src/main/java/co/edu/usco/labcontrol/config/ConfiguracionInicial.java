package co.edu.usco.labcontrol.config;

import co.edu.usco.labcontrol.domain.Catalogo;
import co.edu.usco.labcontrol.domain.Equipo;
import co.edu.usco.labcontrol.repo.CatalogoRepository;
import co.edu.usco.labcontrol.repo.EquipoRepository;
import co.edu.usco.labcontrol.service.ConfiguracionService;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Catálogos iniciales (idempotente): si un catálogo está vacío lo llena con sus valores por defecto. Además, las
 * categorías y estados físicos que ya usan los equipos quedan registrados para que el inventario existente siga siendo
 * válido. Los parámetros y plantillas no necesitan carga: si no están en la base se usan los valores por defecto.
 */
@Component
@Order(15)
public class ConfiguracionInicial implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(ConfiguracionInicial.class);

    private final CatalogoRepository catalogos;
    private final EquipoRepository equipos;

    public ConfiguracionInicial(CatalogoRepository catalogos, EquipoRepository equipos) {
        this.catalogos = catalogos; this.equipos = equipos;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        int creados = 0;
        for (Map.Entry<Catalogo.Tipo, List<String>> c : ConfiguracionService.CATALOGOS.entrySet()) {
            if (catalogos.countByTipo(c.getKey()) > 0) continue;
            int orden = 1;
            for (String v : c.getValue()) { catalogos.save(new Catalogo(c.getKey(), v, orden++)); creados++; }
        }
        List<Equipo> todos = equipos.findAll();
        creados += enUso(Catalogo.Tipo.CATEGORIA_EQUIPO, todos, Equipo::getCategoria);
        creados += enUso(Catalogo.Tipo.ESTADO_FISICO, todos, Equipo::getEstadoFisico);
        if (creados > 0) log.info("Catálogos: se registraron {} valores", creados);
    }

    private int enUso(Catalogo.Tipo tipo, List<Equipo> todos, Function<Equipo, String> campo) {
        Set<String> valores = new LinkedHashSet<>();
        for (Equipo e : todos) {
            String v = campo.apply(e);
            if (v != null && !v.isBlank()) valores.add(v.trim());
        }
        int n = 0;
        for (String v : valores) {
            if (v.length() > 80 || catalogos.findByTipoAndValorIgnoreCase(tipo, v).isPresent()) continue;
            catalogos.save(new Catalogo(tipo, v, 900));
            n++;
        }
        return n;
    }
}
