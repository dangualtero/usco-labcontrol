package co.edu.usco.labcontrol.repo;

import co.edu.usco.labcontrol.domain.RegistroPendiente;
import java.time.LocalDateTime;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RegistroPendienteRepository extends JpaRepository<RegistroPendiente, Long> {
    Optional<RegistroPendiente> findByCorreoIgnoreCase(String correo);
    boolean existsByDocumentoAndCorreoNot(String documento, String correo);

    /** Limpieza: solicitudes que nadie confirmó en 24 horas. */
    @Modifying
    @Query("delete from RegistroPendiente r where r.expiraEn < :limite")
    int borrarVencidos(@Param("limite") LocalDateTime limite);
}
