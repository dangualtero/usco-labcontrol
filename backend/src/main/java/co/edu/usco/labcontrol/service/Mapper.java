package co.edu.usco.labcontrol.service;

import co.edu.usco.labcontrol.domain.*;
import co.edu.usco.labcontrol.web.Dtos.*;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class Mapper {
    private final Clock clock;

    public Mapper(Clock clock) { this.clock = clock; }

    public LocalDateTime ahora() { return LocalDateTime.now(clock); }
    public LocalDate hoy() { return LocalDate.now(clock); }

    public static List<String> lista(String pipe) {
        return pipe == null || pipe.isBlank() ? List.of()
                : Arrays.stream(pipe.split("\\|")).map(String::trim).filter(s -> !s.isEmpty()).toList();
    }

    public static List<LaboratorioRefDto> labs(Usuario u) {
        return u.getLaboratorios().stream().sorted(Comparator.comparing(Laboratorio::getNombre))
                .map(l -> new LaboratorioRefDto(l.getId(), l.getNombre())).toList();
    }

    public UsuarioDto usuario(Usuario u, List<String> permisos) {
        return new UsuarioDto(u.getId(), u.getNombre(), u.getCorreo(), u.getRol().name(), u.getRol().texto(), u.getDocumento(),
                u.getPrograma(), u.getTelefono(), slug(u.getSede()), nombreSede(u.getSede()), labs(u), permisos);
    }

    public UsuarioAdminDto usuarioAdmin(Usuario u) {
        return new UsuarioAdminDto(u.getId(), u.getNombre(), u.getCorreo(), u.getRol().name(), u.getRol().texto(), u.getDocumento(),
                u.getPrograma(), u.getTelefono(), u.isActivo(), slug(u.getSede()), nombreSede(u.getSede()), labs(u));
    }

    public EquipoDto equipo(Equipo e) {
        return new EquipoDto(e.getPlaca(), e.getNombre(), e.getMarca(), e.getModelo(), e.getNumeroSerie(),
                e.getCategoria(), e.getLaboratorio(), e.getUbicacion(), e.getDescripcion(), lista(e.getSpecs()),
                lista(e.getAccesorios()), e.getEstadoFisico(), e.getFotoUrl(), e.getEstado().name(), e.getTipo().name(),
                e.getCantidad(), e.getUnidad().name(), e.getCantidadReferencia(), e.porcentaje(), e.getObservaciones());
    }

    public EquipoAdminDto equipoAdmin(Equipo e) {
        return new EquipoAdminDto(equipo(e), e.isActivo(), e.getBajaMotivo(), e.getBajaFecha());
    }

    /** 1.500 L → «1,5 L»; 3 UNIDAD → «3 und». */
    public static String cantidad(BigDecimal c, Unidad u) {
        if (c == null) return "—";
        String n = c.stripTrailingZeros().toPlainString().replace('.', ',');
        return n + " " + (u == null ? "" : u.simbolo());
    }

    /** $ 1.250.000 */
    public static String pesos(Long v) {
        if (v == null) return "—";
        return "$ " + String.format(java.util.Locale.forLanguageTag("es-CO"), "%,d", v).replace(',', '.');
    }

    public HistorialDto historial(HistorialEvento h) {
        return new HistorialDto(h.getId(), h.getFecha(), h.getTipo(), h.getPlaca(), h.getEquipoNombre(),
                h.getLaboratorio(), h.getPrestamoCodigo(), h.getUsuarioNombre(), h.getAuxiliarNombre(), h.getDetalle());
    }

    public SedeDto sede(Sede s) {
        return new SedeDto(s.getId(), s.getSlug(), s.getNombre(), s.getCiudad(), s.getDireccion(), s.isActiva());
    }

    public MovimientoDto movimiento(MovimientoPresupuesto m) {
        return new MovimientoDto(m.getId(), m.getTipo().name(), m.getConcepto(), m.getCategoria(), m.getMonto(), m.getFecha(),
                m.getPlaca(), m.getRegistradoPor(), m.getRegistradoEn(), m.isAnulado(), m.getAnulacionMotivo(),
                m.getAnuladoPor(), m.getAnuladoEn(), m.getSolicitud() == null ? null : m.getSolicitud().getCodigo());
    }

    public MantenimientoDto mantenimiento(Mantenimiento m) {
        Equipo e = m.getEquipo();
        boolean vencido = m.getEstado() == Mantenimiento.Estado.PROGRAMADO && m.getFechaProgramada() != null && m.getFechaProgramada().isBefore(hoy());
        return new MantenimientoDto(m.getId(), m.getCodigo(), e.getPlaca(), e.getNombre(), e.getLaboratorio(), m.getTipo().name(),
                m.getTipo().texto(), m.getEstado().name(), m.getEstado().texto(), m.getDescripcion(), m.getFechaProgramada(),
                m.getFechaRealizada(), m.getResponsable(), m.getCosto(), m.getResultado(),
                m.getNovedad() == null ? null : m.getNovedad().getId(), m.getRegistradoPor(), m.getCreadoEn(), vencido);
    }

    public NovedadDto novedad(ReporteNovedad n) {
        Equipo e = n.getEquipo();
        return new NovedadDto(n.getId(), n.getSede().getSlug(), e.getPlaca(), e.getNombre(), e.getLaboratorio(), n.getTipo().name(),
                n.getTipo().texto(), n.getDescripcion(), n.getReportadoPor(), n.getCreadoEn(), n.isEnMantenimiento(), n.getPrioridad(),
                n.getAsuntoCorreo(), n.getResumenIa(), n.getAccionSugerida(), n.getNotificacion().name(), n.getNotificacionDetalle(),
                n.getDestinatario(), n.getNotificadoEn(), n.getEstado().name(), n.getEstado().texto(), n.getActualizadoEn(),
                e.getTipo().name(), n.getValidacion().name(), n.getValidacion().texto(), n.getValidadoPor(), n.getValidadoEn(),
                n.getObservacionValidacion(), n.getCantidad(), n.getUnidad() == null ? null : n.getUnidad().name(),
                n.getArchivos().stream().sorted(Comparator.comparing(ArchivoInfo::getSubidoEn)).map(ArchivoService::dto).toList());
    }

    public SolicitudResumenDto solicitud(Solicitud s) {
        return new SolicitudResumenDto(s.getId(), s.getCodigo(), s.getLaboratorio().getId(), s.getLaboratorio().getNombre(), s.getTipo(),
                s.getTitulo(), s.getPrioridadPropuesta(), s.getPrioridadAsignada(), s.getOrdenPrioridad(), s.getValorEstimado(),
                s.getValorAprobado(), s.getEstado().name(), s.getEstado().texto(), s.getCreadoPor().getNombre(),
                s.getCreadoPor().getRol().texto(), s.getCreadoEn(), s.getActualizadoEn(), s.getEnviadoEn(),
                s.getEstadoContractual().name(), s.getEstadoContractual().texto());
    }

    public AvanceDto avance(Avance a) {
        return new AvanceDto(a.getId(), a.getLaboratorio().getId(), a.getLaboratorio().getNombre(),
                a.getCompromiso() == null ? null : a.getCompromiso().getId(), a.getCompromiso() == null ? null : a.getCompromiso().getTitulo(),
                a.getSolicitud() == null ? null : a.getSolicitud().getId(), a.getSolicitud() == null ? null : a.getSolicitud().getCodigo(),
                a.getDescripcion(), a.getPorcentaje(), a.getAutor(), a.getCreadoEn(), a.getVerificacion().name(), a.getVerificacion().texto(),
                a.getVerificadoPor(), a.getVerificadoEn(), a.getObservacionVerificacion(),
                a.getEvidencias().stream().sorted(Comparator.comparing(ArchivoInfo::getSubidoEn)).map(ArchivoService::dto).toList());
    }

    private static String slug(Sede s) { return s == null ? null : s.getSlug(); }
    private static String nombreSede(Sede s) { return s == null ? null : s.getNombre(); }
}
