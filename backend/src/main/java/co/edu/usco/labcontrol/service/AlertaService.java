package co.edu.usco.labcontrol.service;

import co.edu.usco.labcontrol.domain.*;
import co.edu.usco.labcontrol.repo.*;
import co.edu.usco.labcontrol.web.Dtos.AlertaDto;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Alertas de la sede, calculadas en el momento de consultar (no se guardan):
 * <ul>
 *   <li><b>AGOTADO</b>: insumo sin existencias.</li>
 *   <li><b>STOCK_BAJO</b>: insumo con 25 % o menos de su cantidad de referencia.</li>
 *   <li><b>NOVEDAD</b>: reporte de prioridad ALTA o MEDIA que aún no está resuelto.</li>
 *   <li><b>PRESTAMO_VENCIDO</b>: equipo que no se devolvió a tiempo.</li>
 *   <li><b>MANTENIMIENTO</b>: equipo fuera de servicio.</li>
 *   <li><b>SOLICITUD_PENDIENTE</b>: pedido de insumo esperando al auxiliar.</li>
 * </ul>
 */
@Service
public class AlertaService {
    /** Se alerta cuando un insumo queda en este porcentaje o menos. */
    public static final int UMBRAL_PCT = 25;
    private static final Map<String, Integer> ORDEN = Map.of("CRITICA", 0, "ADVERTENCIA", 1, "INFO", 2);

    private final EquipoRepository equipos;
    private final ReporteNovedadRepository reportes;
    private final PrestamoRepository prestamos;
    private final SolicitudInsumoRepository solicitudes;
    private final SedeContexto sedes;
    private final Mapper mapper;

    public AlertaService(EquipoRepository equipos, ReporteNovedadRepository reportes, PrestamoRepository prestamos,
                         SolicitudInsumoRepository solicitudes, SedeContexto sedes, Mapper mapper) {
        this.equipos = equipos; this.reportes = reportes; this.prestamos = prestamos; this.solicitudes = solicitudes;
        this.sedes = sedes; this.mapper = mapper;
    }

    @Transactional(readOnly = true)
    public List<AlertaDto> listar(Long actorId) { return deSede(sedes.de(actorId)); }

    @Transactional(readOnly = true)
    public List<AlertaDto> deSede(Sede sede) {
        LocalDateTime ahora = mapper.ahora();
        List<AlertaDto> out = new ArrayList<>(inventario(sede));

        for (Equipo e : equipos.findBySedeIdAndActivoTrueOrderByLaboratorioAscPlacaAsc(sede.getId())) {
            if (!e.esInsumo() && e.getEstado() == EstadoEquipo.MANTENIMIENTO) {
                out.add(new AlertaDto("MAN-" + e.getPlaca(), "MANTENIMIENTO", "INFO", "En mantenimiento",
                        "No se puede prestar hasta que termine el mantenimiento", e.getPlaca(), e.getNombre(), "EQUIPO",
                        e.getLaboratorio(), null, null));
            }
        }
        for (ReporteNovedad r : reportes.findTop500BySedeIdOrderByCreadoEnDesc(sede.getId())) {
            if (r.getEstado() == ReporteNovedad.Estado.RESUELTO || r.getPrioridad() == null || "BAJA".equals(r.getPrioridad())) continue;
            Equipo e = r.getEquipo();
            boolean alta = "ALTA".equals(r.getPrioridad());
            out.add(new AlertaDto("NOV-" + r.getId(), "NOVEDAD", alta ? "CRITICA" : "ADVERTENCIA",
                    "Reporte de prioridad " + r.getPrioridad() + " · " + r.getEstado().texto(),
                    r.getTipo().texto() + ": " + r.getDescripcion(), e.getPlaca(), e.getNombre(), e.getTipo().name(),
                    e.getLaboratorio(), null, r.getCreadoEn()));
        }
        for (Prestamo p : prestamos.findByEstadoOrderByFechaLimiteAsc(EstadoPrestamo.ACTIVO)) {
            if (!sedes.pertenece(p.getEquipo(), sede) || !p.estaVencido(ahora)) continue;
            Equipo e = p.getEquipo();
            out.add(new AlertaDto("PRE-" + p.getId(), "PRESTAMO_VENCIDO", "CRITICA", "Préstamo vencido",
                    p.getCodigo() + " · " + p.getSolicitante().getNombre() + " debía devolverlo", e.getPlaca(), e.getNombre(), "EQUIPO",
                    e.getLaboratorio(), null, p.getFechaLimite()));
        }
        for (SolicitudInsumo s : solicitudes.findBySedeIdAndEstadoOrderByCreadoEnAsc(sede.getId(), SolicitudInsumo.Estado.PENDIENTE)) {
            Equipo e = s.getEquipo();
            out.add(new AlertaDto("SOL-" + s.getId(), "SOLICITUD_PENDIENTE", "INFO", "Solicitud de insumo pendiente",
                    s.getSolicitante().getNombre() + " pide " + Mapper.cantidad(s.getCantidad(), s.getUnidad()) + " (" + s.getCodigo() + ")",
                    e.getPlaca(), e.getNombre(), "INSUMO", e.getLaboratorio(), e.porcentaje(), s.getCreadoEn()));
        }
        out.sort(Comparator.comparing((AlertaDto a) -> ORDEN.getOrDefault(a.nivel(), 9))
                .thenComparing(AlertaDto::fecha, Comparator.nullsFirst(Comparator.reverseOrder())));
        return out;
    }

    /** Solo las alertas de existencias (agotado y 25 % o menos): las usa también el resumen diario. */
    @Transactional(readOnly = true)
    public List<AlertaDto> inventario(Sede sede) {
        List<AlertaDto> out = new ArrayList<>();
        for (Equipo e : equipos.findBySedeIdAndActivoTrueOrderByLaboratorioAscPlacaAsc(sede.getId())) {
            if (!e.esInsumo()) continue;
            Integer pct = e.porcentaje();
            String quedan = "Quedan " + Mapper.cantidad(e.getCantidad(), e.getUnidad())
                    + (e.getCantidadReferencia() == null ? "" : " de " + Mapper.cantidad(e.getCantidadReferencia(), e.getUnidad()));
            if (e.getCantidad().signum() == 0) {
                out.add(new AlertaDto("AGO-" + e.getPlaca(), "AGOTADO", "CRITICA", "Insumo agotado", quedan + ". Hay que reponerlo",
                        e.getPlaca(), e.getNombre(), "INSUMO", e.getLaboratorio(), 0, null));
            } else if (pct != null && pct <= UMBRAL_PCT) {
                out.add(new AlertaDto("STK-" + e.getPlaca(), "STOCK_BAJO", "ADVERTENCIA", "Existencias en " + pct + " %",
                        quedan + " (se ha consumido el " + (100 - pct) + " %)", e.getPlaca(), e.getNombre(), "INSUMO",
                        e.getLaboratorio(), pct, null));
            }
        }
        return out;
    }
}
