package co.edu.usco.labcontrol.service;

import co.edu.usco.labcontrol.domain.Equipo;
import co.edu.usco.labcontrol.domain.Sede;
import co.edu.usco.labcontrol.domain.Usuario;
import co.edu.usco.labcontrol.repo.SedeRepository;
import co.edu.usco.labcontrol.repo.UsuarioRepository;
import co.edu.usco.labcontrol.web.ApiException;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * Resuelve en qué sede se está trabajando en cada petición.
 * <ul>
 *   <li>Estudiantes, docentes y auxiliares: SIEMPRE su propia sede (la de la base de datos). Lo que envíe el navegador
 *       se ignora, así nadie puede ver ni prestar equipos de otra sede manipulando la URL.</li>
 *   <li>Coordinador general: la sede que eligió en el frontend (/neiva, /pitalito…), enviada en la cabecera {@code X-Sede}.</li>
 *   <li>Sin petición HTTP (pruebas automáticas, tareas internas): la sede principal.</li>
 * </ul>
 */
@Component
public class SedeContexto {
    public static final String CABECERA = "X-Sede";

    private final SedeRepository sedes;
    private final UsuarioRepository usuarios;

    public SedeContexto(SedeRepository sedes, UsuarioRepository usuarios) { this.sedes = sedes; this.usuarios = usuarios; }

    public Sede de(Long usuarioId) {
        return de(usuarios.findById(usuarioId).orElseThrow(() -> ApiException.notFound("Usuario no encontrado")));
    }

    public Sede de(Usuario u) {
        if (u.getSede() != null) return u.getSede();
        String slug = cabecera();
        return slug == null ? principal() : porSlug(slug);
    }

    /** La primera sede creada (Neiva). */
    public Sede principal() {
        return sedes.findFirstByOrderByIdAsc().orElseThrow(() -> new IllegalStateException("No hay sedes registradas"));
    }

    public Sede porSlug(String slug) {
        String s = slug == null ? "" : slug.trim().toLowerCase();
        return sedes.findBySlug(s).orElseThrow(() -> ApiException.notFound("La sede «" + s + "» no existe"));
    }

    /** ¿El equipo pertenece a la sede? (un equipo sin sede, de datos anteriores, es de la sede principal). */
    public boolean pertenece(Equipo e, Sede sede) {
        return e.getSede() == null ? sede.es(principal()) : sede.es(e.getSede());
    }

    /** ¿Un evento del historial (por el slug guardado) pertenece a la sede? Los eventos sin sede son de la principal. */
    public boolean pertenece(String slugEvento, Sede sede) {
        return slugEvento == null ? sede.es(principal()) : slugEvento.equals(sede.getSlug());
    }

    private static String cabecera() {
        if (!(RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attrs)) return null;
        String v = attrs.getRequest().getHeader(CABECERA);
        return v == null || v.isBlank() ? null : v.trim().toLowerCase();
    }
}
