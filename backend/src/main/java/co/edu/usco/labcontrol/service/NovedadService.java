package co.edu.usco.labcontrol.service;

import co.edu.usco.labcontrol.domain.*;
import co.edu.usco.labcontrol.repo.EquipoRepository;
import co.edu.usco.labcontrol.repo.HistorialRepository;
import co.edu.usco.labcontrol.repo.ReporteNovedadRepository;
import co.edu.usco.labcontrol.repo.UsuarioRepository;
import co.edu.usco.labcontrol.web.ApiException;
import co.edu.usco.labcontrol.web.Dtos.*;
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
    /** Evento interno: «se registró la novedad X, avisa». */
    public record NovedadRegistrada(Long id) {}

    private final ReporteNovedadRepository reportes;
    private final EquipoRepository equipos;
    private final UsuarioRepository usuarios;
    private final HistorialRepository historial;
    private final SedeContexto sedes;
    private final Mapper mapper;
    private final ApplicationEventPublisher eventos;

    public NovedadService(ReporteNovedadRepository reportes, EquipoRepository equipos, UsuarioRepository usuarios,
                          HistorialRepository historial, SedeContexto sedes, Mapper mapper, ApplicationEventPublisher eventos) {
        this.reportes = reportes; this.equipos = equipos; this.usuarios = usuarios; this.historial = historial;
        this.sedes = sedes; this.mapper = mapper; this.eventos = eventos;
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

    /** El coordinador puede volver a disparar el aviso (por ejemplo si n8n estaba apagado). */
    @Transactional
    public NovedadDto reenviar(Long actorId, Long id) {
        Sede sede = sedes.de(actorId);
        ReporteNovedad n = reportes.findById(id).filter(x -> x.getSede().es(sede))
                .orElseThrow(() -> ApiException.notFound("La novedad no existe en esta sede"));
        if (n.getNotificacion() == ReporteNovedad.Notificacion.PENDIENTE) throw ApiException.conflict("El aviso se está enviando en este momento");
        n.reintentando();
        eventos.publishEvent(new NovedadRegistrada(n.getId()));
        return dto(n);
    }

    static NovedadDto dto(ReporteNovedad n) {
        Equipo e = n.getEquipo();
        return new NovedadDto(n.getId(), n.getSede().getSlug(), e.getPlaca(), e.getNombre(), e.getLaboratorio(), n.getTipo().name(),
                n.getTipo().texto(), n.getDescripcion(), n.getReportadoPor(), n.getCreadoEn(), n.isEnMantenimiento(), n.getPrioridad(),
                n.getAsuntoCorreo(), n.getResumenIa(), n.getAccionSugerida(), n.getNotificacion().name(), n.getNotificacionDetalle(),
                n.getDestinatario(), n.getNotificadoEn());
    }
}
