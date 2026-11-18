package co.edu.usco.labcontrol.repo;

import co.edu.usco.labcontrol.domain.*;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SolicitudArchivoRepository extends JpaRepository<SolicitudArchivo, Long> {
    List<SolicitudArchivo> findBySolicitudIdOrderByAgregadoEnAsc(Long solicitudId);
}
