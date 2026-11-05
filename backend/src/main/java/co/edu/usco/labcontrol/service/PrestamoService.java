package co.edu.usco.labcontrol.service;

import co.edu.usco.labcontrol.domain.*;
import co.edu.usco.labcontrol.repo.*;
import co.edu.usco.labcontrol.web.ApiException;
import co.edu.usco.labcontrol.web.Dtos.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Núcleo transaccional (RF-02, RF-03, RF-04, RNF-02). Cada operación que cambia la disponibilidad de un
 * equipo toma un bloqueo pesimista sobre la fila del equipo: dos auxiliares o estudiantes que actúan
 * al mismo tiempo sobre la misma placa quedan serializados y el segundo recibe un error de negocio.
 */
@Service
public class PrestamoService {
    public static final Pattern FORMATO_PLACA = Pattern.compile("^USCO-EQ-\\d{3}$");
    private static final String MSG_NO_EXISTE = "El equipo no existe en el inventario activo";
    private static final String MSG_LIMITE = "Límite máximo de equipos alcanzado";
    private static final Set<EstadoPrestamo> VIGENTES = Set.of(EstadoPrestamo.RESERVADO, EstadoPrestamo.ACTIVO);

    private final EquipoRepository equipos;
    private final PrestamoRepository prestamos;
    private final UsuarioRepository usuarios;
    private final HistorialRepository historial;
    private final Mapper mapper;
    private final int limite;

    public PrestamoService(EquipoRepository equipos, PrestamoRepository prestamos, UsuarioRepository usuarios,
                           HistorialRepository historial, Mapper mapper,
                           @Value("${labcontrol.reglas.limite-equipos-por-usuario}") int limite) {
        this.equipos = equipos; this.prestamos = prestamos; this.usuarios = usuarios;
        this.historial = historial; this.mapper = mapper; this.limite = limite;
    }

    // ------------------------------------------------------------------ Estudiante / Docente

    @Transactional
    public PrestamoDto reservar(Long usuarioId, ReservaRequest r) {
        Usuario u = usuarios.findById(usuarioId).orElseThrow(() -> ApiException.notFound("Usuario no encontrado"));
        LocalDateTime ahora = mapper.ahora();
        if (r.inicio() == null || r.fin() == null || !r.fin().isAfter(r.inicio()) || !r.fin().isAfter(ahora)) {
            throw ApiException.badRequest("Seleccione fecha y franja horaria para continuar");
        }
        if (r.asignatura() == null || r.asignatura().isBlank()) {
            throw ApiException.badRequest("Indique la asignatura para la cual solicita el equipo");
        }
        if (r.placa() == null || r.placa().isBlank()) throw ApiException.badRequest("Seleccione un equipo");

        Equipo e = equipos.findActivoForUpdate(r.placa()).orElseThrow(() -> ApiException.notFound(MSG_NO_EXISTE));
        if (e.getEstado() != EstadoEquipo.DISPONIBLE) throw ApiException.conflict("No disponible");
        if (prestamos.countBySolicitanteIdAndEstadoIn(u.getId(), VIGENTES) >= limite) throw ApiException.conflict(MSG_LIMITE);
        if (!prestamos.traslapes(e.getId(), r.inicio(), r.fin()).isEmpty()) {
            throw ApiException.conflict("El equipo ya tiene una reserva en esa franja horaria");
        }

        Prestamo p = new Prestamo(codigo("RES"), e, u, EstadoPrestamo.RESERVADO, r.asignatura().trim(),
                r.inicio(), r.fin(), ahora);
        if (r.docente() != null && !r.docente().isBlank()) p.setDocente(r.docente().trim());
        prestamos.save(p);
        evento("RESERVA", e, p, u.getNombre(), null, "Reserva " + p.getCodigo() + " para " + p.getAsignatura()
                + (p.getDocente() != null ? " · docente " + p.getDocente() : ""));
        return mapper.prestamo(p);
    }

