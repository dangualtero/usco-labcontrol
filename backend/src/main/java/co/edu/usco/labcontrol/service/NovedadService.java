package co.edu.usco.labcontrol.service;

import co.edu.usco.labcontrol.domain.*;
import co.edu.usco.labcontrol.repo.HistorialRepository;
import co.edu.usco.labcontrol.repo.ReporteNovedadRepository;
import co.edu.usco.labcontrol.repo.SeguimientoNovedadRepository;
import co.edu.usco.labcontrol.web.ApiException;
import co.edu.usco.labcontrol.web.Dtos.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/**
 * Novedades del inventario: fallas, daños, pérdidas, insumos agotados, conteos de existencias y consumos.
 * <ol>
 *   <li><b>Registro preliminar</b> (NOVEDAD_REPORTAR): el monitor la reporta con fotografías, observaciones y soportes.
 *       Queda «Por validar» y se avisa al coordinador del laboratorio.</li>
 *   <li><b>Validación</b> (NOVEDAD_VALIDAR): el coordinador la valida o la rechaza. Al validarla se aplican sus efectos
 *       (equipo a mantenimiento, fuera de servicio, existencias…) y se dispara el aviso al correo institucional (n8n).</li>
 * </ol>
 * Si quien reporta ya puede validar (coordinador o administrador), la novedad queda validada de una vez.
 */
@Service
public class NovedadService {
    /** Evento interno: «se validó la novedad X, avisa». forzar = enviar el correo ya, aunque no sea prioridad ALTA. */
    public record NovedadRegistrada(Long id, boolean forzar) {
        public NovedadRegistrada(Long id) { this(id, false); }
    }

    private static final int MAX_ARCHIVOS = 5;

    private final ReporteNovedadRepository reportes;
    private final HistorialRepository historial;
    private final SeguimientoNovedadRepository seguimientos;
    private final MantenimientoService mantenimientos;
    private final ArchivoService archivos;
    private final NotificacionService notificaciones;
    private final Acceso acceso;
    private final Mapper mapper;
    private final ApplicationEventPublisher eventos;

    public NovedadService(ReporteNovedadRepository reportes, HistorialRepository historial, SeguimientoNovedadRepository seguimientos,
                          MantenimientoService mantenimientos, ArchivoService archivos, NotificacionService notificaciones,
                          Acceso acceso, Mapper mapper, ApplicationEventPublisher eventos) {
        this.reportes = reportes; this.historial = historial; this.seguimientos = seguimientos; this.mantenimientos = mantenimientos;
        this.archivos = archivos; this.notificaciones = notificaciones; this.acceso = acceso; this.mapper = mapper; this.eventos = eventos;
    }

    // ------------------------------------------------------------------ Registro preliminar

