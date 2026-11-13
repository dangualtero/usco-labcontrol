package co.edu.usco.labcontrol.service;

import co.edu.usco.labcontrol.domain.ReporteNovedad;
import co.edu.usco.labcontrol.domain.ReporteNovedad.Notificacion;
import co.edu.usco.labcontrol.domain.Sede;
import co.edu.usco.labcontrol.domain.SolicitudInsumo;
import co.edu.usco.labcontrol.repo.ReporteNovedadRepository;
import co.edu.usco.labcontrol.repo.SedeRepository;
import co.edu.usco.labcontrol.repo.SolicitudInsumoRepository;
import co.edu.usco.labcontrol.web.Dtos.AlertaDto;
import co.edu.usco.labcontrol.web.Dtos.ResumenEnviadoDto;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Resumen diario por correo (una vez cada 24 horas, por defecto a las 7:00 a. m. hora de Colombia):
 * los reportes de prioridad MEDIA y BAJA que no se enviaron al instante, los insumos agotados o con 25 % o menos,
 * los reportes ALTA que siguen abiertos y las solicitudes de insumos sin atender. Una sede sin novedades ni alertas
 * de inventario no recibe correo ese día.
 */
@Service
public class ResumenDiarioService {
    private static final Logger log = LoggerFactory.getLogger(ResumenDiarioService.class);
    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("d MMM, h:mm a", Locale.forLanguageTag("es-CO"));
    private static final DateTimeFormatter DIA = DateTimeFormatter.ofPattern("EEEE d 'de' MMMM", Locale.forLanguageTag("es-CO"));

    private final SedeRepository sedes;
    private final ReporteNovedadRepository reportes;
    private final SolicitudInsumoRepository solicitudes;
    private final AlertaService alertas;
    private final CorreoService correo;
    private final Mapper mapper;
    private final TransactionTemplate tx;
    private final String destino;
    private final String appUrl;

    public ResumenDiarioService(SedeRepository sedes, ReporteNovedadRepository reportes, SolicitudInsumoRepository solicitudes,
                                AlertaService alertas, CorreoService correo, Mapper mapper, TransactionTemplate tx,
                                @Value("${labcontrol.novedades.destino:u20232215593@usco.edu.co}") String destino,
                                @Value("${labcontrol.app-url:http://localhost:5173}") String appUrl) {
        this.sedes = sedes; this.reportes = reportes; this.solicitudes = solicitudes; this.alertas = alertas; this.correo = correo;
        this.mapper = mapper; this.tx = tx; this.destino = destino; this.appUrl = appUrl.replaceAll("/+$", "");
    }

    @Scheduled(cron = "${labcontrol.resumen.cron:0 0 7 * * *}", zone = "${labcontrol.resumen.zona:America/Bogota}")
    public void enviarTodos() {
        for (Sede s : sedes.findByActivaTrueOrderByIdAsc()) {
            try { enviar(s.getId(), false); }
            catch (Exception e) { log.error("No se pudo enviar el resumen diario de {}", s.getNombre(), e); }
        }
    }

    /** forzar = enviarlo aunque no haya nada nuevo (botón «Enviar resumen ahora» del Coordinador). */
    public ResumenEnviadoDto enviar(Long sedeId, boolean forzar) {
        Contenido c = tx.execute(st -> contenido(sedeId));
        if (c == null) return new ResumenEnviadoDto(false, 0, 0, destino, "La sede no existe");
        if (!forzar && c.pendientes.isEmpty() && c.inventario.isEmpty()) {
            return new ResumenEnviadoDto(false, 0, 0, destino, "Sin reportes ni alertas de inventario: no se envió correo");
        }
        String asunto = "[Resumen diario] " + c.sede + " · " + c.pendientes.size() + " reporte(s) · " + c.inventario.size() + " alerta(s) de inventario";
        boolean enviado;
        try {
            enviado = correo.enviar(destino, asunto, c.texto, null);
        } catch (IllegalStateException e) {
            return new ResumenEnviadoDto(false, c.pendientes.size(), c.inventario.size(), destino, "El servidor de correo falló: " + e.getMessage());
        }
        if (!enviado) {
            return new ResumenEnviadoDto(false, c.pendientes.size(), c.inventario.size(), destino,
                    "Correo sin configurar (MAIL_HOST): el resumen quedó en la consola del backend");
        }
        List<Long> ids = c.pendientes.stream().map(ReporteNovedad::getId).toList();
        tx.executeWithoutResult(st -> reportes.findAllById(ids).forEach(r -> {
            if (r.getNotificacion() == Notificacion.PENDIENTE_RESUMEN) r.incluidoEnResumen(destino, mapper.ahora());
        }));
        log.info("Resumen diario de {} enviado a {} ({} reportes, {} alertas de inventario)", c.sede, destino, ids.size(), c.inventario.size());
        return new ResumenEnviadoDto(true, ids.size(), c.inventario.size(), destino, "Resumen enviado a " + destino);
    }

