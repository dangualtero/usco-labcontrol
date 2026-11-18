package co.edu.usco.labcontrol.service;

import co.edu.usco.labcontrol.domain.*;
import co.edu.usco.labcontrol.repo.*;
import co.edu.usco.labcontrol.web.ApiException;
import co.edu.usco.labcontrol.web.Dtos.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Reportes y seguimiento: indicadores por laboratorio, tablero, historial (trazabilidad) y exportaciones CSV.
 * Cada cuenta obtiene los datos de sus laboratorios; el Administrador, los de toda la sede.
 */
@Service
public class ReporteService {
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final String BOM = "﻿";
    private static final Set<Solicitud.Estado> EN_TRAMITE = Set.of(Solicitud.Estado.EN_REVISION, Solicitud.Estado.ENVIADA, Solicitud.Estado.DEVUELTA);

    private final EquipoRepository equipos;
    private final MantenimientoRepository mantenimientos;
    private final ReporteNovedadRepository novedades;
    private final SolicitudRepository solicitudes;
    private final CompromisoRepository compromisos;
    private final AvanceRepository avances;
    private final LaboratorioDocumentoRepository documentos;
    private final HistorialRepository historial;
    private final SolicitudService solicitudService;
    private final SeguimientoService seguimiento;
    private final SedeContexto sedes;
    private final ConfiguracionService configuracion;
    private final SolicitudEventoRepository eventos;
    private final Acceso acceso;
    private final Mapper mapper;

    public ReporteService(EquipoRepository equipos, MantenimientoRepository mantenimientos, ReporteNovedadRepository novedades,
                          SolicitudRepository solicitudes, CompromisoRepository compromisos, AvanceRepository avances,
                          LaboratorioDocumentoRepository documentos, HistorialRepository historial, SolicitudService solicitudService,
                          SeguimientoService seguimiento, SedeContexto sedes, ConfiguracionService configuracion,
                          SolicitudEventoRepository eventos, Acceso acceso, Mapper mapper) {
        this.equipos = equipos; this.mantenimientos = mantenimientos; this.novedades = novedades; this.solicitudes = solicitudes;
        this.compromisos = compromisos; this.avances = avances; this.documentos = documentos; this.historial = historial;
        this.solicitudService = solicitudService; this.seguimiento = seguimiento; this.sedes = sedes;
        this.configuracion = configuracion; this.eventos = eventos; this.acceso = acceso; this.mapper = mapper;
    }

    // ------------------------------------------------------------------ Datos de la sede visibles para la cuenta

    private record Datos(List<Laboratorio> labs, List<Equipo> equipos, List<Mantenimiento> mantenimientos, List<ReporteNovedad> novedades,
                         List<Solicitud> solicitudes, List<Compromiso> compromisos, List<Avance> avances, Map<Long, String> decisiones) {}

    private Datos datos(Usuario actor) {
        Sede sede = acceso.sede(actor);
        List<Laboratorio> labs = acceso.laboratorios(actor);
        Set<String> nombres = acceso.nombres(actor);
        Function<String, Boolean> ve = n -> actor.esAdministrador() || (n != null && nombres.contains(n.toLowerCase(Locale.ROOT)));
        return new Datos(labs,
                equipos.findBySedeIdAndActivoTrueOrderByLaboratorioAscPlacaAsc(sede.getId()).stream().filter(e -> ve.apply(e.getLaboratorio())).toList(),
                mantenimientos.findTop500BySedeIdOrderByCreadoEnDesc(sede.getId()).stream().filter(m -> ve.apply(m.getEquipo().getLaboratorio())).toList(),
                novedades.findTop500BySedeIdOrderByCreadoEnDesc(sede.getId()).stream().filter(n -> ve.apply(n.getEquipo().getLaboratorio())).toList(),
                solicitudes.findTop500BySedeIdOrderByCreadoEnDesc(sede.getId()).stream().filter(s -> solicitudService.visible(actor, s)).toList(),
                compromisos.findBySedeIdOrderByFechaLimiteAsc(sede.getId()).stream()
                        .filter(c -> c.getLaboratorio() == null ? actor.esAdministrador() : acceso.ve(actor, c.getLaboratorio())).toList(),
                avances.findTop500BySedeIdOrderByCreadoEnDesc(sede.getId()).stream().filter(a -> acceso.ve(actor, a.getLaboratorio())).toList(),
                decisiones(sede));
    }

