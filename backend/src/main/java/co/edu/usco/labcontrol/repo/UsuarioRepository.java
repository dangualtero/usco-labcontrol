package co.edu.usco.labcontrol.repo;

import co.edu.usco.labcontrol.domain.Rol;
import co.edu.usco.labcontrol.domain.Usuario;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UsuarioRepository extends JpaRepository<Usuario, Long> {
    Optional<Usuario> findByCorreoIgnoreCase(String correo);
    Optional<Usuario> findByDocumento(String documento);
    boolean existsByCorreoIgnoreCase(String correo);
    boolean existsByDocumento(String documento);
    List<Usuario> findAllByOrderByRolAscNombreAsc();
    List<Usuario> findByRolAndActivoTrue(Rol rol);
    List<Usuario> findByRolAndActivoTrueAndSedeId(Rol rol, Long sedeId);
    long countBySedeId(Long sedeId);
    List<Usuario> findBySedeIsNullAndRolNot(Rol rol);
    /** Coordinadores o monitores activos asignados a un laboratorio (para los avisos por correo). */
    @org.springframework.data.jpa.repository.Query("select u from Usuario u join u.laboratorios l where l.id = :lab and u.rol = :rol and u.activo = true")
    List<Usuario> asignados(@org.springframework.data.repository.query.Param("lab") Long laboratorioId,
                            @org.springframework.data.repository.query.Param("rol") Rol rol);
}
