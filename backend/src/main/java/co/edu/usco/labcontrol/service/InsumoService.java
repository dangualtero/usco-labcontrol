package co.edu.usco.labcontrol.service;

import co.edu.usco.labcontrol.domain.*;
import co.edu.usco.labcontrol.repo.*;
import co.edu.usco.labcontrol.web.ApiException;
import co.edu.usco.labcontrol.web.Dtos.*;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Solicitudes de insumos. El estudiante o docente pide una cantidad (en la unidad que prefiera, siempre que sea
 * compatible: mL para un insumo en litros, g para uno en kilogramos…). El auxiliar la aprueba —se descuenta del
 * inventario y queda en el historial como CONSUMO— o la rechaza indicando el motivo.
 */
@Service
public class InsumoService {
    private static final int MAX_PENDIENTES = 5;

    private final SolicitudInsumoRepository solicitudes;
    private final EquipoRepository equipos;
    private final UsuarioRepository usuarios;
    private final HistorialRepository historial;
    private final SedeContexto sedes;
    private final Mapper mapper;

    public InsumoService(SolicitudInsumoRepository solicitudes, EquipoRepository equipos, UsuarioRepository usuarios,
                         HistorialRepository historial, SedeContexto sedes, Mapper mapper) {
        this.solicitudes = solicitudes; this.equipos = equipos; this.usuarios = usuarios; this.historial = historial;
        this.sedes = sedes; this.mapper = mapper;
    }

    // ------------------------------------------------------------------ Estudiante / Docente

    @Transactional
    public SolicitudInsumoDto solicitar(Long usuarioId, String placa, SolicitudInsumoRequest r) {
        Usuario u = usuarios.findById(usuarioId).orElseThrow(() -> ApiException.notFound("Usuario no encontrado"));
        Sede sede = sedes.de(u);
        Equipo e = equipos.findByPlacaAndActivoTrue(placa == null ? "" : placa.trim().toUpperCase())
                .filter(x -> sedes.pertenece(x, sede) && x.esInsumo())
                .orElseThrow(() -> ApiException.notFound("El insumo no existe en el inventario de la " + sede.getNombre()));
        Unidad unidad = r.unidad() == null || r.unidad().isBlank() ? e.getUnidad() : Unidad.de(r.unidad());
        if (unidad == null || !unidad.compatible(e.getUnidad())) {
            throw ApiException.badRequest("Para este insumo usa una unidad compatible con " + e.getUnidad().simbolo());
        }
        if (r.cantidad() == null || r.cantidad().signum() <= 0) throw ApiException.badRequest("Indica cuánto necesitas (mayor que cero)");
        InventarioService.validarCantidad(r.cantidad(), unidad, 0, "La cantidad");
        BigDecimal equivalente = unidad.convertirA(r.cantidad(), e.getUnidad());
        if (equivalente.signum() <= 0) throw ApiException.badRequest("La cantidad es demasiado pequeña");
        if (equivalente.compareTo(e.getCantidad()) > 0) {
            throw ApiException.conflict("No hay suficiente: quedan " + Mapper.cantidad(e.getCantidad(), e.getUnidad()));
        }
        String motivo = r.motivo() == null ? "" : r.motivo().trim().replaceAll("\\s+", " ");
        if (motivo.length() < 5) throw ApiException.badRequest("Cuéntale al auxiliar para qué lo necesitas (mínimo 5 caracteres)");
        if (motivo.length() > 300) throw ApiException.badRequest("El motivo admite máximo 300 caracteres");
        String asignatura = r.asignatura() == null || r.asignatura().isBlank() ? null : r.asignatura().trim();
        if (asignatura != null && asignatura.length() > 120) throw ApiException.badRequest("La asignatura admite máximo 120 caracteres");
        if (solicitudes.countBySolicitanteIdAndEstado(u.getId(), SolicitudInsumo.Estado.PENDIENTE) >= MAX_PENDIENTES) {
            throw ApiException.conflict("Tienes " + MAX_PENDIENTES + " solicitudes de insumos pendientes. Espera a que el auxiliar las atienda");
        }
        SolicitudInsumo s = solicitudes.save(new SolicitudInsumo(codigo(), sede, e, u, r.cantidad(), unidad, equivalente,
                motivo, asignatura, mapper.ahora()));
        return mapper.solicitud(s);
    }

    @Transactional(readOnly = true)
    public List<SolicitudInsumoDto> mias(Long usuarioId) {
        return solicitudes.findTop100BySolicitanteIdOrderByCreadoEnDesc(usuarioId).stream().map(mapper::solicitud).toList();
    }