    /** Última decisión (APROBADA / RECHAZADA) de cada solicitud de la sede, aunque después se haya cerrado. */
    private Map<Long, String> decisiones(Sede sede) {
        Map<Long, String> out = new java.util.HashMap<>();
        for (Object[] f : eventos.decisiones(sede.getId())) out.put(((Number) f[0]).longValue(), (String) f[1]);
        return out;
    }

    // ------------------------------------------------------------------ Indicadores

    @Transactional(readOnly = true)
    public IndicadoresDto indicadores(Long actorId) {
        Usuario actor = acceso.usuario(actorId);
        acceso.exigirAlguno(actor, Permiso.REPORTES_GENERAR, Permiso.TABLERO_VER);
        return indicadores(actor, datos(actor));
    }

    private IndicadoresDto indicadores(Usuario actor, Datos d) {
        boolean verValores = acceso.puede(actor, Permiso.PRESUPUESTO_CONSULTAR);
        int umbral = configuracion.entero(ConfiguracionService.UMBRAL_STOCK);
        List<IndicadorLabDto> filas = new ArrayList<>();
        for (Laboratorio l : d.labs) {
            String n = l.getNombre();
            filas.add(indicador(l.getId(), n, d, x -> n.equalsIgnoreCase(x), l.getId(), verValores, umbral));
        }
        IndicadorLabDto total = indicador(null, "Total", d, x -> true, null, verValores, umbral);
        return new IndicadoresDto(filas, total);
    }

    private IndicadorLabDto indicador(Long labId, String nombre, Datos d, java.util.function.Predicate<String> esLab, Long idLab,
                                      boolean verValores, int umbral) {
        LocalDate hoy = mapper.hoy();
        List<Equipo> eqs = d.equipos.stream().filter(e -> esLab.test(e.getLaboratorio())).toList();
        long equiposN = eqs.stream().filter(e -> !e.esInsumo()).count();
        long insumosN = eqs.stream().filter(Equipo::esInsumo).count();
        long enAlerta = eqs.stream().filter(Equipo::esInsumo).filter(e -> e.getCantidad() != null
                && (e.getCantidad().signum() == 0 || (e.porcentaje() != null && e.porcentaje() <= umbral))).count();
        long fuera = eqs.stream().filter(e -> !e.esInsumo() && e.getEstado() == EstadoEquipo.FUERA_SERVICIO).count();
        List<Mantenimiento> ms = d.mantenimientos.stream().filter(m -> esLab.test(m.getEquipo().getLaboratorio())).toList();
        long mAbiertos = ms.stream().filter(m -> m.getEstado().abierto()).count();
        long mVencidos = ms.stream().filter(m -> m.getEstado() == Mantenimiento.Estado.PROGRAMADO && m.getFechaProgramada() != null
                && m.getFechaProgramada().isBefore(hoy)).count();
        long mRealizados = ms.stream().filter(m -> m.getEstado() == Mantenimiento.Estado.REALIZADO).count();
        List<ReporteNovedad> ns = d.novedades.stream().filter(n -> esLab.test(n.getEquipo().getLaboratorio())).toList();
        long nPorValidar = ns.stream().filter(n -> n.getValidacion() == ReporteNovedad.Validacion.PENDIENTE).count();
        long nAbiertas = ns.stream().filter(n -> n.getValidacion() == ReporteNovedad.Validacion.VALIDADA && n.getEstado() != ReporteNovedad.Estado.RESUELTO).count();
        List<Solicitud> ss = d.solicitudes.stream().filter(s -> idLab == null || s.getLaboratorio().getId().equals(idLab)).toList();
        long sTramite = ss.stream().filter(s -> EN_TRAMITE.contains(s.getEstado())).count();
        long sAprob = ss.stream().filter(s -> "APROBADA".equals(d.decisiones.get(s.getId()))).count();
        long sRech = ss.stream().filter(s -> "RECHAZADA".equals(d.decisiones.get(s.getId()))).count();
        long valorSol = ss.stream().filter(s -> s.getEstado() != Solicitud.Estado.BORRADOR && s.getEstado() != Solicitud.Estado.CANCELADA)
                .mapToLong(s -> s.getValorEstimado() == null ? 0 : s.getValorEstimado()).sum();
        long valorAprob = !verValores ? 0 : ss.stream().mapToLong(s -> s.getValorAprobado() == null ? 0 : s.getValorAprobado()).sum();
        List<Compromiso> cs = d.compromisos.stream().filter(c -> idLab == null || (c.getLaboratorio() != null && c.getLaboratorio().getId().equals(idLab))).toList();
        long cCumplidos = cs.stream().filter(c -> c.getEstado() == Compromiso.Estado.CUMPLIDO).count();
        long cVencidos = cs.stream().filter(c -> c.vencido(hoy)).count();
        long aPend = d.avances.stream().filter(a -> idLab == null || a.getLaboratorio().getId().equals(idLab))
                .filter(a -> a.getVerificacion() == Avance.Verificacion.PENDIENTE).count();
        long docs = idLab != null ? documentos.countByLaboratorioId(idLab) : d.labs.stream().mapToLong(l -> documentos.countByLaboratorioId(l.getId())).sum();
        return new IndicadorLabDto(labId, nombre, equiposN, insumosN, enAlerta, fuera, mAbiertos, mVencidos, mRealizados, nPorValidar, nAbiertas,
                sTramite, sAprob, sRech, valorSol, valorAprob, cs.size(), cCumplidos, cVencidos, aPend, docs);
    }