    @Transactional
    public NovedadDto reportar(Long actorId, String placa, NovedadRequest r, List<MultipartFile> adjuntos) {
        Usuario actor = acceso.usuario(actorId);
        acceso.exigir(actor, Permiso.NOVEDAD_REPORTAR);
        Equipo e = acceso.equipoParaEditar(actor, placa);
        ReporteNovedad.Tipo tipo;
        try { tipo = ReporteNovedad.Tipo.valueOf(r.tipo() == null ? "" : r.tipo().trim().toUpperCase()); }
        catch (IllegalArgumentException ex) { throw ApiException.badRequest("Tipo de novedad no válido"); }
        boolean conteo = tipo == ReporteNovedad.Tipo.EXISTENCIAS || tipo == ReporteNovedad.Tipo.CONSUMO;
        if (conteo && !e.esInsumo()) throw ApiException.badRequest("Los conteos de existencias y consumos solo aplican a insumos");
        String desc = r.descripcion() == null ? "" : r.descripcion().trim().replaceAll("\\s+", " ");
        int minimo = conteo ? 3 : 10;
        if (desc.length() < minimo) throw ApiException.badRequest(conteo ? "Agrega una observación (mínimo 3 caracteres)" : "Describe qué pasó (mínimo 10 caracteres)");
        if (desc.length() > 1000) throw ApiException.badRequest("La descripción no puede superar 1000 caracteres");
        BigDecimal cantidad = null;
        Unidad unidad = null;
        if (conteo) {
            unidad = r.unidad() == null || r.unidad().isBlank() ? e.getUnidad() : Unidad.de(r.unidad());
            if (unidad == null || !unidad.compatible(e.getUnidad())) {
                throw ApiException.badRequest("La unidad debe ser compatible con la del insumo (" + e.getUnidad().simbolo() + ")");
            }
            if (r.cantidad() == null) throw ApiException.badRequest("Indica la cantidad");
            InventarioService.validarCantidad(r.cantidad(), unidad, tipo == ReporteNovedad.Tipo.CONSUMO ? 0 : 0, "La cantidad");
            if (tipo == ReporteNovedad.Tipo.CONSUMO && r.cantidad().signum() == 0) throw ApiException.badRequest("El consumo debe ser mayor que cero");
            if (tipo == ReporteNovedad.Tipo.CONSUMO && unidad.convertirA(r.cantidad(), e.getUnidad()).compareTo(e.getCantidad()) > 0) {
                throw ApiException.badRequest("El consumo supera las existencias registradas (" + Mapper.cantidad(e.getCantidad(), e.getUnidad()) + ")");
            }
            cantidad = r.cantidad();
        }
        if (reportes.existsByEquipoIdAndTipoAndReportadoPorAndCreadoEnAfter(e.getId(), tipo, actor.getNombre(), mapper.ahora().minusMinutes(2))) {
            throw ApiException.conflict("Ya reportaste esta novedad para " + e.getPlaca() + " hace menos de 2 minutos");
        }
        List<MultipartFile> fs = adjuntos == null ? List.of() : adjuntos.stream().filter(f -> f != null && !f.isEmpty()).toList();
        if (fs.size() > MAX_ARCHIVOS) throw ApiException.badRequest("Puedes adjuntar máximo " + MAX_ARCHIVOS + " archivos por novedad");

        boolean pideMantenimiento = Boolean.TRUE.equals(r.mantenimiento()) && !e.esInsumo();
        LocalDateTime ahora = mapper.ahora();
        ReporteNovedad n = new ReporteNovedad(e.getSede() == null ? acceso.sede(actor) : e.getSede(), e, tipo, desc, actor.getNombre(),
                actor.getCorreo(), ahora, pideMantenimiento);
        if (cantidad != null) n.cantidad(cantidad, unidad);
        for (MultipartFile f : fs) n.getArchivos().add(archivos.guardar(actor, f));
        reportes.save(n);
        String det = tipo.texto() + (cantidad == null ? "" : " (" + Mapper.cantidad(cantidad, unidad) + ")") + ": " + desc;
        historial.save(new HistorialEvento(ahora, "NOVEDAD", e, "#" + n.getId(), null, actor.getNombre(),
                det.length() > 600 ? det.substring(0, 599) + "…" : det));

        if (acceso.puede(actor, Permiso.NOVEDAD_VALIDAR)) {
            aplicarValidacion(actor, n, "Registrada y validada por " + actor.getRol().texto().toLowerCase());
        } else {
            Long labId = acceso.laboratorioEnSede(n.getSede(), e.getLaboratorio());
            Set<String> para = notificaciones.correosDe(labId, Rol.COORDINADOR_LAB);
            if (para.isEmpty()) para = notificaciones.correosAdministradores();
            notificaciones.avisar("NOVEDAD_POR_VALIDAR", para, Map.of("placa", e.getPlaca(), "equipo", e.getNombre(),
                    "laboratorio", e.getLaboratorio(), "tipo", tipo.texto(), "descripcion", desc + (cantidad == null ? "" : " · " + Mapper.cantidad(cantidad, unidad)),
                    "reportadoPor", actor.getNombre(), "enlace", notificaciones.enlace(n.getSede().getSlug(), "/novedades")));
        }
        return mapper.novedad(n);
    }

