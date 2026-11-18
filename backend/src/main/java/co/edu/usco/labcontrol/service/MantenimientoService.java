package co.edu.usco.labcontrol.service;

import co.edu.usco.labcontrol.domain.*;
import co.edu.usco.labcontrol.repo.*;
import co.edu.usco.labcontrol.web.ApiException;
import co.edu.usco.labcontrol.web.Dtos.*;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Mantenimientos de los equipos.
 * <ul>
 *   <li>Consultar los programados (INVENTARIO_CONSULTAR): todos los roles, en sus laboratorios.</li>
 *   <li>Registrar un requerimiento de mantenimiento (MANTENIMIENTO_REQUERIR): queda REQUERIDO.</li>
 *   <li>Programar, ejecutar, cerrar o cancelar (MANTENIMIENTO_GESTIONAR): Administrador y Coordinador.</li>
 * </ul>
 * El estado del equipo acompaña al mantenimiento: EN_EJECUCION lo pasa a «En mantenimiento» y REALIZADO lo devuelve
 * a «Operativo» (o a «Fuera de servicio» si el resultado indica que no quedó funcionando).
 */
@Service
public class MantenimientoService {
    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("d 'de' MMMM 'de' yyyy", Locale.forLanguageTag("es-CO"));

    private final MantenimientoRepository mantenimientos;
    private final HistorialRepository historial;
    private final NotificacionService notificaciones;
    private final Acceso acceso;
    private final Mapper mapper;

    public MantenimientoService(MantenimientoRepository mantenimientos, HistorialRepository historial, NotificacionService notificaciones,
                                Acceso acceso, Mapper mapper) {
        this.mantenimientos = mantenimientos; this.historial = historial; this.notificaciones = notificaciones;
        this.acceso = acceso; this.mapper = mapper;
    }

    /** estado: un estado concreto, ABIERTOS o vacío (todos). */
    @Transactional(readOnly = true)
    public List<MantenimientoDto> listar(Long actorId, String estado, String laboratorio, String placa) {
        Usuario actor = acceso.usuario(actorId);
        acceso.exigir(actor, Permiso.INVENTARIO_CONSULTAR);
        return mantenimientos.findTop500BySedeIdOrderByCreadoEnDesc(acceso.sede(actor).getId()).stream()
                .filter(m -> acceso.ve(actor, m.getEquipo()) || (actor.esAdministrador()))
                .filter(m -> Texto.vacio(estado) || ("ABIERTOS".equalsIgnoreCase(estado) ? m.getEstado().abierto() : m.getEstado().name().equalsIgnoreCase(estado.trim())))
                .filter(m -> Texto.vacio(laboratorio) || laboratorio.trim().equalsIgnoreCase(m.getEquipo().getLaboratorio()))
                .filter(m -> Texto.vacio(placa) || m.getEquipo().getPlaca().equalsIgnoreCase(placa.trim()))
                .map(mapper::mantenimiento).toList();
    }

    @Transactional
    public MantenimientoDto crear(Long actorId, MantenimientoRequest r) {
        Usuario actor = acceso.usuario(actorId);
        acceso.exigirAlguno(actor, Permiso.MANTENIMIENTO_REQUERIR, Permiso.MANTENIMIENTO_GESTIONAR);
        boolean gestiona = acceso.puede(actor, Permiso.MANTENIMIENTO_GESTIONAR);
        Equipo e = acceso.equipo(actor, r.placa());
        if (e.esInsumo()) throw ApiException.badRequest("Los insumos no llevan mantenimiento: reporta una novedad de existencias o consumo");
        Mantenimiento.Tipo tipo = tipo(r.tipo());
        String descripcion = Texto.obligatorio(r.descripcion(), 5, 1000, "La descripción");
        if (!gestiona && (r.fechaProgramada() != null || r.responsable() != null || r.costo() != null)) {
            throw ApiException.forbidden("Puedes registrar el requerimiento; la fecha, el responsable y el costo los define el coordinador");
        }
        LocalDate fecha = r.fechaProgramada();
        if (fecha != null && fecha.isBefore(mapper.hoy())) throw ApiException.badRequest("La fecha programada no puede estar en el pasado");
        Mantenimiento m = new Mantenimiento(codigo(), e.getSede() == null ? acceso.sede(actor) : e.getSede(), e, tipo, descripcion, fecha,
                null, actor.getNombre(), mapper.ahora());
        if (gestiona) m.actualizar(tipo, descripcion, fecha, Texto.opcional(r.responsable(), 120, "El responsable"), costo(r.costo()), null);
        mantenimientos.save(m);
        historial.save(new HistorialEvento(mapper.ahora(), "MANTENIMIENTO", e, m.getCodigo(), null, actor.getNombre(),
                tipo.texto() + " " + m.getEstado().texto().toLowerCase(Locale.ROOT)
                        + (fecha == null ? "" : " para el " + FECHA.format(fecha)) + ": " + descripcion));
        if (fecha != null) avisarProgramado(m);
        return mapper.mantenimiento(m);
    }

