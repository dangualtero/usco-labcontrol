package co.edu.usco.labcontrol.repo;

import co.edu.usco.labcontrol.domain.Equipo;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface EquipoRepository extends JpaRepository<Equipo, Long> {
    List<Equipo> findByActivoTrueOrderByLaboratorioAscPlacaAsc();
    Optional<Equipo> findByPlacaAndActivoTrue(String placa);
    Optional<Equipo> findByPlaca(String placa);
    boolean existsByPlaca(String placa);

    /**
     * Bloqueo pesimista (SELECT ... FOR UPDATE): serializa solicitudes concurrentes sobre el mismo
     * equipo y evita doble asignación (RNF-02 / PV-05).
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select e from Equipo e where e.placa = :placa and e.activo = true")
    Optional<Equipo> findActivoForUpdate(@Param("placa") String placa);
}