    // ------------------------------------------------------------------ Validación

    @Transactional
    public NovedadDto validar(Long actorId, Long id, ValidarNovedadRequest r) {
        Usuario actor = acceso.usuario(actorId);
        acceso.exigir(actor, Permiso.NOVEDAD_VALIDAR);
        ReporteNovedad n = buscar(actor, id);
        if (n.getValidacion() != ReporteNovedad.Validacion.PENDIENTE) throw ApiException.conflict("La novedad ya fue " + n.getValidacion().texto().toLowerCase());
        if (!n.getEquipo().isActivo()) throw ApiException.conflict("El elemento fue dado de baja: rechaza la novedad");
        aplicarValidacion(actor, n, Texto.opcional(r == null ? null : r.observacion(), 600, "La observación"));
        return mapper.novedad(n);
    }

    @Transactional
    public NovedadDto rechazar(Long actorId, Long id, ValidarNovedadRequest r) {
        Usuario actor = acceso.usuario(actorId);
        acceso.exigir(actor, Permiso.NOVEDAD_VALIDAR);
        ReporteNovedad n = buscar(actor, id);
        if (n.getValidacion() != ReporteNovedad.Validacion.PENDIENTE) throw ApiException.conflict("La novedad ya fue " + n.getValidacion().texto().toLowerCase());
        String obs = Texto.obligatorio(r == null ? null : r.observacion(), 5, 600, "El motivo del rechazo");
        LocalDateTime ahora = mapper.ahora();
        n.validar(ReporteNovedad.Validacion.RECHAZADA, actor.getNombre(), obs, ahora);
        historial.save(new HistorialEvento(ahora, "VALIDACION", n.getEquipo(), "#" + n.getId(), n.getReportadoPor(), actor.getNombre(),
                "Novedad rechazada: " + obs));
        return mapper.novedad(n);
    }

    /** Aplica los efectos de la novedad sobre el inventario y dispara el aviso. */
    private void aplicarValidacion(Usuario actor, ReporteNovedad n, String observacion) {
        LocalDateTime ahora = mapper.ahora();
        Equipo e = n.getEquipo();
        StringBuilder efectos = new StringBuilder();
        switch (n.getTipo()) {
            case PERDIDO -> {
                if (!e.esInsumo() && e.getEstado() != EstadoEquipo.FUERA_SERVICIO) {
                    efectos.append("Equipo: ").append(e.getEstado().texto()).append(" → ").append(EstadoEquipo.FUERA_SERVICIO.texto()).append(". ");
                    e.setEstado(EstadoEquipo.FUERA_SERVICIO);
                }
            }
            case AGOTADO -> {
                if (e.esInsumo() && e.getCantidad() != null && e.getCantidad().signum() > 0) {
                    efectos.append("Existencias: ").append(Mapper.cantidad(e.getCantidad(), e.getUnidad())).append(" → 0. ");
                    e.fijarCantidad(BigDecimal.ZERO);
                }
            }
            case EXISTENCIAS -> {
                BigDecimal nueva = n.getUnidad().convertirA(n.getCantidad(), e.getUnidad());
                efectos.append("Existencias: ").append(Mapper.cantidad(e.getCantidad(), e.getUnidad())).append(" → ")
                        .append(Mapper.cantidad(nueva, e.getUnidad())).append(". ");
                e.fijarCantidad(nueva);
            }
            case CONSUMO -> {
                BigDecimal consumo = n.getUnidad().convertirA(n.getCantidad(), e.getUnidad());
                if (consumo.compareTo(e.getCantidad()) > 0) {
                    throw ApiException.conflict("El consumo (" + Mapper.cantidad(consumo, e.getUnidad()) + ") supera las existencias actuales ("
                            + Mapper.cantidad(e.getCantidad(), e.getUnidad()) + "): registra primero un conteo de existencias");
                }
                BigDecimal antes = e.getCantidad();
                e.descontar(consumo);
                efectos.append("Existencias: ").append(Mapper.cantidad(antes, e.getUnidad())).append(" → ")
                        .append(Mapper.cantidad(e.getCantidad(), e.getUnidad())).append(". ");
            }
            default -> { }
        }
        if (n.isEnMantenimiento() && !e.esInsumo()) {
            if (e.getEstado() == EstadoEquipo.DISPONIBLE) {
                e.setEstado(EstadoEquipo.MANTENIMIENTO);
                efectos.append("Equipo: Operativo → En mantenimiento. ");
            }
            Mantenimiento m = mantenimientos.desdeNovedad(actor, n);
            if (m != null) efectos.append("Mantenimiento ").append(m.getCodigo()).append(" requerido. ");
        }
        n.validar(ReporteNovedad.Validacion.VALIDADA, actor.getNombre(), observacion, ahora);
        String det = "Novedad validada. " + efectos + (observacion == null ? "" : observacion);
        historial.save(new HistorialEvento(ahora, "VALIDACION", e, "#" + n.getId(), n.getReportadoPor(), actor.getNombre(),
                det.trim().length() > 600 ? det.trim().substring(0, 599) + "…" : det.trim()));
        if (n.getTipo() == ReporteNovedad.Tipo.EXISTENCIAS || n.getTipo() == ReporteNovedad.Tipo.CONSUMO) {
            n.sinAviso("Ajuste de existencias: no genera correo", ahora);
        } else {
            n.porNotificar();
            eventos.publishEvent(new NovedadRegistrada(n.getId()));
        }
    }

