package co.edu.usco.labcontrol.repo;

import co.edu.usco.labcontrol.domain.EstadoPrestamo;
import co.edu.usco.labcontrol.domain.Prestamo;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PrestamoRepository extends JpaRepository<Prestamo, Long> {
    List<Prestamo> findBySolicitanteIdOrderByFechaSolicitudDesc(Long solicitanteId);
    List<Prestamo> findByEstadoOrderByFechaLimiteAsc(EstadoPrestamo estado);
    List<Prestamo> findByEstadoOrderByFranjaInicioAsc(EstadoPrestamo estado);
    List<Prestamo> findByEstadoAndCustodioIdOrderByFechaLimiteAsc(EstadoPrestamo estado, Long custodioId);
    List<Prestamo> findBySolicitanteIdAndEstadoIn(Long solicitanteId, Collection<EstadoPrestamo> estados);
    long countBySolicitanteIdAndEstadoIn(Long solicitanteId, Collection<EstadoPrestamo> estados);
    List<Prestamo> findByEquipoPlacaAndEstadoInOrderByFechaSolicitudDesc(String placa, Collection<EstadoPrestamo> estados);
    long countByIncidenciaIsNotNull();
    boolean existsByCodigo(String codigo);

    /** Reservas/préstamos del equipo que se traslapan con la franja [inicio, fin). */
    @Query("""
           select p from Prestamo p
           where p.equipo.id = :equipoId
             and p.estado in (co.edu.usco.labcontrol.domain.EstadoPrestamo.RESERVADO,
                              co.edu.usco.labcontrol.domain.EstadoPrestamo.ACTIVO)
             and coalesce(p.fechaSalida, p.franjaInicio) < :fin
             and coalesce(p.fechaLimite, p.franjaFin) > :inicio
           """)
    List<Prestamo> traslapes(@Param("equipoId") Long equipoId,
                             @Param("inicio") LocalDateTime inicio, @Param("fin") LocalDateTime fin);

    @Query("select p from Prestamo p where p.fechaSalida is not null and p.fechaSalida >= :desde and p.fechaSalida < :hasta")
    List<Prestamo> conSalidaEntre(@Param("desde") LocalDateTime desde, @Param("hasta") LocalDateTime hasta);
}
