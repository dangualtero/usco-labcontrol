package co.edu.usco.labcontrol.service;

import co.edu.usco.labcontrol.domain.Permiso;
import co.edu.usco.labcontrol.domain.Rol;
import co.edu.usco.labcontrol.domain.Usuario;
import co.edu.usco.labcontrol.web.ApiException;
import co.edu.usco.labcontrol.web.Dtos.PermisoCambioRequest;
import co.edu.usco.labcontrol.web.Dtos.PermisoDto;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Permisos por rol (tabla permisos_rol). Si no hay fila para un permiso, vale el de la hoja de roles
 * ({@link Permiso#porDefecto}). El Administrador siempre tiene todos; los permisos «solo del Administrador» no se
 * pueden dar a otros roles.
 */
@Service
public class PermisoService {
    private final JdbcTemplate jdbc;
    private final Map<Rol, Map<Permiso, Boolean>> cache = new ConcurrentHashMap<>();

    public PermisoService(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public boolean tiene(Usuario u, Permiso p) {
        if (u == null || !u.isActivo()) return false;
        if (u.esAdministrador()) return true;
        if (p.soloAdministrador()) return false;
        return matriz(u.getRol()).getOrDefault(p, p.porDefecto(u.getRol()));
    }

    public List<String> de(Usuario u) {
        return Arrays.stream(Permiso.values()).filter(p -> tiene(u, p)).map(Enum::name).toList();
    }

    public List<PermisoDto> listar() {
        Map<Permiso, Boolean> coord = matriz(Rol.COORDINADOR_LAB), mon = matriz(Rol.MONITOR);
        return Arrays.stream(Permiso.values()).map(p -> new PermisoDto(p.name(), p.modulo().name(), p.modulo().texto(), p.descripcion(),
                p.soloAdministrador(),
                !p.soloAdministrador() && coord.getOrDefault(p, p.porDefecto(Rol.COORDINADOR_LAB)),
                !p.soloAdministrador() && mon.getOrDefault(p, p.porDefecto(Rol.MONITOR)),
                p.porDefecto(Rol.COORDINADOR_LAB), p.porDefecto(Rol.MONITOR))).toList();
    }

    @Transactional
    public List<PermisoDto> cambiar(PermisoCambioRequest r) {
        Rol rol = Rol.de(r.rol());
        if (rol == null || rol == Rol.ADMINISTRADOR) throw ApiException.badRequest("Solo se configuran los permisos de COORDINADOR_LAB y MONITOR");
        Permiso p;
        try { p = Permiso.valueOf(r.permiso() == null ? "" : r.permiso().trim().toUpperCase()); }
        catch (IllegalArgumentException e) { throw ApiException.badRequest("Permiso no válido"); }
        if (p.soloAdministrador()) throw ApiException.badRequest("«" + p.descripcion() + "» es exclusivo del Administrador");
        int n = jdbc.update("UPDATE permisos_rol SET habilitado = ? WHERE rol = ? AND permiso = ?", r.habilitado(), rol.name(), p.name());
        if (n == 0) jdbc.update("INSERT INTO permisos_rol (rol, permiso, habilitado) VALUES (?, ?, ?)", rol.name(), p.name(), r.habilitado());
        cache.remove(rol);
        return listar();
    }

    /** Vuelve a los permisos de la hoja de roles. */
    @Transactional
    public List<PermisoDto> restablecer() {
        jdbc.update("DELETE FROM permisos_rol");
        cache.clear();
        return listar();
    }

    private Map<Permiso, Boolean> matriz(Rol rol) {
        return cache.computeIfAbsent(rol, r -> {
            Map<Permiso, Boolean> m = new EnumMap<>(Permiso.class);
            jdbc.query("SELECT permiso, habilitado FROM permisos_rol WHERE rol = ?", rs -> {
                try { m.put(Permiso.valueOf(rs.getString(1)), rs.getBoolean(2)); } catch (IllegalArgumentException ignorado) { /* permiso retirado */ }
            }, r.name());
            return m;
        });
    }
}