    // ------------------------------------------------------------------ Consulta

    /** validacion: PENDIENTE, VALIDADA, RECHAZADA o vacío. */
    @Transactional(readOnly = true)
    public List<NovedadDto> listar(Long actorId, String validacion, String laboratorio, String placa) {
        Usuario actor = acceso.usuario(actorId);
        acceso.exigirAlguno(actor, Permiso.NOVEDAD_REPORTAR, Permiso.NOVEDAD_VALIDAR, Permiso.INVENTARIO_CONSULTAR);
        return reportes.findTop500BySedeIdOrderByCreadoEnDesc(acceso.sede(actor).getId()).stream()
                .filter(n -> acceso.ve(actor, n.getEquipo()))
                .filter(n -> Texto.vacio(validacion) || n.getValidacion().name().equalsIgnoreCase(validacion.trim()))
                .filter(n -> Texto.vacio(laboratorio) || laboratorio.trim().equalsIgnoreCase(n.getEquipo().getLaboratorio()))
                .filter(n -> Texto.vacio(placa) || n.getEquipo().getPlaca().equalsIgnoreCase(placa.trim()))
                .map(mapper::novedad).toList();
    }

    /** Huella para el «tiempo real»: el navegador la consulta cada pocos segundos y solo recarga la lista si cambió. */
    @Transactional(readOnly = true)
    public VersionReportesDto version(Long actorId) {
        Usuario actor = acceso.usuario(actorId);
        var filas = reportes.huella(acceso.sede(actor).getId());
        Object[] f = filas.isEmpty() ? new Object[0] : filas.get(0);
        long total = f.length > 0 && f[0] instanceof Number num ? num.longValue() : 0;
        StringBuilder sb = new StringBuilder();
        for (Object o : f) sb.append(o).append('|');
        return new VersionReportesDto(Integer.toHexString(sb.toString().hashCode()), total);
    }