    // ------------------------------------------------------------------ Tablero

    @Transactional(readOnly = true)
    public TableroDto tablero(Long actorId) {
        Usuario actor = acceso.usuario(actorId);
        acceso.exigir(actor, Permiso.TABLERO_VER);
        Datos d = datos(actor);
        Map<String, Long> porEstado = new LinkedHashMap<>();
        for (Solicitud.Estado e : Solicitud.Estado.values()) {
            long n = d.solicitudes.stream().filter(s -> s.getEstado() == e).count();
            if (n > 0) porEstado.put(e.name(), n);
        }
        LocalDate hoy = mapper.hoy();
        List<MantenimientoDto> proximos = d.mantenimientos.stream()
                .filter(m -> m.getEstado() == Mantenimiento.Estado.PROGRAMADO && m.getFechaProgramada() != null)
                .sorted(Comparator.comparing(Mantenimiento::getFechaProgramada)).limit(8).map(mapper::mantenimiento).toList();
        List<SolicitudResumenDto> recientes = d.solicitudes.stream().limit(8).map(mapper::solicitud)
                .map(r -> acceso.puede(actor, Permiso.PRESUPUESTO_CONSULTAR) ? r : sinValores(r)).toList();
        List<NovedadDto> porValidar = acceso.puede(actor, Permiso.NOVEDAD_VALIDAR)
                ? d.novedades.stream().filter(n -> n.getValidacion() == ReporteNovedad.Validacion.PENDIENTE).limit(8).map(mapper::novedad).toList()
                : d.novedades.stream().limit(8).map(mapper::novedad).toList();
        List<CompromisoDto> comps = d.compromisos.stream()
                .filter(c -> c.getEstado() == Compromiso.Estado.PENDIENTE || c.getEstado() == Compromiso.Estado.EN_CURSO)
                .sorted(Comparator.comparing(Compromiso::getFechaLimite)).limit(8).map(seguimiento::compromiso).toList();
        return new TableroDto(indicadores(actor, d), porEstado, proximos, recientes, porValidar, comps);
    }

    private static SolicitudResumenDto sinValores(SolicitudResumenDto d) {
        return new SolicitudResumenDto(d.id(), d.codigo(), d.laboratorioId(), d.laboratorio(), d.tipo(), d.titulo(), d.prioridadPropuesta(),
                d.prioridadAsignada(), d.ordenPrioridad(), d.valorEstimado(), null, d.estado(), d.estadoTexto(), d.creadoPor(),
                d.creadoPorRol(), d.creadoEn(), d.actualizadoEn(), d.enviadoEn(), null, null);
    }

    // ------------------------------------------------------------------ Historial (trazabilidad)

