package co.edu.usco.labcontrol.repo;

import co.edu.usco.labcontrol.domain.*;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MantenimientoRepository extends JpaRepository<Mantenimiento, Long> {
    List<Mantenimiento> findTop500BySedeIdOrderByCreadoEnDesc(Long sedeId);
    List<Mantenimiento> findByEquipoIdOrderByCreadoEnDesc(Long equipoId);
    boolean existsByCodigo(String codigo);
    long countByCodigoStartingWith(String prefijo);
}
