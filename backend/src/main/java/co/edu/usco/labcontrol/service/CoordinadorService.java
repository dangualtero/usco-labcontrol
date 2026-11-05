package co.edu.usco.labcontrol.service;

import co.edu.usco.labcontrol.domain.*;
import co.edu.usco.labcontrol.repo.*;
import co.edu.usco.labcontrol.web.ApiException;
import co.edu.usco.labcontrol.web.Dtos.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CoordinadorService {
    public static final List<String> LABORATORIOS = List.of("Electrónica", "Petróleos", "Civil", "Agrícola");
    private static final String MASCARA = "••••••";
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final EquipoRepository equipos;
    private final PrestamoRepository prestamos;
    private final HistorialRepository historial;
    private final UsuarioRepository usuarios;
    private final Mapper mapper;
    private final SedeContexto sedes;

    public CoordinadorService(EquipoRepository equipos, PrestamoRepository prestamos, HistorialRepository historial,
                              UsuarioRepository usuarios, Mapper mapper, SedeContexto sedes) {
        this.equipos = equipos; this.prestamos = prestamos; this.historial = historial;
        this.usuarios = usuarios; this.mapper = mapper; this.sedes = sedes;
    }

    @Transactional(readOnly = true)
    public KpisDto kpis(Long actorId, LocalDate desde, LocalDate hasta) {
        Sede sede = sedes.de(actorId);
        LocalDate h = hasta != null ? hasta : mapper.ahora().toLocalDate();
        LocalDate d = desde != null ? desde : h.minusDays(29);
        var rango = prestamos.conSalidaEntre(d.atStartOfDay(), h.plusDays(1).atStartOfDay()).stream()
                .filter(p -> sedes.pertenece(p.getEquipo(), sede)).toList();
        var activosEq = equipos.findBySedeIdAndActivoTrueOrderByLaboratorioAscPlacaAsc(sede.getId());
        LocalDateTime ahora = mapper.ahora();

        var devueltos = rango.stream().filter(p -> p.getEstado() == EstadoPrestamo.DEVUELTO).toList();
        long tiempoMedio = Math.round(devueltos.stream()
                .mapToLong(p -> java.time.Duration.between(p.getFechaSalida(), p.getFechaEntrada()).toMinutes()).average().orElse(0));
        long aTiempo = devueltos.stream().filter(p -> !p.getFechaEntrada().isAfter(p.getFechaLimite())).count();
        long pct = devueltos.isEmpty() ? 100 : Math.round(100.0 * aTiempo / devueltos.size());

        Map<String, Long> porLab = rango.stream().collect(Collectors.groupingBy(p -> p.getEquipo().getLaboratorio(), Collectors.counting()));
        var uso = LABORATORIOS.stream().map(l -> new UsoLaboratorioDto(l, porLab.getOrDefault(l, 0L))).toList();

        Map<LocalDate, Long> porDia = rango.stream().collect(Collectors.groupingBy(p -> p.getFechaSalida().toLocalDate(), TreeMap::new, Collectors.counting()));
        List<SerieDiaDto> serie = new ArrayList<>();
        for (LocalDate x = d; !x.isAfter(h) && serie.size() < 120; x = x.plusDays(1)) {
            serie.add(new SerieDiaDto(x.toString(), porDia.getOrDefault(x, 0L)));
        }

        var activos = prestamos.findByEstadoOrderByFechaLimiteAsc(EstadoPrestamo.ACTIVO).stream()
                .filter(p -> sedes.pertenece(p.getEquipo(), sede)).toList();
        return new KpisDto(activosEq.size(),
                activosEq.stream().filter(e -> e.getEstado() == EstadoEquipo.DISPONIBLE).count(),
                activosEq.stream().filter(e -> e.getEstado() == EstadoEquipo.PRESTADO).count(),
                activosEq.stream().filter(e -> e.getEstado() == EstadoEquipo.MANTENIMIENTO).count(),
                activos.size(), activos.stream().filter(p -> p.estaVencido(ahora)).count(),
                rango.size(), devueltos.size(), rango.stream().filter(p -> p.getIncidencia() != null).count(),
                tiempoMedio, pct, uso, serie);
    }

    @Transactional(readOnly = true)
    public List<HistorialDto> historial(Long actorId, LocalDate desde, LocalDate hasta, String laboratorio, String q) {
        Sede sede = sedes.de(actorId);
        LocalDate h = hasta != null ? hasta : mapper.ahora().toLocalDate();
        LocalDate d = desde != null ? desde : h.minusDays(29);
        String nq = CatalogoService.normalizar(q);
        return historial.entre(d.atStartOfDay(), h.plusDays(1).atStartOfDay()).stream()
                .filter(ev -> sedes.pertenece(ev.getSede(), sede))
                .filter(ev -> laboratorio == null || laboratorio.isBlank() || laboratorio.equalsIgnoreCase("todos")
                        || laboratorio.equalsIgnoreCase(ev.getLaboratorio()))
                .filter(ev -> nq.isEmpty() || CatalogoService.normalizar(String.join(" ",
                        nz(ev.getPlaca()), nz(ev.getEquipoNombre()), nz(ev.getUsuarioNombre()), nz(ev.getPrestamoCodigo()),
                        nz(ev.getTipo()))).contains(nq))
                .map(mapper::historial).toList();
    }

    /**
     * Reporte exportable sanitizado (PS-10 / Ley 1581 de 2012): conserva lo necesario para identificar al
     * responsable de un equipo y enmascara documento, teléfono y dirección de residencia.
     */
    @Transactional(readOnly = true)
    public String reporteCsv(Long actorId, LocalDate desde, LocalDate hasta, String laboratorio) {
        StringBuilder sb = new StringBuilder("﻿");
        sb.append("Fecha;Tipo;Placa;Equipo;Laboratorio;Préstamo;Usuario;Auxiliar;Detalle;Documento;Teléfono;Dirección\r\n");
        for (HistorialDto ev : historial(actorId, desde, hasta, laboratorio, null)) {
            sb.append(String.join(";", celda(ev.fecha().format(FMT)), celda(ev.tipo()), celda(ev.placa()), celda(ev.equipo()),
                    celda(ev.laboratorio()), celda(ev.prestamo()), celda(ev.usuario()), celda(ev.auxiliar()),
                    celda(ev.detalle()), MASCARA, MASCARA, MASCARA)).append("\r\n");
        }
        return sb.toString();
    }

    @Transactional(readOnly = true)
    public List<EquipoAdminDto> equiposAdmin(Long actorId) {
        List<EquipoAdminDto> out = new ArrayList<>();
        for (Equipo e : equipos.findBySedeIdOrderByLaboratorioAscPlacaAsc(sedes.de(actorId).getId())) {
            String ultimo = prestamos.findByEquipoPlacaAndEstadoInOrderByFechaSolicitudDesc(e.getPlaca(),
                    Set.of(EstadoPrestamo.ACTIVO, EstadoPrestamo.DEVUELTO)).stream().findFirst()
                    .map(p -> p.getSolicitante().getNombre()).orElse(null);
            out.add(new EquipoAdminDto(mapper.equipo(e), e.isActivo(), e.getBajaMotivo(), e.getBajaFecha(), ultimo));
        }
        return out;
    }

    /** Dar de baja = soft delete (PS-08): sale del inventario activo pero el registro se conserva. */
    @Transactional
    public EquipoAdminDto baja(Long coordinadorId, String placa, String motivo) {
        Usuario c = usuarios.findById(coordinadorId).orElseThrow();
        if (motivo == null || motivo.trim().length() < 5) throw ApiException.badRequest("Indique el motivo de la baja");
        Equipo e = equipos.findActivoForUpdate(placa).filter(x -> sedes.pertenece(x, sedes.de(c)))
                .orElseThrow(() -> ApiException.notFound("El equipo no existe en el inventario activo"));
        if (e.getEstado() == EstadoEquipo.PRESTADO) throw ApiException.conflict("No se puede dar de baja un equipo que está prestado");
        LocalDateTime ahora = mapper.ahora();
        e.darDeBaja(motivo.trim(), ahora);
        historial.save(new HistorialEvento(ahora, "BAJA", e, null, null, c.getNombre(), "Baja (soft delete): " + motivo.trim()));
        return new EquipoAdminDto(mapper.equipo(e), false, e.getBajaMotivo(), e.getBajaFecha(), null);
    }

    private static String nz(String s) { return s == null ? "" : s; }

    /** Escapa comillas y neutraliza inyección de fórmulas en Excel (=, +, -, @). */
    private static String celda(String v) {
        if (v == null) return "";
        String s = v;
        if (!s.isEmpty() && "=+-@".indexOf(s.charAt(0)) >= 0) s = "'" + s;
        return s.contains(";") || s.contains("\"") || s.contains("\n") ? "\"" + s.replace("\"", "\"\"") + "\"" : s;
    }
}
