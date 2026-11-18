package co.edu.usco.labcontrol.repo;

import co.edu.usco.labcontrol.domain.*;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CatalogoRepository extends JpaRepository<Catalogo, Long> {
    List<Catalogo> findByTipoOrderByOrdenAscValorAsc(Catalogo.Tipo tipo);
    List<Catalogo> findAllByOrderByTipoAscOrdenAscValorAsc();
    Optional<Catalogo> findByTipoAndValorIgnoreCase(Catalogo.Tipo tipo, String valor);
    long countByTipo(Catalogo.Tipo tipo);
}
