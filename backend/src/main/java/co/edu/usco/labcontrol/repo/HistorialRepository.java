package co.edu.usco.labcontrol.repo;

import co.edu.usco.labcontrol.domain.HistorialEvento;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

/** Solo lectura + inserción: no extiende JpaRepository para no exponer delete/update (RF-03). */
public interface HistorialRepository extends Repository<HistorialEvento, Long> {
    HistorialEvento save(HistorialEvento evento);

    @Query("select h from HistorialEvento h where h.fecha >= :desde and h.fecha < :hasta order by h.fecha desc")
    List<HistorialEvento> entre(@Param("desde") LocalDateTime desde, @Param("hasta") LocalDateTime hasta);

    long count();
}