    /** Requerimiento automático al validar una novedad que pide mantenimiento. */
    Mantenimiento desdeNovedad(Usuario actor, ReporteNovedad n) {
        Equipo e = n.getEquipo();
        boolean yaHay = mantenimientos.findByEquipoIdOrderByCreadoEnDesc(e.getId()).stream().anyMatch(m -> m.getEstado().abierto());
        if (yaHay) return null;
        Mantenimiento m = new Mantenimiento(codigo(), n.getSede(), e, Mantenimiento.Tipo.CORRECTIVO,
                n.getTipo().texto() + ": " + n.getDescripcion(), null, n, actor.getNombre(), mapper.ahora());
        mantenimientos.save(m);
        historial.save(new HistorialEvento(mapper.ahora(), "MANTENIMIENTO", e, m.getCodigo(), n.getReportadoPor(), actor.getNombre(),
                "Mantenimiento correctivo requerido a partir de la novedad #" + n.getId()));
        return m;
    }

    @Transactional
    public MantenimientoDto actualizar(Long actorId, Long id, MantenimientoRequest r) {
        Usuario actor = acceso.usuario(actorId);
        acceso.exigir(actor, Permiso.MANTENIMIENTO_GESTIONAR);
        Mantenimiento m = buscar(actor, id);
        if (!m.getEstado().abierto()) throw ApiException.conflict("El mantenimiento ya está " + m.getEstado().texto().toLowerCase(Locale.ROOT));
        LocalDate antes = m.getFechaProgramada();
        LocalDate fecha = r.fechaProgramada();
        if (fecha != null && !fecha.equals(antes) && fecha.isBefore(mapper.hoy())) throw ApiException.badRequest("La fecha programada no puede estar en el pasado");
        Mantenimiento.Tipo tipo = r.tipo() == null ? m.getTipo() : tipo(r.tipo());
        String descripcion = r.descripcion() == null ? m.getDescripcion() : Texto.obligatorio(r.descripcion(), 5, 1000, "La descripción");
        m.actualizar(tipo, descripcion, fecha, Texto.opcional(r.responsable(), 120, "El responsable"), costo(r.costo()), mapper.ahora());
        if (m.getEstado() == Mantenimiento.Estado.REQUERIDO && fecha != null) m.cambiarEstado(Mantenimiento.Estado.PROGRAMADO, null, null, mapper.ahora());
        if (m.getEstado() == Mantenimiento.Estado.PROGRAMADO && fecha == null) m.cambiarEstado(Mantenimiento.Estado.REQUERIDO, null, null, mapper.ahora());
        String det = "Mantenimiento actualizado";
        if (fecha != null && !fecha.equals(antes)) det = "Mantenimiento programado para el " + FECHA.format(fecha);
        historial.save(new HistorialEvento(mapper.ahora(), "MANTENIMIENTO", m.getEquipo(), m.getCodigo(), null, actor.getNombre(), det));
        if (fecha != null && !fecha.equals(antes)) avisarProgramado(m);
        return mapper.mantenimiento(m);
    }

