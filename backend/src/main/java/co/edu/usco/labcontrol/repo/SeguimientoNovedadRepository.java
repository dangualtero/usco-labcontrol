package co.edu.usco.labcontrol.repo;

import co.edu.usco.labcontrol.domain.SeguimientoNovedad;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SeguimientoNovedadRepository extends JpaRepository<SeguimientoNovedad, Long> {
    List<SeguimientoNovedad> findByReporteIdOrderByFechaAsc(Long reporteId);
    long countByReporteId(Long reporteId);
}
