package co.edu.usco.labcontrol.repo;

import co.edu.usco.labcontrol.domain.ReporteNovedad;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ReporteNovedadRepository extends JpaRepository<ReporteNovedad, Long> {
    List<ReporteNovedad> findTop50BySedeIdOrderByCreadoEnDesc(Long sedeId);
    List<ReporteNovedad> findTop500BySedeIdOrderByCreadoEnDesc(Long sedeId);
    List<ReporteNovedad> findBySedeIdAndNotificacionOrderByCreadoEnAsc(Long sedeId, ReporteNovedad.Notificacion notificacion);
    List<ReporteNovedad> findBySedeIdAndPrioridadAndEstadoInOrderByCreadoEnDesc(Long sedeId, String prioridad, Collection<ReporteNovedad.Estado> estados);
    /** Huella barata de los reportes de una sede: cambia cuando llega uno nuevo, se notifica o se actualiza su seguimiento. */
    @Query("select count(r), max(r.id), max(r.actualizadoEn), max(r.notificadoEn), sum(r.intentosNotificacion) from ReporteNovedad r where r.sede.id = :sede")
    List<Object[]> huella(@Param("sede") Long sedeId);
    boolean existsByEquipoIdAndTipoAndReportadoPorAndCreadoEnAfter(Long equipoId, ReporteNovedad.Tipo tipo, String reportadoPor, LocalDateTime desde);
}
