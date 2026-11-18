package co.edu.usco.labcontrol.service;

import co.edu.usco.labcontrol.domain.*;
import co.edu.usco.labcontrol.repo.*;
import co.edu.usco.labcontrol.web.ApiException;
import co.edu.usco.labcontrol.web.Dtos.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/**
 * Reportes y seguimiento: compromisos de los laboratorios, avances con evidencias y su verificación.
 * <ul>
 *   <li>COMPROMISOS_GESTIONAR: crear y actualizar compromisos (coordinador en sus laboratorios; Administrador en toda la sede).</li>
 *   <li>AVANCES_REGISTRAR: registrar avances con evidencias (coordinador y monitor).</li>
 *   <li>AVANCES_VERIFICAR: verificar u observar los avances (Administrador).</li>
 * </ul>
 */
@Service
public class SeguimientoService {
    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("d 'de' MMMM 'de' yyyy", Locale.forLanguageTag("es-CO"));
    private static final int MAX_EVIDENCIAS = 5;

    private final CompromisoRepository compromisos;
    private final AvanceRepository avances;
    private final SolicitudRepository solicitudes;
    private final UsuarioRepository usuarios;
    private final ArchivoService archivos;
    private final NotificacionService notificaciones;
    private final Acceso acceso;
    private final Mapper mapper;

    public SeguimientoService(CompromisoRepository compromisos, AvanceRepository avances, SolicitudRepository solicitudes,
                              UsuarioRepository usuarios, ArchivoService archivos, NotificacionService notificaciones,
                              Acceso acceso, Mapper mapper) {
        this.compromisos = compromisos; this.avances = avances; this.solicitudes = solicitudes; this.usuarios = usuarios;
        this.archivos = archivos; this.notificaciones = notificaciones; this.acceso = acceso; this.mapper = mapper;
    }

    // ------------------------------------------------------------------ Compromisos

    @Transactional(readOnly = true)
    public List<CompromisoDto> compromisos(Long actorId, String estado, Long laboratorioId) {
        Usuario actor = acceso.usuario(actorId);
        acceso.exigirAlguno(actor, Permiso.REPORTES_GENERAR, Permiso.AVANCES_REGISTRAR, Permiso.COMPROMISOS_GESTIONAR);
        LocalDate hoy = mapper.hoy();
        return compromisos.findBySedeIdOrderByFechaLimiteAsc(acceso.sede(actor).getId()).stream()
                // Los compromisos institucionales (de toda la sede) son del Administrador
                .filter(c -> c.getLaboratorio() == null ? actor.esAdministrador() : acceso.ve(actor, c.getLaboratorio()))
                .filter(c -> laboratorioId == null || (c.getLaboratorio() != null && c.getLaboratorio().getId().equals(laboratorioId)))
                .filter(c -> Texto.vacio(estado) || ("VENCIDOS".equalsIgnoreCase(estado) ? c.vencido(hoy)
                        : "ABIERTOS".equalsIgnoreCase(estado) ? c.getEstado() == Compromiso.Estado.PENDIENTE || c.getEstado() == Compromiso.Estado.EN_CURSO
                        : c.getEstado().name().equalsIgnoreCase(estado.trim())))
                .map(this::compromiso).toList();
    }

    @Transactional
    public CompromisoDto crearCompromiso(Long actorId, CompromisoRequest r) {
        Usuario actor = acceso.usuario(actorId);
        acceso.exigir(actor, Permiso.COMPROMISOS_GESTIONAR);
        Laboratorio lab = laboratorioDe(actor, r.laboratorioId());
        Datos d = datos(actor, r, lab);
        if (d.fecha.isBefore(mapper.hoy())) throw ApiException.badRequest("La fecha límite no puede estar en el pasado");
        Compromiso c = compromisos.save(new Compromiso(acceso.sede(actor), lab, d.titulo, d.descripcion, d.responsable, d.fecha,
                actor.getNombre(), mapper.ahora()));
        avisarCompromiso(c, actor);
        return compromiso(c);
    }

