package co.edu.usco.labcontrol.service;

import co.edu.usco.labcontrol.domain.*;
import co.edu.usco.labcontrol.repo.EquipoRepository;
import co.edu.usco.labcontrol.repo.HistorialRepository;
import co.edu.usco.labcontrol.repo.ReporteNovedadRepository;
import co.edu.usco.labcontrol.repo.SeguimientoNovedadRepository;
import co.edu.usco.labcontrol.repo.UsuarioRepository;
import co.edu.usco.labcontrol.web.ApiException;
import co.edu.usco.labcontrol.web.Dtos.*;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Reportes de novedades de los auxiliares (equipo agotado, dañado, perdido, con fallas…).
 * Guardar el reporte es inmediato; el aviso al correo institucional se envía después, en segundo plano
 * ({@link NotificadorNovedades}), cuando la transacción ya quedó confirmada en la base de datos.
 */
@Service
public class NovedadService {
    /** Evento interno: «se registró la novedad X, avisa». forzar = enviar el correo ya, aunque no sea prioridad ALTA. */
    public record NovedadRegistrada(Long id, boolean forzar) {
        public NovedadRegistrada(Long id) { this(id, false); }
    }

    private final ReporteNovedadRepository reportes;
    private final EquipoRepository equipos;
    private final UsuarioRepository usuarios;
    private final HistorialRepository historial;
    private final SedeContexto sedes;
    private final Mapper mapper;
    private final ApplicationEventPublisher eventos;
    private final SeguimientoNovedadRepository seguimientos;

    public NovedadService(ReporteNovedadRepository reportes, EquipoRepository equipos, UsuarioRepository usuarios,
                          HistorialRepository historial, SedeContexto sedes, Mapper mapper, ApplicationEventPublisher eventos,
                          SeguimientoNovedadRepository seguimientos) {
        this.reportes = reportes; this.equipos = equipos; this.usuarios = usuarios; this.historial = historial;
        this.sedes = sedes; this.mapper = mapper; this.eventos = eventos; this.seguimientos = seguimientos;
    }

    @Transactional
    public NovedadDto reportar(Long actorId, String placa, NovedadRequest r) {
        Usuario actor = usuarios.findById(actorId).orElseThrow();
        Sede sede = sedes.de(actor);
        Equipo e = equipos.findActivoForUpdate(placa == null ? "" : placa.trim().toUpperCase())
                .filter(x -> sedes.pertenece(x, sede))
                .orElseThrow(() -> ApiException.notFound("El equipo no existe en el inventario activo de la " + sede.getNombre()));
        ReporteNovedad.Tipo tipo;
        try { tipo = ReporteNovedad.Tipo.valueOf(r.tipo() == null ? "" : r.tipo().trim().toUpperCase()); }
        catch (IllegalArgumentException ex) { throw ApiException.badRequest("Tipo de novedad no válido: AGOTADO, DANADO, PERDIDO, FALLA u OTRO"); }
        String desc = r.descripcion() == null ? "" : r.descripcion().trim().replaceAll("\\s+", " ");
        if (desc.length() < 10) throw ApiException.badRequest("Describe qué pasó (mínimo 10 caracteres)");
        if (desc.length() > 1000) throw ApiException.badRequest("La descripción no puede superar 1000 caracteres");
        if (reportes.existsByEquipoIdAndTipoAndReportadoPorAndCreadoEnAfter(e.getId(), tipo, actor.getNombre(), mapper.ahora().minusMinutes(10))) {
            throw ApiException.conflict("Ya reportaste esta novedad para " + e.getPlaca() + " hace menos de 10 minutos");
        }

        // Opcional: retirar el equipo del préstamo mientras se revisa (no aplica si está prestado ahora mismo).
        boolean mantenimiento = Boolean.TRUE.equals(r.mantenimiento()) && e.getEstado() == EstadoEquipo.DISPONIBLE;
        if (mantenimiento) {
            e.setEstado(EstadoEquipo.MANTENIMIENTO);
            historial.save(new HistorialEvento(mapper.ahora(), "ESTADO", e, null, null, actor.getNombre(),
                    "Enviado a mantenimiento por novedad: " + tipo.texto()));
        }
        historial.save(new HistorialEvento(mapper.ahora(), "NOVEDAD", e, null, null, actor.getNombre(), tipo.texto() + ": " + desc));
        ReporteNovedad n = reportes.save(new ReporteNovedad(sede, e, tipo, desc, actor.getNombre(), actor.getCorreo(), mapper.ahora(), mantenimiento));
        eventos.publishEvent(new NovedadRegistrada(n.getId()));
        return dto(n);
    }

    /** Novedades de la sede (auxiliar: la suya; coordinador: la abierta en el selector). */
    @Transactional(readOnly = true)
    public List<NovedadDto> listar(Long actorId) {
        return reportes.findTop50BySedeIdOrderByCreadoEnDesc(sedes.de(actorId).getId()).stream().map(NovedadService::dto).toList();
    }