    private record Contenido(String sede, List<ReporteNovedad> pendientes, List<AlertaDto> inventario, String texto) {}

    private Contenido contenido(Long sedeId) {
        Sede sede = sedes.findById(sedeId).orElse(null);
        if (sede == null) return null;
        List<ReporteNovedad> pendientes = reportes.findBySedeIdAndNotificacionOrderByCreadoEnAsc(sede.getId(), Notificacion.PENDIENTE_RESUMEN);
        List<AlertaDto> inventario = alertas.inventario(sede);
        List<ReporteNovedad> altasAbiertas = reportes.findBySedeIdAndPrioridadAndEstadoInOrderByCreadoEnDesc(sede.getId(), "ALTA",
                List.of(ReporteNovedad.Estado.ABIERTO, ReporteNovedad.Estado.EN_PROCESO));
        long solicitudesPend = solicitudes.countBySedeIdAndEstado(sede.getId(), SolicitudInsumo.Estado.PENDIENTE);

        StringBuilder t = new StringBuilder();
        t.append("Resumen diario de USCO LabControl · ").append(sede.getNombre()).append('\n')
                .append(DIA.format(mapper.ahora())).append("\n\n");

        t.append("REPORTES DE PRIORIDAD MEDIA Y BAJA (").append(pendientes.size()).append(")\n");
        if (pendientes.isEmpty()) t.append("  Ninguno nuevo.\n");
        for (ReporteNovedad r : pendientes) {
            t.append("  • [").append(r.getPrioridad() == null ? "—" : r.getPrioridad()).append("] ")
                    .append(r.getEquipo().getPlaca()).append(" · ").append(r.getEquipo().getNombre())
                    .append(" · ").append(r.getTipo().texto()).append('\n')
                    .append("    ").append(FECHA.format(r.getCreadoEn())).append(" · reportó ").append(r.getReportadoPor()).append('\n')
                    .append("    ").append(r.getResumenIa() != null ? r.getResumenIa() : r.getDescripcion()).append('\n');
            if (r.getAccionSugerida() != null) t.append("    Acción sugerida: ").append(r.getAccionSugerida()).append('\n');
        }

        t.append("\nINVENTARIO: INSUMOS AGOTADOS O CON 25 % O MENOS (").append(inventario.size()).append(")\n");
        if (inventario.isEmpty()) t.append("  Todo en orden.\n");
        for (AlertaDto a : inventario) {
            t.append("  • ").append(a.placa()).append(" · ").append(a.elemento()).append(" (").append(a.laboratorio()).append(")\n")
                    .append("    ").append(a.titulo()).append(". ").append(a.detalle()).append('\n');
        }

        t.append("\nPENDIENTES\n")
                .append("  • Reportes de prioridad ALTA sin resolver: ").append(altasAbiertas.size()).append('\n')
                .append("  • Solicitudes de insumos sin atender: ").append(solicitudesPend).append("\n\n")
                .append("Ver reportes: ").append(appUrl).append('/').append(sede.getSlug()).append("/coordinador/reportes\n")
                .append("Ver alertas:  ").append(appUrl).append('/').append(sede.getSlug()).append("/coordinador/alertas\n\n")
                .append("Los reportes de prioridad ALTA se envían al instante; este resumen llega una vez al día.\n");
        // Cargar las relaciones dentro de la transacción (el correo se arma con ellas fuera de ella)
        pendientes.forEach(r -> r.getEquipo().getPlaca());
        return new Contenido(sede.getNombre(), pendientes, inventario, t.toString());
    }
}