    @Transactional
    public PrestamoDto cancelarReserva(Long usuarioId, Long prestamoId) {
        Prestamo p = prestamos.findById(prestamoId).orElseThrow(() -> ApiException.notFound("Reserva no encontrada"));
        if (!p.getSolicitante().getId().equals(usuarioId)) throw ApiException.notFound("Reserva no encontrada");
        if (p.getEstado() != EstadoPrestamo.RESERVADO) throw ApiException.conflict("Solo se pueden cancelar reservas pendientes");
        p.setEstado(EstadoPrestamo.CANCELADO);
        evento("CANCELACION", p.getEquipo(), p, p.getSolicitante().getNombre(), null, "Reserva cancelada por el usuario");
        return mapper.prestamo(p);
    }

    @Transactional(readOnly = true)
    public List<PrestamoDto> misPrestamos(Long usuarioId) {
        return prestamos.findBySolicitanteIdOrderByFechaSolicitudDesc(usuarioId).stream().map(mapper::prestamo).toList();
    }

    // ------------------------------------------------------------------ Ventanilla (Auxiliar)

    @Transactional(readOnly = true)
    public PanelDto panel() {
        List<PrestamoDto> activos = prestamos.findByEstadoOrderByFechaLimiteAsc(EstadoPrestamo.ACTIVO).stream().map(mapper::prestamo).toList();
        List<PrestamoDto> reservas = prestamos.findByEstadoOrderByFranjaInicioAsc(EstadoPrestamo.RESERVADO).stream().map(mapper::prestamo).toList();
        var lista = equipos.findByActivoTrueOrderByLaboratorioAscPlacaAsc();
        long disp = lista.stream().filter(e -> e.getEstado() == EstadoEquipo.DISPONIBLE).count();
        long pres = lista.stream().filter(e -> e.getEstado() == EstadoEquipo.PRESTADO).count();
        long mant = lista.stream().filter(e -> e.getEstado() == EstadoEquipo.MANTENIMIENTO).count();
        long venc = activos.stream().filter(PrestamoDto::vencido).count();
        return new PanelDto(new ResumenDto(disp, pres, mant, venc), reservas, activos);
    }

    @Transactional(readOnly = true)
    public SolicitudesUsuarioDto solicitudes(String documento) {
        Usuario u = buscarSolicitante(documento);
        List<PrestamoDto> reservas = prestamos.findBySolicitanteIdAndEstadoIn(u.getId(), Set.of(EstadoPrestamo.RESERVADO))
                .stream().map(mapper::prestamo).toList();
        long activos = prestamos.countBySolicitanteIdAndEstadoIn(u.getId(), Set.of(EstadoPrestamo.ACTIVO));
        return new SolicitudesUsuarioDto(u.getNombre(), u.getDocumento(), u.getCodigoEstudiantil(), u.getPrograma(), activos, limite, reservas);
    }

    /** Busca por documento o por código estudiantil (solo estudiantes y docentes activos). */
    private Usuario buscarSolicitante(String criterio) {
        String c = criterio == null ? "" : criterio.trim();
        return usuarios.findByDocumentoOrCodigoEstudiantil(c, c)
                .filter(x -> x.isActivo() && (x.getRol() == Rol.ESTUDIANTE || x.getRol() == Rol.DOCENTE))
                .orElseThrow(() -> ApiException.notFound("No se encontró un estudiante o docente con ese código o documento"));
    }

    /** El auxiliar rechaza una solicitud pendiente indicando el motivo (la ve el estudiante en «Mis reservas»). */
    @Transactional
    public PrestamoDto rechazarReserva(Long auxiliarId, Long prestamoId, String motivo) {
        Usuario aux = usuarios.findById(auxiliarId).orElseThrow(() -> ApiException.notFound("Auxiliar no encontrado"));
        if (motivo == null || motivo.trim().length() < 5) throw ApiException.badRequest("Indique el motivo del rechazo");
        Prestamo p = prestamos.findById(prestamoId == null ? -1L : prestamoId).orElseThrow(() -> ApiException.notFound("Solicitud no encontrada"));
        if (p.getEstado() != EstadoPrestamo.RESERVADO) throw ApiException.conflict("Solo se pueden rechazar solicitudes pendientes");
        p.setEstado(EstadoPrestamo.RECHAZADO);
        p.setObservaciones("Rechazada por " + aux.getNombre() + ": " + motivo.trim());
        evento("RECHAZO", p.getEquipo(), p, p.getSolicitante().getNombre(), aux.getNombre(), "Solicitud rechazada: " + motivo.trim());
        return mapper.prestamo(p);
    }

