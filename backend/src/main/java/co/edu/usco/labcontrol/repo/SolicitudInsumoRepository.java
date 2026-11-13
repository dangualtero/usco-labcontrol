package co.edu.usco.labcontrol.repo;

import co.edu.usco.labcontrol.domain.SolicitudInsumo;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SolicitudInsumoRepository extends JpaRepository<SolicitudInsumo, Long> {
    List<SolicitudInsumo> findTop200BySedeIdOrderByCreadoEnDesc(Long sedeId);
    List<SolicitudInsumo> findBySedeIdAndEstadoOrderByCreadoEnAsc(Long sedeId, SolicitudInsumo.Estado estado);
    List<SolicitudInsumo> findTop100BySolicitanteIdOrderByCreadoEnDesc(Long solicitanteId);
    long countBySolicitanteIdAndEstado(Long solicitanteId, SolicitudInsumo.Estado estado);
    long countBySedeIdAndEstado(Long sedeId, SolicitudInsumo.Estado estado);
    boolean existsByCodigo(String codigo);
}