    @Transactional
    public CompromisoDto actualizarCompromiso(Long actorId, Long id, CompromisoRequest r) {
        Usuario actor = acceso.usuario(actorId);
        acceso.exigir(actor, Permiso.COMPROMISOS_GESTIONAR);
        Compromiso c = editable(actor, id);
        Datos d = datos(actor, r, c.getLaboratorio());
        Usuario antes = c.getResponsable();
        c.actualizar(d.titulo, d.descripcion, d.responsable, d.fecha, mapper.ahora());
        if (d.responsable != null && (antes == null || !antes.getId().equals(d.responsable.getId()))) avisarCompromiso(c, actor);
        return compromiso(c);
    }

    @Transactional
    public CompromisoDto estadoCompromiso(Long actorId, Long id, EstadoRequest r) {
        Usuario actor = acceso.usuario(actorId);
        acceso.exigir(actor, Permiso.COMPROMISOS_GESTIONAR);
        Compromiso c = editable(actor, id);
        Compromiso.Estado e;
        try { e = Compromiso.Estado.valueOf(r.estado() == null ? "" : r.estado().trim().toUpperCase()); }
        catch (IllegalArgumentException ex) { throw ApiException.badRequest("Estado no válido: PENDIENTE, EN_CURSO, CUMPLIDO o CANCELADO"); }
        c.cambiarEstado(e, mapper.ahora());
        return compromiso(c);
    }

    private Compromiso editable(Usuario actor, Long id) {
        Compromiso c = compromisos.findById(id == null ? -1L : id).filter(x -> x.getSede().es(acceso.sede(actor))).orElse(null);
        if (c == null) throw ApiException.notFound("El compromiso no existe");
        if (c.getLaboratorio() == null ? !actor.esAdministrador() : !acceso.ve(actor, c.getLaboratorio())) {
            throw ApiException.forbidden("Solo el Administrador modifica los compromisos institucionales");
        }
        return c;
    }

    private Laboratorio laboratorioDe(Usuario actor, Long id) {
        if (id == null) {
            if (!actor.esAdministrador()) throw ApiException.badRequest("Selecciona el laboratorio del compromiso");
            return null;
        }
        return acceso.laboratorio(actor, id);
    }

    private record Datos(String titulo, String descripcion, Usuario responsable, LocalDate fecha) {}

    private Datos datos(Usuario actor, CompromisoRequest r, Laboratorio lab) {
        String titulo = Texto.obligatorio(r.titulo(), 5, 160, "El título");
        String desc = Texto.opcional(r.descripcion(), 2000, "La descripción");
        if (r.fechaLimite() == null) throw ApiException.badRequest("Indica la fecha límite");
        Usuario resp = null;
        if (r.responsableId() != null) {
            resp = usuarios.findById(r.responsableId()).filter(Usuario::isActivo)
                    .orElseThrow(() -> ApiException.badRequest("El responsable no existe o está inactivo"));
            boolean valido = resp.esAdministrador()
                    || (lab != null && resp.getLaboratorios().stream().anyMatch(l -> l.getId().equals(lab.getId())))
                    || (lab == null && resp.getSede() != null && resp.getSede().es(acceso.sede(actor)));
            if (!valido) throw ApiException.badRequest("El responsable debe estar asignado al laboratorio del compromiso");
        }
        return new Datos(titulo, desc, resp, r.fechaLimite());
    }

    private void avisarCompromiso(Compromiso c, Usuario actor) {
        if (c.getResponsable() == null || c.getResponsable().getId().equals(actor.getId())) return;
        notificaciones.avisar("COMPROMISO_ASIGNADO", NotificacionService.correos(c.getResponsable()), Map.of(
                "titulo", c.getTitulo(), "laboratorio", c.getLaboratorio() == null ? "Toda la sede" : c.getLaboratorio().getNombre(),
                "fecha", FECHA.format(c.getFechaLimite()), "descripcion", c.getDescripcion() == null ? "" : c.getDescripcion(),
                "responsable", c.getResponsable().getNombre(), "enlace", notificaciones.enlace(c.getSede().getSlug(), "/seguimiento")));
    }

