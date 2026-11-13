package co.edu.usco.labcontrol.service;

import co.edu.usco.labcontrol.domain.*;
import co.edu.usco.labcontrol.web.Dtos.*;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class Mapper {
    private final Clock clock;

    public Mapper(Clock clock) { this.clock = clock; }

    public LocalDateTime ahora() { return LocalDateTime.now(clock); }

    public static List<String> lista(String pipe) {
        return pipe == null || pipe.isBlank() ? List.of()
                : Arrays.stream(pipe.split("\\|")).map(String::trim).filter(s -> !s.isEmpty()).toList();
    }

    public UsuarioDto usuario(Usuario u) {
        return new UsuarioDto(u.getId(), u.getNombre(), u.getCorreo(), u.getRol().name(), u.getRol().grupo(),
                u.getDocumento(), u.getPrograma(), u.getCodigoEstudiantil(), slug(u.getSede()), nombreSede(u.getSede()));
    }

    public UsuarioAdminDto usuarioAdmin(Usuario u) {
        return new UsuarioAdminDto(u.getId(), u.getNombre(), u.getCorreo(), u.getRol().name(), u.getDocumento(),
                u.getCodigoEstudiantil(), u.getPrograma(), u.isActivo(), slug(u.getSede()), nombreSede(u.getSede()));
    }

    public EquipoDto equipo(Equipo e) {
        return new EquipoDto(e.getPlaca(), e.getNombre(), e.getMarca(), e.getModelo(), e.getNumeroSerie(),
                e.getCategoria(), e.getLaboratorio(), e.getUbicacion(), e.getDescripcion(), lista(e.getSpecs()),
                lista(e.getAccesorios()), e.getEstadoFisico(), e.getFotoUrl(), e.getEstado().name(), e.getTipo().name(),
                e.getCantidad(), e.getUnidad().name(), e.getCantidadReferencia(), e.porcentaje());
    }

    /** 1.500 L → «1,5 L»; 3 UNIDAD → «3 und». */
    public static String cantidad(java.math.BigDecimal c, Unidad u) {
        if (c == null) return "—";
        String n = c.stripTrailingZeros().toPlainString().replace('.', ',');
        return n + " " + (u == null ? "" : u.simbolo());
    }

    public SolicitudInsumoDto solicitud(SolicitudInsumo s) {
        Equipo e = s.getEquipo();
        return new SolicitudInsumoDto(s.getId(), s.getCodigo(), e.getPlaca(), e.getNombre(), e.getLaboratorio(),
                s.getSolicitante().getNombre(), s.getSolicitante().getDocumento(), s.getCantidad(), s.getUnidad().name(),
                s.getCantidadEquivalente(), e.getUnidad().name(), e.getCantidad(), s.getEstado().name(), s.getMotivo(),
                s.getAsignatura(), s.getCreadoEn(), s.getResueltoEn(), s.getResueltoPor(), s.getRespuesta());
    }

    public PrestamoDto prestamo(Prestamo p) {
        return new PrestamoDto(p.getId(), p.getCodigo(), p.getEquipo().getPlaca(), p.getEquipo().getNombre(),
                p.getEquipo().getLaboratorio(), p.getEquipo().getFotoUrl(),
                p.getSolicitante().getNombre(), p.getSolicitante().getDocumento(),
                p.getAsignatura(), p.getDocente(), p.getEstado().name(), p.estaVencido(ahora()),
                p.getFranjaInicio(), p.getFranjaFin(), p.getFechaSolicitud(), p.getFechaSalida(),
                p.getFechaLimite(), p.getFechaEntrada(), p.getEstadoEntrada(), p.getObservaciones(),
                p.getIncidencia(), nombre(p.getAuxiliarSalida()), nombre(p.getAuxiliarEntrada()), nombre(p.getCustodio()));
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
                m.getAnuladoPor(), m.getAnuladoEn());
    }

    private static String nombre(Usuario u) { return u == null ? null : u.getNombre(); }
    private static String slug(Sede s) { return s == null ? null : s.getSlug(); }
    private static String nombreSede(Sede s) { return s == null ? null : s.getNombre(); }
}