    /** Registrar Salida (préstamo): PF-08, PV-04, PV-10, PU-07. */
    @Transactional
    public PrestamoDto salida(Long auxiliarId, SalidaRequest r) {
        Usuario aux = usuarios.findById(auxiliarId).orElseThrow(() -> ApiException.notFound("Auxiliar no encontrado"));
        String placa = r.placa() == null ? "" : r.placa().trim().toUpperCase();
        if (!FORMATO_PLACA.matcher(placa).matches()) {
            throw ApiException.badRequest("Usa el formato USCO-EQ-000. Ejemplo: USCO-EQ-102");
        }
        int horas = r.horas() == null ? 2 : r.horas();
        if (horas < 1 || horas > 8) throw ApiException.badRequest("La duración del préstamo debe estar entre 1 y 8 horas");
        Usuario sol = buscarSolicitante(r.documento());

        Equipo e = equipos.findActivoForUpdate(placa).orElseThrow(() -> ApiException.notFound(MSG_NO_EXISTE));
        switch (e.getEstado()) {
            case PRESTADO -> throw ApiException.conflict("El equipo ya se encuentra prestado");
            case MANTENIMIENTO -> throw ApiException.conflict("El equipo está en mantenimiento y no puede prestarse");
            default -> { }
        }
        if (prestamos.countBySolicitanteIdAndEstadoIn(sol.getId(), Set.of(EstadoPrestamo.ACTIVO)) >= limite) {
            throw ApiException.conflict(MSG_LIMITE);
        }

        LocalDateTime ahora = mapper.ahora();
        LocalDateTime limiteDev = ahora.plusHours(horas);
        Prestamo propia = prestamos.findBySolicitanteIdAndEstadoIn(sol.getId(), Set.of(EstadoPrestamo.RESERVADO)).stream()
                .filter(p -> p.getEquipo().getId().equals(e.getId())).findFirst().orElse(null);
        boolean otraReserva = prestamos.traslapes(e.getId(), ahora, limiteDev).stream()
                .anyMatch(p -> propia == null || !p.getId().equals(propia.getId()));
        if (otraReserva) throw ApiException.conflict("El equipo está reservado por otro usuario en esa franja");

        Prestamo p = propia != null ? propia
                : new Prestamo(codigo("PR"), e, sol, EstadoPrestamo.ACTIVO, null, ahora, limiteDev, ahora);
        p.setEstado(EstadoPrestamo.ACTIVO);
        p.setFechaSalida(ahora);
        p.setFechaLimite(limiteDev);
        p.setAuxiliarSalida(aux);
        p.setCustodio(aux);
        prestamos.save(p);
        e.setEstado(EstadoEquipo.PRESTADO);
        evento("SALIDA", e, p, sol.getNombre(), aux.getNombre(),
                "Salida registrada. Devolución límite " + limiteDev.toLocalTime().withSecond(0).withNano(0));
        return mapper.prestamo(p);
    }