    @Transactional
    public MantenimientoDto cambiarEstado(Long actorId, Long id, MantenimientoEstadoRequest r) {
        Usuario actor = acceso.usuario(actorId);
        acceso.exigir(actor, Permiso.MANTENIMIENTO_GESTIONAR);
        Mantenimiento m = buscar(actor, id);
        Mantenimiento.Estado nuevo;
        try { nuevo = Mantenimiento.Estado.valueOf(r.estado() == null ? "" : r.estado().trim().toUpperCase()); }
        catch (IllegalArgumentException ex) { throw ApiException.badRequest("Estado no válido"); }
        if (!m.getEstado().abierto()) throw ApiException.conflict("El mantenimiento ya está " + m.getEstado().texto().toLowerCase(Locale.ROOT));
        if (nuevo == m.getEstado()) throw ApiException.conflict("El mantenimiento ya está en ese estado");
        if (nuevo == Mantenimiento.Estado.REQUERIDO) throw ApiException.badRequest("Para volver a requerido, quita la fecha programada");
        if (nuevo == Mantenimiento.Estado.PROGRAMADO && m.getFechaProgramada() == null) throw ApiException.badRequest("Primero define la fecha programada");
        String resultado = Texto.opcional(r.resultado(), 1000, "El resultado");
        if ((nuevo == Mantenimiento.Estado.REALIZADO || nuevo == Mantenimiento.Estado.CANCELADO) && (resultado == null || resultado.length() < 5)) {
            throw ApiException.badRequest(nuevo == Mantenimiento.Estado.REALIZADO ? "Describe el resultado del mantenimiento (mínimo 5 caracteres)"
                    : "Indica el motivo de la cancelación (mínimo 5 caracteres)");
        }
        LocalDate realizada = null;
        if (nuevo == Mantenimiento.Estado.REALIZADO) {
            realizada = r.fechaRealizada() == null ? mapper.hoy() : r.fechaRealizada();
            if (realizada.isAfter(mapper.hoy())) throw ApiException.badRequest("La fecha de realización no puede estar en el futuro");
        }
        Mantenimiento.Estado antes = m.getEstado();
        m.cambiarEstado(nuevo, realizada, resultado, mapper.ahora());
        Equipo e = m.getEquipo();
        EstadoEquipo estadoAntes = e.getEstado();
        if (nuevo == Mantenimiento.Estado.EN_EJECUCION) e.setEstado(EstadoEquipo.MANTENIMIENTO);
        if (nuevo == Mantenimiento.Estado.REALIZADO) {
            e.setEstado(Boolean.FALSE.equals(r.equipoOperativo()) ? EstadoEquipo.FUERA_SERVICIO : EstadoEquipo.DISPONIBLE);
        }
        String det = antes.texto() + " → " + nuevo.texto() + (resultado == null ? "" : ": " + resultado);
        if (estadoAntes != e.getEstado()) det += ". Equipo: " + estadoAntes.texto() + " → " + e.getEstado().texto();
        historial.save(new HistorialEvento(mapper.ahora(), "MANTENIMIENTO", e, m.getCodigo(), null, actor.getNombre(),
                det.length() > 600 ? det.substring(0, 599) + "…" : det));
        return mapper.mantenimiento(m);
    }

    // ------------------------------------------------------------------

    private Mantenimiento buscar(Usuario actor, Long id) {
        Mantenimiento m = mantenimientos.findById(id == null ? -1L : id).orElse(null);
        if (m == null || !acceso.ve(actor, m.getEquipo())) throw ApiException.notFound("El mantenimiento no existe en tus laboratorios");
        return m;
    }

    private static Mantenimiento.Tipo tipo(String t) {
        try { return Mantenimiento.Tipo.valueOf(t == null ? "" : t.trim().toUpperCase()); }
        catch (IllegalArgumentException ex) { throw ApiException.badRequest("Tipo de mantenimiento no válido: preventivo, correctivo, calibración o verificación"); }
    }

    private static Long costo(Long c) {
        if (c == null) return null;
        if (c < 0 || c > 100_000_000_000L) throw ApiException.badRequest("El costo no es válido");
        return c;
    }

    /** MT-2026-0001, consecutivo por año. */
    private String codigo() {
        String prefijo = "MT-" + mapper.hoy().getYear() + "-";
        long n = mantenimientos.countByCodigoStartingWith(prefijo) + 1;
        String c;
        do { c = prefijo + String.format("%04d", n++); } while (mantenimientos.existsByCodigo(c));
        return c;
    }

    private void avisarProgramado(Mantenimiento m) {
        Equipo e = m.getEquipo();
        Long labId = labId(m);
        Set<String> para = new HashSet<>(notificaciones.correosDe(labId, Rol.COORDINADOR_LAB));
        para.addAll(notificaciones.correosDe(labId, Rol.MONITOR));
        notificaciones.avisar("MANTENIMIENTO_PROGRAMADO", para, Map.of(
                "codigo", m.getCodigo(), "tipo", m.getTipo().texto(), "placa", e.getPlaca(), "equipo", e.getNombre(),
                "laboratorio", e.getLaboratorio(), "fecha", m.getFechaProgramada() == null ? "—" : FECHA.format(m.getFechaProgramada()),
                "responsable", m.getResponsable() == null ? "Por definir" : m.getResponsable(),
                "enlace", notificaciones.enlace(m.getSede().getSlug(), "/mantenimientos")));
    }

    private Long labId(Mantenimiento m) {
        String nombre = m.getEquipo().getLaboratorio();
        return m.getSede() == null || nombre == null ? null
                : acceso.laboratorioEnSede(m.getSede(), nombre);
    }
}
