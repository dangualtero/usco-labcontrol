package co.edu.usco.labcontrol.repo;

import co.edu.usco.labcontrol.domain.*;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SolicitudRepository extends JpaRepository<Solicitud, Long> {
    List<Solicitud> findTop500BySedeIdOrderByCreadoEnDesc(Long sedeId);
    boolean existsByCodigo(String codigo);
    long countByCodigoStartingWith(String prefijo);
}
