package co.edu.usco.labcontrol.repo;

import co.edu.usco.labcontrol.domain.*;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AvanceRepository extends JpaRepository<Avance, Long> {
    List<Avance> findTop500BySedeIdOrderByCreadoEnDesc(Long sedeId);
    List<Avance> findBySolicitudIdOrderByCreadoEnDesc(Long solicitudId);
    List<Avance> findByCompromisoIdOrderByCreadoEnDesc(Long compromisoId);
}