    /** Personas que pueden ser responsables de un compromiso del laboratorio (o de la sede). */
    @Transactional(readOnly = true)
    public List<PersonaDto> personas(Long actorId, Long laboratorioId) {
        Usuario actor = acceso.usuario(actorId);
        acceso.exigir(actor, Permiso.COMPROMISOS_GESTIONAR);
        Map<Long, PersonaDto> out = new LinkedHashMap<>();
        if (laboratorioId != null) {
            Laboratorio lab = acceso.laboratorio(actor, laboratorioId);
            for (Rol rol : List.of(Rol.COORDINADOR_LAB, Rol.MONITOR)) {
                usuarios.asignados(lab.getId(), rol).forEach(u -> out.put(u.getId(), new PersonaDto(u.getId(), u.getNombre(), u.getRol().texto())));
            }
        } else {
            Sede sede = acceso.sede(actor);
            usuarios.findAll().stream().filter(Usuario::isActivo).filter(u -> !u.esAdministrador() && u.getSede() != null && u.getSede().es(sede))
                    .forEach(u -> out.put(u.getId(), new PersonaDto(u.getId(), u.getNombre(), u.getRol().texto())));
        }
        usuarios.findByRolAndActivoTrue(Rol.ADMINISTRADOR).forEach(u -> out.put(u.getId(), new PersonaDto(u.getId(), u.getNombre(), u.getRol().texto())));
        return out.values().stream().sorted(Comparator.comparing(PersonaDto::nombre)).toList();
    }

    CompromisoDto compromiso(Compromiso c) {
        List<Avance> avs = c.getId() == null ? List.of() : avances.findByCompromisoIdOrderByCreadoEnDesc(c.getId());
        Integer ultimo = avs.stream().map(Avance::getPorcentaje).filter(p -> p != null).findFirst().orElse(null);
        return new CompromisoDto(c.getId(), c.getLaboratorio() == null ? null : c.getLaboratorio().getId(),
                c.getLaboratorio() == null ? "Toda la sede" : c.getLaboratorio().getNombre(), c.getTitulo(), c.getDescripcion(),
                c.getResponsable() == null ? null : c.getResponsable().getId(), c.getResponsable() == null ? null : c.getResponsable().getNombre(),
                c.getFechaLimite(), c.getEstado().name(), c.getEstado().texto(), c.vencido(mapper.hoy()), avs.size(), ultimo,
                c.getCreadoPor(), c.getCreadoEn());
    }

    // ------------------------------------------------------------------ Avances

    /** verificacion: PENDIENTE, VERIFICADO, OBSERVADO o vacío. */
    @Transactional(readOnly = true)
    public List<AvanceDto> avances(Long actorId, Long laboratorioId, Long compromisoId, Long solicitudId, String verificacion) {
        Usuario actor = acceso.usuario(actorId);
        acceso.exigirAlguno(actor, Permiso.REPORTES_GENERAR, Permiso.AVANCES_REGISTRAR, Permiso.AVANCES_VERIFICAR);
        return avances.findTop500BySedeIdOrderByCreadoEnDesc(acceso.sede(actor).getId()).stream()
                .filter(a -> acceso.ve(actor, a.getLaboratorio()))
                .filter(a -> laboratorioId == null || a.getLaboratorio().getId().equals(laboratorioId))
                .filter(a -> compromisoId == null || (a.getCompromiso() != null && a.getCompromiso().getId().equals(compromisoId)))
                .filter(a -> solicitudId == null || (a.getSolicitud() != null && a.getSolicitud().getId().equals(solicitudId)))
                .filter(a -> Texto.vacio(verificacion) || a.getVerificacion().name().equalsIgnoreCase(verificacion.trim()))
                .map(mapper::avance).toList();
    }

