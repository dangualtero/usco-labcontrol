package co.edu.usco.labcontrol.service;

import co.edu.usco.labcontrol.domain.Sede;
import co.edu.usco.labcontrol.repo.EquipoRepository;
import co.edu.usco.labcontrol.repo.SedeRepository;
import co.edu.usco.labcontrol.repo.UsuarioRepository;
import co.edu.usco.labcontrol.web.ApiException;
import co.edu.usco.labcontrol.web.Dtos.*;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Sedes de la Universidad: alta, edición y activación (solo el Coordinador general). */
@Service
public class SedeService {
    /** Segmentos que ya usa el frontend: una sede no puede llamarse así porque chocaría con esas rutas. */
    private static final Set<String> RESERVADOS = Set.of("login", "api", "images", "assets", "sedes", "coordinador",
            "auxiliar", "usuario", "admin", "favicon.ico");

    private final SedeRepository sedes;
    private final EquipoRepository equipos;
    private final UsuarioRepository usuarios;
    private final PresupuestoService presupuesto;
    private final Mapper mapper;

    public SedeService(SedeRepository sedes, EquipoRepository equipos, UsuarioRepository usuarios,
                       PresupuestoService presupuesto, Mapper mapper) {
        this.sedes = sedes; this.equipos = equipos; this.usuarios = usuarios; this.presupuesto = presupuesto; this.mapper = mapper;
    }

    /**
     * Lista pública (pantalla «Elige tu sede» y validación de la URL): nombre, ciudad y estado, sin datos sensibles.
     * Incluye las inactivas para que el Coordinador pueda seguir administrándolas; la pantalla pública solo muestra las activas.
     */
    @Transactional(readOnly = true)
    public List<SedeDto> publicas() {
        return sedes.findAllByOrderByIdAsc().stream().map(mapper::sede).toList();
    }

    @Transactional(readOnly = true)
    public List<SedeAdminDto> listarAdmin() {
        return sedes.findAllByOrderByIdAsc().stream().map(this::admin).toList();
    }

    @Transactional
    public SedeAdminDto crear(SedeRequest r) {
        Datos d = validar(r);
        String slug = slug(d.ciudad);
        if (slug.isEmpty() || RESERVADOS.contains(slug)) throw ApiException.badRequest("La ciudad no es válida para una dirección web");
        if (sedes.existsBySlug(slug)) throw ApiException.conflict("Ya existe una sede para " + d.ciudad + " (dirección /" + slug + ")");
        if (sedes.existsByNombreIgnoreCase(d.nombre)) throw ApiException.conflict("Ya existe una sede llamada «" + d.nombre + "»");
        Sede s = sedes.save(new Sede(slug, d.nombre, d.ciudad, d.direccion, mapper.ahora()));
        return admin(s);
    }

    /** Edita nombre y dirección. La ciudad (y su dirección web) no cambia, para no romper enlaces ya compartidos. */
    @Transactional
    public SedeAdminDto actualizar(String slug, SedeRequest r) {
        Sede s = sedes.findBySlug(slug).orElseThrow(() -> ApiException.notFound("La sede no existe"));
        Datos d = validar(new SedeRequest(r.nombre(), s.getCiudad(), r.direccion()));
        sedes.findAll().stream().filter(o -> !o.es(s) && o.getNombre().equalsIgnoreCase(d.nombre)).findAny()
                .ifPresent(o -> { throw ApiException.conflict("Ya existe una sede llamada «" + d.nombre + "»"); });
        s.actualizar(d.nombre, s.getCiudad(), d.direccion);
        return admin(s);
    }

    /** Desactivar oculta la sede de la lista pública e impide el ingreso de sus usuarios; no borra nada. */
    @Transactional
    public SedeAdminDto activar(String slug, boolean activa) {
        Sede s = sedes.findBySlug(slug).orElseThrow(() -> ApiException.notFound("La sede no existe"));
        if (!activa && sedes.findByActivaTrueOrderByIdAsc().size() <= 1 && s.isActiva()) {
            throw ApiException.conflict("Debe quedar al menos una sede activa");
        }
        s.setActiva(activa);
        return admin(s);
    }

    private SedeAdminDto admin(Sede s) {
        return new SedeAdminDto(mapper.sede(s), equipos.countBySedeIdAndActivoTrue(s.getId()), usuarios.countBySedeId(s.getId()),
                presupuesto.saldo(s));
    }

    private record Datos(String nombre, String ciudad, String direccion) {}

    private static Datos validar(SedeRequest r) {
        String ciudad = limpio(r.ciudad());
        if (ciudad == null || ciudad.length() < 3) throw ApiException.badRequest("Indica la ciudad o municipio de la sede (ej. Pitalito)");
        if (ciudad.length() > 60) throw ApiException.badRequest("El nombre de la ciudad es demasiado largo");
        String nombre = limpio(r.nombre());
        if (nombre == null) nombre = "Sede " + ciudad;
        if (nombre.length() < 4 || nombre.length() > 80) throw ApiException.badRequest("El nombre de la sede debe tener entre 4 y 80 caracteres");
        String dir = limpio(r.direccion());
        if (dir != null && dir.length() > 160) throw ApiException.badRequest("La dirección es demasiado larga");
        return new Datos(nombre, ciudad, dir);
    }

    /** «La Plata» → «la-plata», «Garzón» → «garzon». */
    static String slug(String texto) {
        return CatalogoService.normalizar(texto).replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
    }

    private static String limpio(String s) { return s == null || s.isBlank() ? null : s.trim().replaceAll("\\s+", " "); }
}