    /** Vuelve a disparar el aviso (por ejemplo si n8n estaba apagado). ahora = enviar ya, sea cual sea la prioridad. */
    @Transactional
    public NovedadDto reenviar(Long actorId, Long id, boolean ahora) {
        Usuario actor = acceso.usuario(actorId);
        acceso.exigir(actor, Permiso.NOVEDAD_VALIDAR);
        ReporteNovedad n = buscar(actor, id);
        if (n.getValidacion() != ReporteNovedad.Validacion.VALIDADA) throw ApiException.conflict("Solo se avisa de novedades validadas");
        if (n.getNotificacion() == ReporteNovedad.Notificacion.NO_APLICA) throw ApiException.conflict("Esta novedad no genera aviso");
        if (n.getNotificacion() == ReporteNovedad.Notificacion.PENDIENTE) throw ApiException.conflict("El aviso se está enviando en este momento");
        n.reintentando();
        eventos.publishEvent(new NovedadRegistrada(n.getId(), ahora));
        return mapper.novedad(n);
    }

    // ------------------------------------------------------------------ Seguimiento

    @Transactional(readOnly = true)
    public List<SeguimientoDto> seguimiento(Long actorId, Long id) {
        Usuario actor = acceso.usuario(actorId);
        ReporteNovedad n = buscar(actor, id);
        return seguimientos.findByReporteIdOrderByFechaAsc(n.getId()).stream()
                .map(s -> new SeguimientoDto(s.getId(), s.getFecha(), s.getAutor(),
                        s.getEstadoAnterior() == null ? null : s.getEstadoAnterior().name(), s.getEstadoNuevo().name(), s.getComentario()))
                .toList();
    }

    /**
     * Comentario de seguimiento y, si se indica, cambio de estado (Abierto → En proceso → Resuelto).
     * Comentar: quien puede reportar. Cambiar el estado: quien puede validar.
     */
    @Transactional
    public NovedadDto seguir(Long actorId, Long id, SeguimientoRequest r) {
        Usuario actor = acceso.usuario(actorId);
        acceso.exigirAlguno(actor, Permiso.NOVEDAD_REPORTAR, Permiso.NOVEDAD_VALIDAR);
        ReporteNovedad n = buscar(actor, id);
        ReporteNovedad.Estado nuevo = n.getEstado();
        if (r.estado() != null && !r.estado().isBlank()) {
            try { nuevo = ReporteNovedad.Estado.valueOf(r.estado().trim().toUpperCase()); }
            catch (IllegalArgumentException e) { throw ApiException.badRequest("Estado no válido: ABIERTO, EN_PROCESO o RESUELTO"); }
        }
        if (nuevo != n.getEstado()) {
            acceso.exigir(actor, Permiso.NOVEDAD_VALIDAR);
            if (n.getValidacion() == ReporteNovedad.Validacion.PENDIENTE) throw ApiException.conflict("Primero valida la novedad");
        }
        String comentario = r.comentario() == null ? "" : r.comentario().trim().replaceAll("\\s+", " ");
        if (comentario.length() < 3) throw ApiException.badRequest("Escribe un comentario para el seguimiento (mínimo 3 caracteres)");
        if (comentario.length() > 600) throw ApiException.badRequest("El comentario admite máximo 600 caracteres");
        ReporteNovedad.Estado anterior = n.getEstado();
        LocalDateTime ahora = mapper.ahora();
        n.cambiarEstado(nuevo, ahora);
        seguimientos.save(new SeguimientoNovedad(n, ahora, actor.getNombre(), anterior, nuevo, comentario));
        String det = "Novedad #" + n.getId() + (anterior == nuevo ? "" : ": " + anterior.texto() + " → " + nuevo.texto()) + ". " + comentario;
        historial.save(new HistorialEvento(ahora, "SEGUIMIENTO", n.getEquipo(), "#" + n.getId(), null, actor.getNombre(),
                det.length() > 600 ? det.substring(0, 599) + "…" : det));
        return mapper.novedad(n);
    }

    private ReporteNovedad buscar(Usuario actor, Long id) {
        ReporteNovedad n = reportes.findById(id == null ? -1L : id).orElse(null);
        if (n == null || !acceso.ve(actor, n.getEquipo())) throw ApiException.notFound("La novedad no existe en tus laboratorios");
        return n;
    }
}
