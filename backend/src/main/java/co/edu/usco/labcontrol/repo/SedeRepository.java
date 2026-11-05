package co.edu.usco.labcontrol.repo;

import co.edu.usco.labcontrol.domain.Sede;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SedeRepository extends JpaRepository<Sede, Long> {
    Optional<Sede> findBySlug(String slug);
    Optional<Sede> findFirstByOrderByIdAsc();
    boolean existsBySlug(String slug);
    boolean existsByNombreIgnoreCase(String nombre);
    List<Sede> findAllByOrderByIdAsc();
    List<Sede> findByActivaTrueOrderByIdAsc();
}
