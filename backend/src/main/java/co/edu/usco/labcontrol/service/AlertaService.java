package co.edu.usco.labcontrol.service;

import co.edu.usco.labcontrol.domain.*;
import co.edu.usco.labcontrol.repo.*;
import co.edu.usco.labcontrol.web.Dtos.AlertaDto;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Alertas calculadas en el momento de consultar (no se guardan). Cada cuenta ve las de sus laboratorios y, de las que
 * requieren una acción, solo las que puede atender. Umbrales configurables en Parametrización → Parámetros.
 * <ul>
 *   <li><b>AGOTADO</b> / <b>STOCK_BAJO</b>: insumos sin existencias o por debajo del umbral.</li>
 *   <li><b>FUERA_SERVICIO</b>: equipos fuera de servicio.</li>
 *   <li><b>NOVEDAD_POR_VALIDAR</b>: registros preliminares esperando al coordinador.</li>
 *   <li><b>NOVEDAD</b>: novedades validadas de prioridad ALTA o MEDIA sin resolver.</li>
 *   <li><b>MANTENIMIENTO_VENCIDO</b> / <b>MANTENIMIENTO_PROXIMO</b>: mantenimientos programados.</li>
 *   <li><b>SOLICITUD_POR_VALIDAR</b>, <b>SOLICITUD_DEVUELTA</b>, <b>SOLICITUD_SIN_DECISION</b>: solicitudes que esperan a alguien.</li>
 *   <li><b>COMPROMISO_VENCIDO</b>: compromisos que pasaron su fecha límite.</li>
 * </ul>
 */
