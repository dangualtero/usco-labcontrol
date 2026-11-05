package co.edu.usco.labcontrol.repo;

import co.edu.usco.labcontrol.domain.ReporteNovedad;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReporteNovedadRepository extends JpaRepository<ReporteNovedad, Long> {
    List<ReporteNovedad> findTop50BySedeIdOrderByCreadoEnDesc(Long sedeId);
    boolean existsByEquipoIdAndTipoAndReportadoPorAndCreadoEnAfter(Long equipoId, ReporteNovedad.Tipo tipo, String reportadoPor, LocalDateTime desde);
}