    @Transactional
    public SolicitudInsumoDto cancelar(Long usuarioId, Long id) {
        SolicitudInsumo s = solicitudes.findById(id == null ? -1L : id).filter(x -> x.getSolicitante().getId().equals(usuarioId))
                .orElseThrow(() -> ApiException.notFound("Solicitud no encontrada"));
        if (s.getEstado() != SolicitudInsumo.Estado.PENDIENTE) throw ApiException.conflict("Solo se pueden cancelar solicitudes pendientes");
        s.resolver(SolicitudInsumo.Estado.CANCELADA, s.getSolicitante().getNombre(), "Cancelada por el solicitante", mapper.ahora());
        return mapper.solicitud(s);
    }

    // ------------------------------------------------------------------ Auxiliar

    /** Pendientes primero (las más antiguas arriba) y luego las ya resueltas más recientes. */
    @Transactional(readOnly = true)
    public List<SolicitudInsumoDto> deSede(Long actorId) {
        Sede sede = sedes.de(actorId);
        List<SolicitudInsumo> todas = new ArrayList<>(solicitudes.findTop200BySedeIdOrderByCreadoEnDesc(sede.getId()));
        todas.sort(Comparator.comparing((SolicitudInsumo s) -> s.getEstado() != SolicitudInsumo.Estado.PENDIENTE)
                .thenComparing(s -> s.getEstado() == SolicitudInsumo.Estado.PENDIENTE ? s.getCreadoEn() : null,
                        Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(SolicitudInsumo::getCreadoEn, Comparator.reverseOrder()));
        return todas.stream().map(mapper::solicitud).toList();
    }

    @Transactional
    public SolicitudInsumoDto aprobar(Long auxiliarId, Long id, String respuesta) {
        Usuario aux = usuarios.findById(auxiliarId).orElseThrow(() -> ApiException.notFound("Auxiliar no encontrado"));
        SolicitudInsumo s = pendiente(aux, id);
        Equipo e = equipos.findActivoForUpdate(s.getEquipo().getPlaca())
                .orElseThrow(() -> ApiException.conflict("El insumo ya no está en el inventario activo"));
        if (s.getCantidadEquivalente().compareTo(e.getCantidad()) > 0) {
            throw ApiException.conflict("No alcanza: quedan " + Mapper.cantidad(e.getCantidad(), e.getUnidad())
                    + " y se pidieron " + Mapper.cantidad(s.getCantidad(), s.getUnidad()) + ". Recházala o ajusta las existencias");
        }
        e.descontar(s.getCantidadEquivalente());
        String nota = respuesta == null || respuesta.isBlank() ? null : recorta(respuesta.trim(), 300);
        s.resolver(SolicitudInsumo.Estado.APROBADA, aux.getNombre(), nota, mapper.ahora());
        historial.save(new HistorialEvento(mapper.ahora(), "CONSUMO", e, s.getCodigo(), s.getSolicitante().getNombre(), aux.getNombre(),
                recorta("Entregado " + Mapper.cantidad(s.getCantidad(), s.getUnidad()) + " (quedan " + Mapper.cantidad(e.getCantidad(), e.getUnidad())
                        + (e.porcentaje() == null ? "" : ", " + e.porcentaje() + " %") + "). Motivo: " + s.getMotivo(), 600)));
        return mapper.solicitud(s);
    }

    @Transactional
    public SolicitudInsumoDto rechazar(Long auxiliarId, Long id, String motivo) {
        Usuario aux = usuarios.findById(auxiliarId).orElseThrow(() -> ApiException.notFound("Auxiliar no encontrado"));
        if (motivo == null || motivo.trim().length() < 5) throw ApiException.badRequest("Indica el motivo del rechazo (mínimo 5 caracteres)");
        SolicitudInsumo s = pendiente(aux, id);
        s.resolver(SolicitudInsumo.Estado.RECHAZADA, aux.getNombre(), recorta(motivo.trim(), 300), mapper.ahora());
        return mapper.solicitud(s);
    }

    private SolicitudInsumo pendiente(Usuario aux, Long id) {
        Sede sede = sedes.de(aux);
        SolicitudInsumo s = solicitudes.findById(id == null ? -1L : id).filter(x -> x.getSede().es(sede))
                .orElseThrow(() -> ApiException.notFound("Solicitud no encontrada"));
        if (s.getEstado() != SolicitudInsumo.Estado.PENDIENTE) throw ApiException.conflict("La solicitud ya fue atendida");
        return s;
    }

    private String codigo() {
        String c;
        do { c = "SI-" + (10000 + ThreadLocalRandom.current().nextInt(89999)); } while (solicitudes.existsByCodigo(c));
        return c;
    }

    private static String recorta(String s, int n) { return s.length() <= n ? s : s.substring(0, n - 1) + "…"; }
}
