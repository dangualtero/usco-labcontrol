package co.edu.usco.labcontrol.repo;

import co.edu.usco.labcontrol.domain.Laboratorio;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LaboratorioRepository extends JpaRepository<Laboratorio, Long> {
    List<Laboratorio> findBySedeIdOrderByNombreAsc(Long sedeId);
    List<Laboratorio> findBySedeIdAndActivoTrueOrderByNombreAsc(Long sedeId);
    Optional<Laboratorio> findBySedeIdAndNombreIgnoreCase(Long sedeId, String nombre);
    long countBySedeIdAndActivoTrue(Long sedeId);
}