    @Transactional(readOnly = true)
    public List<HistorialDto> historial(Long actorId, LocalDate desde, LocalDate hasta, String laboratorio, String q) {
        Usuario actor = acceso.usuario(actorId);
        acceso.exigir(actor, Permiso.HISTORIAL_VER);
        Sede sede = acceso.sede(actor);
        Set<String> nombres = acceso.nombres(actor);
        LocalDate h = hasta != null ? hasta : mapper.hoy();
        LocalDate d = desde != null ? desde : h.minusDays(29);
        if (d.isAfter(h)) throw ApiException.badRequest("La fecha inicial no puede ser posterior a la final");
        if (d.isBefore(h.minusDays(366))) throw ApiException.badRequest("Consulta máximo un año de historial a la vez");
        String nq = Texto.normalizar(q);
        return historial.entre(d.atStartOfDay(), h.plusDays(1).atStartOfDay()).stream()
                .filter(ev -> sedes.pertenece(ev.getSede(), sede))
                .filter(ev -> actor.esAdministrador() || (ev.getLaboratorio() != null && nombres.contains(ev.getLaboratorio().toLowerCase(Locale.ROOT))))
                .filter(ev -> Texto.vacio(laboratorio) || laboratorio.trim().equalsIgnoreCase(ev.getLaboratorio()))
                .filter(ev -> nq.isEmpty() || Texto.normalizar(String.join(" ", nz(ev.getPlaca()), nz(ev.getEquipoNombre()),
                        nz(ev.getUsuarioNombre()), nz(ev.getAuxiliarNombre()), nz(ev.getPrestamoCodigo()), nz(ev.getTipo()), nz(ev.getDetalle()))).contains(nq))
                .map(mapper::historial).toList();
    }

    // ------------------------------------------------------------------ Exportaciones CSV