    /** Vista «Reportes»: todos los reportes de la sede (los 500 más recientes). */
    @Transactional(readOnly = true)
    public List<NovedadDto> todos(Long actorId) {
        return reportes.findTop500BySedeIdOrderByCreadoEnDesc(sedes.de(actorId).getId()).stream().map(NovedadService::dto).toList();
    }

    /** Huella para el «tiempo real»: el navegador la consulta cada pocos segundos y solo recarga la lista si cambió. */
    @Transactional(readOnly = true)
    public VersionReportesDto version(Long actorId) {
        var filas = reportes.huella(sedes.de(actorId).getId());
        Object[] f = filas.isEmpty() ? new Object[0] : filas.get(0);
        long total = f.length > 0 && f[0] instanceof Number n ? n.longValue() : 0;
        StringBuilder sb = new StringBuilder();
        for (Object o : f) sb.append(o).append('|');
        return new VersionReportesDto(Integer.toHexString(sb.toString().hashCode()), total);
    }

    /**
     * El coordinador puede volver a disparar el aviso (por ejemplo si n8n estaba apagado).
     * ahora = true: envía el correo de inmediato aunque la prioridad no sea ALTA.
     */
    @Transactional
    public NovedadDto reenviar(Long actorId, Long id) { return reenviar(actorId, id, false); }

    @Transactional
    public NovedadDto reenviar(Long actorId, Long id, boolean ahora) {
        ReporteNovedad n = buscar(sedes.de(actorId), id);
        if (n.getNotificacion() == ReporteNovedad.Notificacion.PENDIENTE) throw ApiException.conflict("El aviso se está enviando en este momento");
        n.reintentando();
        eventos.publishEvent(new NovedadRegistrada(n.getId(), ahora));
        return dto(n);
    }

    // ------------------------------------------------------------------ Seguimiento

    @Transactional(readOnly = true)
    public List<SeguimientoDto> seguimiento(Long actorId, Long id) {
        ReporteNovedad n = buscar(sedes.de(actorId), id);
        return seguimientos.findByReporteIdOrderByFechaAsc(n.getId()).stream()
                .map(s -> new SeguimientoDto(s.getId(), s.getFecha(), s.getAutor(),
                        s.getEstadoAnterior() == null ? null : s.getEstadoAnterior().name(), s.getEstadoNuevo().name(), s.getComentario()))
                .toList();
    }

    /** Agrega un comentario al seguimiento y, si se indica, cambia el estado (Abierto → En proceso → Resuelto). */
    @Transactional
    public NovedadDto seguir(Long actorId, Long id, SeguimientoRequest r) {
        Usuario actor = usuarios.findById(actorId).orElseThrow();
        ReporteNovedad n = buscar(sedes.de(actor), id);
        ReporteNovedad.Estado nuevo = n.getEstado();
        if (r.estado() != null && !r.estado().isBlank()) {
            try { nuevo = ReporteNovedad.Estado.valueOf(r.estado().trim().toUpperCase()); }
            catch (IllegalArgumentException e) { throw ApiException.badRequest("Estado no válido: ABIERTO, EN_PROCESO o RESUELTO"); }
        }
        String comentario = r.comentario() == null ? "" : r.comentario().trim().replaceAll("\\s+", " ");
        if (comentario.length() < 3) throw ApiException.badRequest("Escribe un comentario para el seguimiento (mínimo 3 caracteres)");
        if (comentario.length() > 600) throw ApiException.badRequest("El comentario admite máximo 600 caracteres");
        ReporteNovedad.Estado anterior = n.getEstado();
        LocalDateTime ahora = mapper.ahora();
        n.cambiarEstado(nuevo, ahora);
        seguimientos.save(new SeguimientoNovedad(n, ahora, actor.getNombre(), anterior, nuevo, comentario));
        String det = "Reporte #" + n.getId() + (anterior == nuevo ? "" : ": " + anterior.texto() + " → " + nuevo.texto()) + ". " + comentario;
        historial.save(new HistorialEvento(ahora, "SEGUIMIENTO", n.getEquipo(), null, null, actor.getNombre(),
                det.length() > 600 ? det.substring(0, 599) + "…" : det));
        return dto(n);
    }

    private ReporteNovedad buscar(Sede sede, Long id) {
        return reportes.findById(id == null ? -1L : id).filter(x -> x.getSede().es(sede))
                .orElseThrow(() -> ApiException.notFound("La novedad no existe en esta sede"));
    }

    static NovedadDto dto(ReporteNovedad n) {
        Equipo e = n.getEquipo();
        return new NovedadDto(n.getId(), n.getSede().getSlug(), e.getPlaca(), e.getNombre(), e.getLaboratorio(), n.getTipo().name(),
                n.getTipo().texto(), n.getDescripcion(), n.getReportadoPor(), n.getCreadoEn(), n.isEnMantenimiento(), n.getPrioridad(),
                n.getAsuntoCorreo(), n.getResumenIa(), n.getAccionSugerida(), n.getNotificacion().name(), n.getNotificacionDetalle(),
                n.getDestinatario(), n.getNotificadoEn(), n.getEstado().name(), n.getEstado().texto(), n.getActualizadoEn(),
                e.getTipo().name());
    }
}