@Service
public class AlertaService {
    private static final Map<String, Integer> ORDEN = Map.of("CRITICA", 0, "ADVERTENCIA", 1, "INFO", 2);
    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.forLanguageTag("es-CO"));

    private final EquipoRepository equipos;
    private final ReporteNovedadRepository reportes;
    private final MantenimientoRepository mantenimientos;
    private final SolicitudRepository solicitudes;
    private final CompromisoRepository compromisos;
    private final ConfiguracionService configuracion;
    private final Acceso acceso;
    private final Mapper mapper;

    public AlertaService(EquipoRepository equipos, ReporteNovedadRepository reportes, MantenimientoRepository mantenimientos,
                         SolicitudRepository solicitudes, CompromisoRepository compromisos, ConfiguracionService configuracion,
                         Acceso acceso, Mapper mapper) {
        this.equipos = equipos; this.reportes = reportes; this.mantenimientos = mantenimientos; this.solicitudes = solicitudes;
        this.compromisos = compromisos; this.configuracion = configuracion; this.acceso = acceso; this.mapper = mapper;
    }

    @Transactional(readOnly = true)
    public List<AlertaDto> listar(Long actorId) {
        Usuario actor = acceso.usuario(actorId);
        acceso.exigir(actor, Permiso.ALERTAS_VER);
        return calcular(acceso.sede(actor), actor);
    }

    /** Todas las alertas de la sede (para el resumen diario). */
    @Transactional(readOnly = true)
    public List<AlertaDto> deSede(Sede sede) { return calcular(sede, null); }

    /** actor null = vista completa del Administrador. */
    private List<AlertaDto> calcular(Sede sede, Usuario actor) {
        Set<String> nombres = actor == null || actor.esAdministrador() ? null : acceso.nombres(actor);
        Predicate<String> ve = lab -> nombres == null || (lab != null && nombres.contains(lab.toLowerCase(Locale.ROOT)));
        boolean admin = actor == null || actor.esAdministrador();
        boolean validaNovedades = actor == null || acceso.puede(actor, Permiso.NOVEDAD_VALIDAR);
        boolean validaSolicitudes = actor == null || acceso.puede(actor, Permiso.SOLICITUD_VALIDAR);
        boolean revisaSolicitudes = actor == null || acceso.puede(actor, Permiso.SOLICITUD_REVISAR);
        LocalDateTime ahora = mapper.ahora();
        LocalDate hoy = mapper.hoy();

        List<AlertaDto> out = new ArrayList<>(inventario(sede, ve));
        for (Equipo e : equipos.findBySedeIdAndActivoTrueOrderByLaboratorioAscPlacaAsc(sede.getId())) {
            if (e.esInsumo() || e.getEstado() != EstadoEquipo.FUERA_SERVICIO || !ve.test(e.getLaboratorio())) continue;
            out.add(new AlertaDto("FS-" + e.getPlaca(), "FUERA_SERVICIO", "ADVERTENCIA", "Equipo fuera de servicio",
                    "Revisa su hoja de vida para programar la reparación o la baja", e.getPlaca(), e.getNombre(), "EQUIPO",
                    e.getLaboratorio(), null, null, "/inventario/" + e.getPlaca()));
        }

        int diasNovedad = configuracion.entero(ConfiguracionService.DIAS_NOVEDAD);
        for (ReporteNovedad r : reportes.findTop500BySedeIdOrderByCreadoEnDesc(sede.getId())) {
            Equipo e = r.getEquipo();
            if (!ve.test(e.getLaboratorio())) continue;
            if (r.getValidacion() == ReporteNovedad.Validacion.PENDIENTE) {
                if (!validaNovedades) continue;
                boolean atrasada = r.getCreadoEn().plusDays(diasNovedad).isBefore(ahora);
                out.add(new AlertaDto("NPV-" + r.getId(), "NOVEDAD_POR_VALIDAR", atrasada ? "CRITICA" : "ADVERTENCIA",
                        "Novedad por validar" + (atrasada ? " hace más de " + diasNovedad + " día(s)" : ""),
                        r.getReportadoPor() + " · " + r.getTipo().texto() + ": " + r.getDescripcion(), e.getPlaca(), e.getNombre(),
                        e.getTipo().name(), e.getLaboratorio(), null, r.getCreadoEn(), "/novedades"));
                continue;
            }
            if (r.getValidacion() != ReporteNovedad.Validacion.VALIDADA || r.getEstado() == ReporteNovedad.Estado.RESUELTO
                    || r.getPrioridad() == null || "BAJA".equals(r.getPrioridad())) continue;
            boolean alta = "ALTA".equals(r.getPrioridad());
            out.add(new AlertaDto("NOV-" + r.getId(), "NOVEDAD", alta ? "CRITICA" : "ADVERTENCIA",
                    "Novedad de prioridad " + r.getPrioridad() + " · " + r.getEstado().texto(),
                    r.getTipo().texto() + ": " + r.getDescripcion(), e.getPlaca(), e.getNombre(), e.getTipo().name(),
                    e.getLaboratorio(), null, r.getCreadoEn(), "/novedades"));
        }

        int diasMant = configuracion.entero(ConfiguracionService.DIAS_MANTENIMIENTO);
        for (Mantenimiento m : mantenimientos.findTop500BySedeIdOrderByCreadoEnDesc(sede.getId())) {
            Equipo e = m.getEquipo();
            if (m.getEstado() != Mantenimiento.Estado.PROGRAMADO || m.getFechaProgramada() == null || !ve.test(e.getLaboratorio())) continue;
            LocalDate f = m.getFechaProgramada();
            if (f.isBefore(hoy)) {
                out.add(new AlertaDto("MV-" + m.getId(), "MANTENIMIENTO_VENCIDO", "CRITICA", "Mantenimiento vencido",
                        m.getCodigo() + " · " + m.getTipo().texto() + " programado para el " + FECHA.format(f), e.getPlaca(), e.getNombre(),
                        "EQUIPO", e.getLaboratorio(), null, f.atStartOfDay(), "/mantenimientos"));
            } else if (!f.isAfter(hoy.plusDays(diasMant))) {
                out.add(new AlertaDto("MP-" + m.getId(), "MANTENIMIENTO_PROXIMO", "INFO", "Mantenimiento próximo",
                        m.getCodigo() + " · " + m.getTipo().texto() + " el " + FECHA.format(f)
                                + (m.getResponsable() == null ? "" : " · " + m.getResponsable()), e.getPlaca(), e.getNombre(),
                        "EQUIPO", e.getLaboratorio(), null, f.atStartOfDay(), "/mantenimientos"));
            }
        }

        int diasSol = configuracion.entero(ConfiguracionService.DIAS_SOLICITUD);
        for (Solicitud s : solicitudes.findTop500BySedeIdOrderByCreadoEnDesc(sede.getId())) {
            String lab = s.getLaboratorio().getNombre();
            if (!ve.test(lab)) continue;
            boolean propia = actor != null && s.getCreadoPor().getId().equals(actor.getId());
            switch (s.getEstado()) {
                case EN_REVISION -> {
                    if (!admin && validaSolicitudes) out.add(alertaSolicitud(s, "SOLICITUD_POR_VALIDAR", "ADVERTENCIA",
                            "Solicitud por validar", s.getCreadoPor().getNombre() + " la preparó y espera tu revisión"));
                }
                case DEVUELTA -> {
                    if (propia || (!admin && validaSolicitudes)) out.add(alertaSolicitud(s, "SOLICITUD_DEVUELTA", "ADVERTENCIA",
                            "Solicitud devuelta para ajuste", s.getObservacionDevolucion() == null ? "Revisa la observación" : s.getObservacionDevolucion()));
                }
                case ENVIADA -> {
                    if (revisaSolicitudes && s.getEnviadoEn() != null && s.getEnviadoEn().plusDays(diasSol).isBefore(ahora)) {
                        out.add(alertaSolicitud(s, "SOLICITUD_SIN_DECISION", "ADVERTENCIA", "Solicitud sin decisión",
                                "Enviada hace más de " + diasSol + " días · " + Mapper.pesos(s.getValorEstimado())));
                    }
                }
                default -> { }
            }
        }

        for (Compromiso c : compromisos.findBySedeIdOrderByFechaLimiteAsc(sede.getId())) {
            if (!c.vencido(hoy)) continue;
            if (c.getLaboratorio() != null ? !ve.test(c.getLaboratorio().getNombre()) : !admin) continue;
            out.add(new AlertaDto("CV-" + c.getId(), "COMPROMISO_VENCIDO", "CRITICA", "Compromiso vencido",
                    c.getTitulo() + " · venció el " + FECHA.format(c.getFechaLimite())
                            + (c.getResponsable() == null ? "" : " · " + c.getResponsable().getNombre()),
                    null, null, null, c.getLaboratorio() == null ? "Toda la sede" : c.getLaboratorio().getNombre(), null,
                    c.getFechaLimite().atStartOfDay(), "/seguimiento"));
        }

        out.sort(Comparator.comparing((AlertaDto a) -> ORDEN.getOrDefault(a.nivel(), 9))
                .thenComparing(AlertaDto::fecha, Comparator.nullsFirst(Comparator.reverseOrder())));
        return out;
    }

    private static AlertaDto alertaSolicitud(Solicitud s, String tipo, String nivel, String titulo, String detalle) {
        return new AlertaDto("SR-" + s.getId() + "-" + tipo, tipo, nivel, titulo + " · " + s.getCodigo(), s.getTitulo() + ". " + detalle,
                null, null, null, s.getLaboratorio().getNombre(), null, s.getActualizadoEn() == null ? s.getCreadoEn() : s.getActualizadoEn(),
                "/solicitudes/" + s.getId());
    }

    /** Solo las alertas de existencias (agotado y por debajo del umbral): las usa también el resumen diario. */
    @Transactional(readOnly = true)
    public List<AlertaDto> inventario(Sede sede) { return inventario(sede, l -> true); }

    private List<AlertaDto> inventario(Sede sede, Predicate<String> ve) {
        int umbral = configuracion.entero(ConfiguracionService.UMBRAL_STOCK);
        List<AlertaDto> out = new ArrayList<>();
        for (Equipo e : equipos.findBySedeIdAndActivoTrueOrderByLaboratorioAscPlacaAsc(sede.getId())) {
            if (!e.esInsumo() || !ve.test(e.getLaboratorio()) || e.getCantidad() == null) continue;
            Integer pct = e.porcentaje();
            String quedan = "Quedan " + Mapper.cantidad(e.getCantidad(), e.getUnidad())
                    + (e.getCantidadReferencia() == null ? "" : " de " + Mapper.cantidad(e.getCantidadReferencia(), e.getUnidad()));
            if (e.getCantidad().signum() == 0) {
                out.add(new AlertaDto("AGO-" + e.getPlaca(), "AGOTADO", "CRITICA", "Insumo agotado", quedan + ". Hay que reponerlo",
                        e.getPlaca(), e.getNombre(), "INSUMO", e.getLaboratorio(), 0, null, "/inventario/" + e.getPlaca()));
            } else if (pct != null && pct <= umbral) {
                out.add(new AlertaDto("STK-" + e.getPlaca(), "STOCK_BAJO", "ADVERTENCIA", "Existencias en " + pct + " %",
                        quedan + " (se ha consumido el " + (100 - pct) + " %)", e.getPlaca(), e.getNombre(), "INSUMO",
                        e.getLaboratorio(), pct, null, "/inventario/" + e.getPlaca()));
            }
        }
        return out;
    }
}