    /** tipo: inventario, mantenimientos, novedades, solicitudes, compromisos, avances, indicadores o historial. */
    @Transactional(readOnly = true)
    public String csv(Long actorId, String tipo, LocalDate desde, LocalDate hasta, String laboratorio) {
        Usuario actor = acceso.usuario(actorId);
        String t = tipo == null ? "" : tipo.trim().toLowerCase(Locale.ROOT);
        if (t.equals("historial")) return csvHistorial(actorId, desde, hasta, laboratorio);
        acceso.exigir(actor, Permiso.REPORTES_GENERAR);
        Datos d = datos(actor);
        LocalDateTime ini = desde == null ? null : desde.atStartOfDay();
        LocalDateTime fin = hasta == null ? null : hasta.plusDays(1).atStartOfDay();
        java.util.function.Predicate<LocalDateTime> enRango = f -> f == null || ((ini == null || !f.isBefore(ini)) && (fin == null || f.isBefore(fin)));
        java.util.function.Predicate<String> lab = n -> Texto.vacio(laboratorio) || laboratorio.trim().equalsIgnoreCase(n);
        boolean verValores = acceso.puede(actor, Permiso.PRESUPUESTO_CONSULTAR);
        StringBuilder sb = new StringBuilder(BOM);
        switch (t) {
            case "inventario" -> {
                fila(sb, "Placa", "Tipo", "Nombre", "Categoría", "Laboratorio", "Ubicación", "Marca", "Modelo", "Serie", "Estado",
                        "Estado físico", "Existencias", "Referencia (100 %)", "% existencias", "Observaciones");
                for (Equipo e : d.equipos) {
                    if (!lab.test(e.getLaboratorio())) continue;
                    fila(sb, e.getPlaca(), e.getTipo().name(), e.getNombre(), e.getCategoria(), e.getLaboratorio(), e.getUbicacion(), e.getMarca(),
                            e.getModelo(), e.getNumeroSerie(), e.getEstado().texto(), e.getEstadoFisico(), Mapper.cantidad(e.getCantidad(), e.getUnidad()),
                            e.getCantidadReferencia() == null ? "" : Mapper.cantidad(e.getCantidadReferencia(), e.getUnidad()),
                            e.porcentaje() == null ? "" : e.porcentaje(), e.getObservaciones());
                }
            }
            case "mantenimientos" -> {
                fila(sb, "Código", "Placa", "Elemento", "Laboratorio", "Tipo", "Estado", "Descripción", "Fecha programada", "Fecha realizada",
                        "Responsable", "Costo", "Resultado", "Registró", "Creado");
                for (Mantenimiento m : d.mantenimientos) {
                    if (!lab.test(m.getEquipo().getLaboratorio()) || !enRango.test(m.getCreadoEn())) continue;
                    fila(sb, m.getCodigo(), m.getEquipo().getPlaca(), m.getEquipo().getNombre(), m.getEquipo().getLaboratorio(), m.getTipo().texto(),
                            m.getEstado().texto(), m.getDescripcion(), m.getFechaProgramada(), m.getFechaRealizada(), m.getResponsable(),
                            verValores ? m.getCosto() : "", m.getResultado(), m.getRegistradoPor(), fecha(m.getCreadoEn()));
                }
            }
            case "novedades" -> {
                fila(sb, "Id", "Fecha", "Placa", "Elemento", "Laboratorio", "Tipo", "Descripción", "Cantidad", "Reportó", "Validación",
                        "Validó", "Observación validación", "Prioridad", "Estado", "Aviso");
                for (ReporteNovedad n : d.novedades) {
                    if (!lab.test(n.getEquipo().getLaboratorio()) || !enRango.test(n.getCreadoEn())) continue;
                    fila(sb, n.getId(), fecha(n.getCreadoEn()), n.getEquipo().getPlaca(), n.getEquipo().getNombre(), n.getEquipo().getLaboratorio(),
                            n.getTipo().texto(), n.getDescripcion(), n.getCantidad() == null ? "" : Mapper.cantidad(n.getCantidad(), n.getUnidad()),
                            n.getReportadoPor(), n.getValidacion().texto(), n.getValidadoPor(), n.getObservacionValidacion(), n.getPrioridad(),
                            n.getEstado().texto(), n.getNotificacion().name());
                }
            }
            case "solicitudes" -> {
                List<String> cab = new ArrayList<>(List.of("Código", "Laboratorio", "Tipo", "Título", "Estado", "Prioridad propuesta",
                        "Prioridad asignada", "Orden", "Valor estimado", "Creó", "Creada", "Enviada", "Decidió", "Decisión / motivo", "Cierre"));
                if (verValores) cab.addAll(List.of("Rubro", "Fuente", "CDP", "Valor aprobado", "Modalidad", "Contrato", "Contratista",
                        "Valor contratado", "Estado contractual"));
                fila(sb, cab.toArray());
                for (Solicitud s : d.solicitudes) {
                    if (!lab.test(s.getLaboratorio().getNombre()) || !enRango.test(s.getCreadoEn())) continue;
                    List<Object> f = new ArrayList<>(List.of(s.getCodigo(), s.getLaboratorio().getNombre(), nz(s.getTipo()), s.getTitulo(),
                            s.getEstado().texto(), nz(s.getPrioridadPropuesta()), nz(s.getPrioridadAsignada()),
                            s.getOrdenPrioridad() == null ? "" : s.getOrdenPrioridad(), s.getValorEstimado() == null ? "" : s.getValorEstimado(),
                            s.getCreadoPor().getNombre(), fecha(s.getCreadoEn()), fecha(s.getEnviadoEn()), nz(s.getDecididoPor()),
                            nz(s.getDecisionMotivo()), nz(s.getCierreNota())));
                    if (verValores) f.addAll(List.of(nz(s.getRubro()), nz(s.getFuenteFinanciacion()), nz(s.getCdpNumero()),
                            s.getValorAprobado() == null ? "" : s.getValorAprobado(), nz(s.getModalidadContratacion()), nz(s.getContratoNumero()),
                            nz(s.getContratista()), s.getValorContratado() == null ? "" : s.getValorContratado(), s.getEstadoContractual().texto()));
                    fila(sb, f.toArray());
                }
            }
            case "compromisos" -> {
                fila(sb, "Id", "Laboratorio", "Título", "Responsable", "Fecha límite", "Estado", "Vencido", "Avances", "Último %", "Creó");
                for (Compromiso c : d.compromisos) {
                    String n = c.getLaboratorio() == null ? "Toda la sede" : c.getLaboratorio().getNombre();
                    if (!lab.test(n)) continue;
                    CompromisoDto x = seguimiento.compromiso(c);
                    fila(sb, x.id(), n, x.titulo(), nz(x.responsable()), x.fechaLimite(), x.estadoTexto(), x.vencido() ? "Sí" : "No", x.avances(),
                            x.ultimoPorcentaje() == null ? "" : x.ultimoPorcentaje(), x.creadoPor());
                }
            }
            case "avances" -> {
                fila(sb, "Id", "Fecha", "Laboratorio", "Compromiso", "Solicitud", "Descripción", "%", "Autor", "Verificación", "Verificó",
                        "Observación", "Evidencias");
                for (Avance a : d.avances) {
                    if (!lab.test(a.getLaboratorio().getNombre()) || !enRango.test(a.getCreadoEn())) continue;
                    fila(sb, a.getId(), fecha(a.getCreadoEn()), a.getLaboratorio().getNombre(), a.getCompromiso() == null ? "" : a.getCompromiso().getTitulo(),
                            a.getSolicitud() == null ? "" : a.getSolicitud().getCodigo(), a.getDescripcion(), a.getPorcentaje() == null ? "" : a.getPorcentaje(),
                            a.getAutor(), a.getVerificacion().texto(), nz(a.getVerificadoPor()), nz(a.getObservacionVerificacion()), a.getEvidencias().size());
                }
            }
            case "indicadores" -> {
                fila(sb, "Laboratorio", "Equipos", "Insumos", "Insumos en alerta", "Equipos fuera de servicio", "Mantenimientos abiertos",
                        "Mantenimientos vencidos", "Mantenimientos realizados", "Novedades por validar", "Novedades abiertas", "Solicitudes en trámite",
                        "Solicitudes aprobadas", "Solicitudes rechazadas", "Valor solicitado", verValores ? "Valor aprobado" : "",
                        "Compromisos", "Compromisos cumplidos", "Compromisos vencidos", "Avances por verificar", "Documentos");
                IndicadoresDto ind = indicadores(actor, d);
                List<IndicadorLabDto> filas = new ArrayList<>(ind.laboratorios());
                filas.add(ind.total());
                for (IndicadorLabDto i : filas) {
                    fila(sb, i.laboratorio(), i.equipos(), i.insumos(), i.insumosEnAlerta(), i.equiposFueraServicio(), i.mantenimientosAbiertos(),
                            i.mantenimientosVencidos(), i.mantenimientosRealizados(), i.novedadesPorValidar(), i.novedadesAbiertas(),
                            i.solicitudesEnTramite(), i.solicitudesAprobadas(), i.solicitudesRechazadas(), i.valorSolicitado(),
                            verValores ? i.valorAprobado() : "", i.compromisos(), i.compromisosCumplidos(), i.compromisosVencidos(),
                            i.avancesPendientes(), i.documentos());
                }
            }
            default -> throw ApiException.badRequest("Reporte no válido: inventario, mantenimientos, novedades, solicitudes, compromisos, avances, indicadores o historial");
        }
        return sb.toString();
    }

    private String csvHistorial(Long actorId, LocalDate desde, LocalDate hasta, String laboratorio) {
        StringBuilder sb = new StringBuilder(BOM);
        fila(sb, "Fecha", "Tipo", "Placa", "Elemento", "Laboratorio", "Referencia", "Involucrado", "Registró", "Detalle");
        for (HistorialDto ev : historial(actorId, desde, hasta, laboratorio, null)) {
            fila(sb, ev.fecha().format(FMT), ev.tipo(), ev.placa(), ev.equipo(), ev.laboratorio(), ev.referencia(), ev.involucrado(),
                    ev.actor(), ev.detalle());
        }
        return sb.toString();
    }

    private static void fila(StringBuilder sb, Object... celdas) {
        for (int i = 0; i < celdas.length; i++) {
            if (i > 0) sb.append(';');
            sb.append(Texto.celda(celdas[i]));
        }
        sb.append("\r\n");
    }

    private static String fecha(LocalDateTime f) { return f == null ? "" : f.format(FMT); }
    private static String nz(String s) { return s == null ? "" : s; }
}
