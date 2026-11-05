package co.edu.usco.labcontrol.repo;

import co.edu.usco.labcontrol.domain.MovimientoPresupuesto;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MovimientoPresupuestoRepository extends JpaRepository<MovimientoPresupuesto, Long> {
    List<MovimientoPresupuesto> findBySedeIdOrderByFechaDescIdDesc(Long sedeId);
    List<MovimientoPresupuesto> findAllByOrderByFechaDescIdDesc();
}