    @Transactional
    public AvanceDto registrarAvance(Long actorId, AvanceRequest r, List<MultipartFile> evidencias) {
        Usuario actor = acceso.usuario(actorId);
        acceso.exigir(actor, Permiso.AVANCES_REGISTRAR);
        Compromiso comp = null;
        Solicitud sol = null;
        Laboratorio lab = r.laboratorioId() == null ? null : acceso.laboratorio(actor, r.laboratorioId());
        if (r.compromisoId() != null) {
            comp = compromisos.findById(r.compromisoId()).filter(c -> c.getSede().es(acceso.sede(actor)))
                    .filter(c -> c.getLaboratorio() == null ? actor.esAdministrador() : acceso.ve(actor, c.getLaboratorio()))
                    .orElseThrow(() -> ApiException.notFound("El compromiso no existe"));
            if (comp.getEstado() == Compromiso.Estado.CUMPLIDO || comp.getEstado() == Compromiso.Estado.CANCELADO) {
                throw ApiException.conflict("El compromiso ya está " + comp.getEstado().texto().toLowerCase());
            }
            if (comp.getLaboratorio() != null) {
                if (lab != null && !lab.getId().equals(comp.getLaboratorio().getId())) throw ApiException.badRequest("El compromiso es de otro laboratorio");
                lab = comp.getLaboratorio();
            }
        }
        if (r.solicitudId() != null) {
            sol = solicitudes.findById(r.solicitudId()).filter(s -> acceso.ve(actor, s.getLaboratorio()))
                    .orElseThrow(() -> ApiException.notFound("La solicitud no existe en tus laboratorios"));
            if (sol.getEstado() != Solicitud.Estado.APROBADA) throw ApiException.conflict("Solo se registran avances de solicitudes aprobadas");
            if (lab != null && !lab.getId().equals(sol.getLaboratorio().getId())) throw ApiException.badRequest("La solicitud es de otro laboratorio");
            lab = sol.getLaboratorio();
        }
        if (lab == null) throw ApiException.badRequest("Selecciona el laboratorio del avance");
        String desc = Texto.obligatorio(r.descripcion(), 10, 2000, "La descripción del avance");
        Integer pct = r.porcentaje();
        if (pct != null && (pct < 0 || pct > 100)) throw ApiException.badRequest("El porcentaje debe estar entre 0 y 100");
        List<MultipartFile> fs = evidencias == null ? List.of() : evidencias.stream().filter(f -> f != null && !f.isEmpty()).toList();
        if (fs.size() > MAX_EVIDENCIAS) throw ApiException.badRequest("Puedes adjuntar máximo " + MAX_EVIDENCIAS + " evidencias por avance");
        LocalDateTime ahora = mapper.ahora();
        Avance a = new Avance(lab.getSede(), lab, comp, sol, desc, pct, actor.getNombre(), ahora);
        List<ArchivoInfo> guardados = new ArrayList<>();
        for (MultipartFile f : fs) guardados.add(archivos.guardar(actor, f));
        a.getEvidencias().addAll(guardados);
        avances.save(a);
        if (comp != null && comp.getEstado() == Compromiso.Estado.PENDIENTE) comp.cambiarEstado(Compromiso.Estado.EN_CURSO, ahora);
        return mapper.avance(a);
    }

    @Transactional
    public AvanceDto verificar(Long actorId, Long id, VerificarRequest r) {
        Usuario actor = acceso.usuario(actorId);
        acceso.exigir(actor, Permiso.AVANCES_VERIFICAR);
        Avance a = avances.findById(id == null ? -1L : id).filter(x -> acceso.ve(actor, x.getLaboratorio()))
                .orElseThrow(() -> ApiException.notFound("El avance no existe"));
        Avance.Verificacion v;
        try { v = Avance.Verificacion.valueOf(r.resultado() == null ? "" : r.resultado().trim().toUpperCase()); }
        catch (IllegalArgumentException e) { v = null; }
        if (v != Avance.Verificacion.VERIFICADO && v != Avance.Verificacion.OBSERVADO) throw ApiException.badRequest("Resultado no válido: VERIFICADO u OBSERVADO");
        String obs = v == Avance.Verificacion.OBSERVADO ? Texto.obligatorio(r.observacion(), 5, 1000, "La observación")
                : Texto.opcional(r.observacion(), 1000, "La observación");
        a.verificar(v, actor.getNombre(), obs, mapper.ahora());
        return mapper.avance(a);
    }
}