    /** Registrar Entrada (devolución): PF-09, PF-10, PS-07 (advertencia disciplinaria en el cliente). */
    @Transactional
    public EntradaResponse entrada(Long auxiliarId, EntradaRequest r) {
        Usuario aux = usuarios.findById(auxiliarId).orElseThrow(() -> ApiException.notFound("Auxiliar no encontrado"));
        Prestamo p = prestamos.findById(r.prestamoId() == null ? -1L : r.prestamoId())
                .orElseThrow(() -> ApiException.notFound("Préstamo no encontrado"));
        if (p.getEstado() != EstadoPrestamo.ACTIVO) throw ApiException.conflict("El préstamo no está activo");

        boolean problema = r.danos() || r.accesoriosIncompletos();
        if (!r.buenEstado() && !problema && !r.danosMenoresPreexistentes()) {
            throw ApiException.badRequest("Seleccione el estado físico del equipo");
        }
        if (r.buenEstado() && problema) {
            throw ApiException.badRequest("Estado inconsistente: no puede marcar «Buen estado» junto con daños o accesorios incompletos");
        }
        boolean hayNota = r.observaciones() != null && !r.observaciones().isBlank();
        if ((problema || r.danosMenoresPreexistentes()) && !hayNota) {
            throw ApiException.badRequest("Describa la novedad en las observaciones");
        }

        Equipo e = equipos.findByPlaca(p.getEquipo().getPlaca()).orElseThrow();
        LocalDateTime ahora = mapper.ahora();

        List<String> etiquetas = new ArrayList<>();
        if (r.buenEstado()) etiquetas.add("Buen estado");
        if (r.danos()) etiquetas.add("Presenta daños");
        if (r.accesoriosIncompletos()) etiquetas.add("Accesorios incompletos");
        if (r.danosMenoresPreexistentes()) etiquetas.add("Daños menores preexistentes");
        String obs = "[" + String.join(" · ", etiquetas) + "]" + (hayNota ? " " + r.observaciones().trim() : "");

        p.setEstado(EstadoPrestamo.DEVUELTO);
        p.setFechaEntrada(ahora);
        p.setAuxiliarEntrada(aux);
        p.setEstadoEntrada(problema ? "CON_NOVEDAD" : "BUEN_ESTADO");
        p.setObservaciones(obs);
        e.setEstado(r.danos() ? EstadoEquipo.MANTENIMIENTO : EstadoEquipo.DISPONIBLE);

        String incidencia = null;
        if (problema) {
            incidencia = String.format("INC-%d-%03d", ahora.getYear(), prestamos.countByIncidenciaIsNotNull() + 1);
            p.setIncidencia(incidencia);
        }
        prestamos.save(p);
        evento("ENTRADA", e, p, p.getSolicitante().getNombre(), aux.getNombre(), "Devolución registrada. " + obs);
        if (incidencia != null) {
            evento("INCIDENCIA", e, p, p.getSolicitante().getNombre(), aux.getNombre(),
                    "Incidencia " + incidencia + (r.danos() ? " · equipo enviado a mantenimiento" : " · accesorios faltantes"));
        }
        return new EntradaResponse(mapper.prestamo(p), mapper.equipo(e), incidencia);
    }

    // ------------------------------------------------------------------ Cierre de turno (PF-12)

    @Transactional(readOnly = true)
    public CierreTurnoDto cierreTurno(Long auxiliarId) {
        var pendientes = prestamos.findByEstadoAndCustodioIdOrderByFechaLimiteAsc(EstadoPrestamo.ACTIVO, auxiliarId)
                .stream().map(mapper::prestamo).toList();
        var otros = usuarios.findByRolAndActivoTrue(Rol.AUXILIAR).stream()
                .filter(a -> !a.getId().equals(auxiliarId)).map(a -> new AuxiliarDto(a.getId(), a.getNombre())).toList();
        return new CierreTurnoDto(pendientes, otros);
    }

    @Transactional
    public TransferenciaResponse transferirCustodia(Long auxiliarId, Long destinoId) {
        Usuario aux = usuarios.findById(auxiliarId).orElseThrow();
        if (destinoId == null || destinoId.equals(auxiliarId)) throw ApiException.badRequest("Seleccione el auxiliar que recibe el turno");
        Usuario destino = usuarios.findById(destinoId).filter(u -> u.getRol() == Rol.AUXILIAR)
                .orElseThrow(() -> ApiException.badRequest("El auxiliar de destino no es válido"));
        var pendientes = prestamos.findByEstadoAndCustodioIdOrderByFechaLimiteAsc(EstadoPrestamo.ACTIVO, auxiliarId);
        for (Prestamo p : pendientes) {
            p.setCustodio(destino);
            evento("CUSTODIA", p.getEquipo(), p, p.getSolicitante().getNombre(), aux.getNombre(),
                    "Custodia transferida de " + aux.getNombre() + " a " + destino.getNombre());
        }
        return new TransferenciaResponse(pendientes.size(), destino.getNombre());
    }

    // ------------------------------------------------------------------ util

    private void evento(String tipo, Equipo e, Prestamo p, String usuario, String auxiliar, String detalle) {
        historial.save(new HistorialEvento(mapper.ahora(), tipo, e, p.getCodigo(), usuario, auxiliar, detalle));
    }

    private String codigo(String prefijo) {
        String c = prefijo + "-" + (10000 + ThreadLocalRandom.current().nextInt(89999));
        while (prestamos.existsByCodigo(c)) {
            c = prefijo + "-" + (10000 + ThreadLocalRandom.current().nextInt(89999));
        }
        return c;
    }
}
