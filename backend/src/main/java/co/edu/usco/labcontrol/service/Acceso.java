package co.edu.usco.labcontrol.service;

import co.edu.usco.labcontrol.domain.*;
import co.edu.usco.labcontrol.repo.EquipoRepository;
import co.edu.usco.labcontrol.repo.LaboratorioRepository;
import co.edu.usco.labcontrol.repo.UsuarioRepository;
import co.edu.usco.labcontrol.web.ApiException;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * Control de acceso en un solo lugar: quién es el usuario, qué permisos tiene y qué laboratorios puede ver.
 * <ul>
 *   <li>ADMINISTRADOR: todos los laboratorios de la sede abierta (y todas las sedes).</li>
 *   <li>COORDINADOR_LAB y MONITOR: solo sus laboratorios asignados (activos).</li>
 * </ul>
 */
@Component
public class Acceso {
    private final UsuarioRepository usuarios;
    private final LaboratorioRepository laboratorios;
    private final EquipoRepository equipos;
    private final PermisoService permisos;
    private final SedeContexto sedes;

    public Acceso(UsuarioRepository usuarios, LaboratorioRepository laboratorios, EquipoRepository equipos, PermisoService permisos,
                  SedeContexto sedes) {
        this.usuarios = usuarios; this.laboratorios = laboratorios; this.equipos = equipos; this.permisos = permisos; this.sedes = sedes;
    }

    public Usuario usuario(Long id) {
        return usuarios.findById(id == null ? -1L : id).filter(Usuario::isActivo)
                .orElseThrow(() -> new ApiException(org.springframework.http.HttpStatus.UNAUTHORIZED, "Sesión no válida o expirada"));
    }

    public boolean puede(Usuario u, Permiso p) { return permisos.tiene(u, p); }

    /** Atajo para los controladores: exige el permiso a la cuenta de la sesión. */
    public Usuario exigir(Long usuarioId, Permiso p) {
        Usuario u = usuario(usuarioId);
        exigir(u, p);
        return u;
    }

    public void exigir(Usuario u, Permiso p) {
        if (!puede(u, p)) throw ApiException.forbidden("Tu rol no tiene permiso para: " + p.descripcion().toLowerCase(Locale.ROOT));
    }

    /** Exige al menos uno de los permisos. */
    public void exigirAlguno(Usuario u, Permiso... ps) {
        for (Permiso p : ps) if (puede(u, p)) return;
        exigir(u, ps[0]);
    }

    public Sede sede(Usuario u) { return sedes.de(u); }

    /** Laboratorios que la cuenta puede ver en la sede abierta. */
    public List<Laboratorio> laboratorios(Usuario u) {
        Sede sede = sede(u);
        if (u.esAdministrador()) return laboratorios.findBySedeIdOrderByNombreAsc(sede.getId());
        return u.getLaboratorios().stream().filter(Laboratorio::isActivo).filter(l -> l.getSede().es(sede))
                .sorted(Comparator.comparing(Laboratorio::getNombre)).toList();
    }

    /** Nombres (en minúscula) de los laboratorios visibles, para filtrar equipos por su campo «laboratorio». */
    public Set<String> nombres(Usuario u) {
        return laboratorios(u).stream().map(l -> l.getNombre().toLowerCase(Locale.ROOT)).collect(Collectors.toSet());
    }

    public boolean ve(Usuario u, Laboratorio l) {
        if (l == null) return false;
        if (!l.getSede().es(sede(u))) return false;
        return u.esAdministrador() || u.getLaboratorios().stream().anyMatch(x -> x.getId().equals(l.getId()) && x.isActivo());
    }

    public boolean ve(Usuario u, Equipo e) {
        if (e == null || !sedes.pertenece(e, sede(u))) return false;
        return u.esAdministrador() || nombres(u).contains(e.getLaboratorio() == null ? "" : e.getLaboratorio().toLowerCase(Locale.ROOT));
    }

    public Laboratorio laboratorio(Usuario u, Long id) {
        Laboratorio l = laboratorios.findById(id == null ? -1L : id).orElse(null);
        if (!ve(u, l)) throw ApiException.notFound("El laboratorio no existe o no está asignado a tu cuenta");
        return l;
    }

    /** Laboratorio por nombre dentro de la sede abierta. */
    public Laboratorio laboratorioPorNombre(Usuario u, String nombre) {
        return laboratorios.findBySedeIdAndNombreIgnoreCase(sede(u).getId(), nombre == null ? "" : nombre.trim()).orElse(null);
    }

    /** Id del laboratorio con ese nombre en la sede (null si no existe). */
    public Long laboratorioEnSede(Sede sede, String nombre) {
        return laboratorios.findBySedeIdAndNombreIgnoreCase(sede.getId(), nombre == null ? "" : nombre.trim()).map(Laboratorio::getId).orElse(null);
    }

    /** Equipo activo visible para la cuenta. */
    public Equipo equipo(Usuario u, String placa) {
        Equipo e = equipos.findByPlacaAndActivoTrue(placa == null ? "" : placa.trim().toUpperCase()).orElse(null);
        if (!ve(u, e)) throw ApiException.notFound("El elemento no existe en el inventario de tus laboratorios");
        return e;
    }

    /** Igual que {@link #equipo} pero con bloqueo de escritura (para cambios de existencias o estado). */
    public Equipo equipoParaEditar(Usuario u, String placa) {
        Equipo e = equipos.findActivoForUpdate(placa == null ? "" : placa.trim().toUpperCase()).orElse(null);
        if (!ve(u, e)) throw ApiException.notFound("El elemento no existe en el inventario de tus laboratorios");
        return e;
    }
}
