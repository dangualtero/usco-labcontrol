package co.edu.usco.labcontrol.repo;

import co.edu.usco.labcontrol.domain.*;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SolicitudEventoRepository extends JpaRepository<SolicitudEvento, Long> {
    List<SolicitudEvento> findBySolicitudIdOrderByFechaAsc(Long solicitudId);

    /** [solicitudId, accion] de las decisiones de la sede, en orden de fecha (la última gana). */
    @org.springframework.data.jpa.repository.Query("select e.solicitud.id, e.accion from SolicitudEvento e where e.solicitud.sede.id = :sede "
            + "and e.accion in ('APROBADA', 'RECHAZADA') order by e.fecha asc")
    List<Object[]> decisiones(@org.springframework.data.repository.query.Param("sede") Long sedeId);
}
