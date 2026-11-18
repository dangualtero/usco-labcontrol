package co.edu.usco.labcontrol.service;

import co.edu.usco.labcontrol.domain.*;
import co.edu.usco.labcontrol.domain.Solicitud.Estado;
import co.edu.usco.labcontrol.repo.*;
import co.edu.usco.labcontrol.web.ApiException;
import co.edu.usco.labcontrol.web.Dtos.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/**
 * Solicitudes y requerimientos de los laboratorios.
 * <pre>
 * BORRADOR ──(monitor envía)──► EN_REVISION ──(coordinador valida y envía)──► ENVIADA ──► APROBADA / RECHAZADA ──► CERRADA
 *    │                              │                                            │
 *    └──(coordinador envía)─────────┴────────────────────────────────────────────┘
 *                         DEVUELTA ◄──(el coordinador o el Administrador la devuelve para ajuste)
 * </pre>
 * <ul>
 *   <li>SOLICITUD_CREAR: crear y editar sus borradores, adjuntar cotizaciones, fotografías y soportes.</li>
 *   <li>SOLICITUD_ENVIAR: enviarla al Administrador. Sin este permiso (monitor), «enviar» la deja en revisión del coordinador.</li>
 *   <li>SOLICITUD_VALIDAR: validar lo que preparó el monitor (editar, devolver, enviar o cancelar).</li>
 *   <li>SOLICITUD_REVISAR (Administrador): devolver, priorizar, aprobar o rechazar y cerrar.</li>
 *   <li>PRESUPUESTO_CONSULTAR / PRESUPUESTO_GESTIONAR: ver o registrar la información presupuestal y contractual.</li>
 * </ul>
 */
@Service
public class SolicitudService {
    private static final Set<String> PRIORIDADES = Set.of("ALTA", "MEDIA", "BAJA");
    private static final int MAX_ADJUNTOS = 15;

    private final SolicitudRepository solicitudes;
    private final SolicitudArchivoRepository adjuntos;
    private final SolicitudEventoRepository eventos;
    private final AvanceRepository avances;
    private final MovimientoPresupuestoRepository movimientos;
    private final PresupuestoService presupuesto;
    private final ConfiguracionService configuracion;
    private final ArchivoService archivos;
    private final NotificacionService notificaciones;
    private final Acceso acceso;
    private final Mapper mapper;

    public SolicitudService(SolicitudRepository solicitudes, SolicitudArchivoRepository adjuntos, SolicitudEventoRepository eventos,
                            AvanceRepository avances, MovimientoPresupuestoRepository movimientos, PresupuestoService presupuesto,
                            ConfiguracionService configuracion, ArchivoService archivos, NotificacionService notificaciones,
                            Acceso acceso, Mapper mapper) {
        this.solicitudes = solicitudes; this.adjuntos = adjuntos; this.eventos = eventos; this.avances = avances;
        this.movimientos = movimientos; this.presupuesto = presupuesto; this.configuracion = configuracion; this.archivos = archivos;
        this.notificaciones = notificaciones; this.acceso = acceso; this.mapper = mapper;
    }

    // ------------------------------------------------------------------ Consulta

    /**
     * Solicitudes visibles: el Administrador ve las de toda la sede (menos los borradores ajenos y las que siguen en
     * revisión del coordinador); el coordinador y el monitor, las de sus laboratorios (los borradores, solo los propios
     * o, para el coordinador, los de su laboratorio).
     */
    @Transactional(readOnly = true)
    public List<SolicitudResumenDto> listar(Long actorId, String estado, Long laboratorioId, String q) {
        Usuario actor = acceso.usuario(actorId);
        acceso.exigirAlguno(actor, Permiso.SOLICITUD_CREAR, Permiso.SOLICITUD_REVISAR, Permiso.SOLICITUD_VALIDAR);
        String nq = Texto.normalizar(q);
        return solicitudes.findTop500BySedeIdOrderByCreadoEnDesc(acceso.sede(actor).getId()).stream()
                .filter(s -> visible(actor, s))
                .filter(s -> Texto.vacio(estado) || ("ABIERTAS".equalsIgnoreCase(estado)
                        ? s.getEstado() != Estado.CERRADA && s.getEstado() != Estado.CANCELADA && s.getEstado() != Estado.RECHAZADA
                        : s.getEstado().name().equalsIgnoreCase(estado.trim())))
                .filter(s -> laboratorioId == null || s.getLaboratorio().getId().equals(laboratorioId))
                .filter(s -> nq.isEmpty() || Texto.normalizar(s.getCodigo() + " " + s.getTitulo() + " " + s.getTipo() + " "
                        + s.getLaboratorio().getNombre()).contains(nq))
                .sorted(orden(estado))
                .map(s -> resumen(actor, s)).toList();
    }

    @Transactional(readOnly = true)
    public SolicitudDetalleDto detalle(Long actorId, Long id) {
        Usuario actor = acceso.usuario(actorId);
        return detalle(actor, buscar(actor, id));
    }

    // ------------------------------------------------------------------ Elaboración

    @Transactional
    public SolicitudDetalleDto crear(Long actorId, SolicitudRequest r) {
        Usuario actor = acceso.usuario(actorId);
        acceso.exigir(actor, Permiso.SOLICITUD_CREAR);
        Laboratorio lab = acceso.laboratorio(actor, r.laboratorioId());
        if (!lab.isActivo()) throw ApiException.conflict("El laboratorio está inactivo");
        LocalDateTime ahora = mapper.ahora();
        Solicitud s = new Solicitud(codigo(), lab.getSede(), lab, actor, ahora);
        aplicar(actor, s, r, lab);
        solicitudes.save(s);
        evento(s, actor, "CREADA", null, Estado.BORRADOR, null);
        return detalle(actor, s);
    }

    @Transactional
    public SolicitudDetalleDto actualizar(Long actorId, Long id, SolicitudRequest r) {
        Usuario actor = acceso.usuario(actorId);
        Solicitud s = buscar(actor, id);
        if (!puedeEditar(actor, s)) throw ApiException.forbidden("No puedes editar esta solicitud en su estado actual (" + s.getEstado().texto().toLowerCase() + ")");
        Laboratorio lab = r.laboratorioId() == null || r.laboratorioId().equals(s.getLaboratorio().getId())
                ? s.getLaboratorio() : acceso.laboratorio(actor, r.laboratorioId());
        aplicar(actor, s, r, lab);
        evento(s, actor, "EDITADA", null, null, null);
        return detalle(actor, s);
    }

    private void aplicar(Usuario actor, Solicitud s, SolicitudRequest r, Laboratorio lab) {
        String tipo = configuracion.validar(Catalogo.Tipo.TIPO_SOLICITUD, r.tipo(), "el tipo de solicitud");
        String titulo = Texto.obligatorio(r.titulo(), 5, 160, "El título");
        String justificacion = Texto.opcional(r.justificacion(), 4000, "La justificación");
        String specs = Texto.opcional(r.especificaciones(), 4000, "Las especificaciones técnicas");
        BigDecimal cantidad = r.cantidad();
        if (cantidad != null && (cantidad.signum() <= 0 || cantidad.compareTo(new BigDecimal("1000000000")) > 0 || cantidad.stripTrailingZeros().scale() > 3)) {
            throw ApiException.badRequest("La cantidad debe ser mayor que cero (máximo 3 decimales)");
        }
        String unidad = Texto.opcional(r.unidadMedida(), 30, "La unidad de medida");
        String prioridad = Texto.limpio(r.prioridadPropuesta()) == null ? null : r.prioridadPropuesta().trim().toUpperCase(Locale.ROOT);
        if (prioridad != null && !PRIORIDADES.contains(prioridad)) throw ApiException.badRequest("Prioridad no válida: ALTA, MEDIA o BAJA");
        Long valor = r.valorEstimado();
        if (valor != null && (valor < 0 || valor > 100_000_000_000L)) throw ApiException.badRequest("El valor estimado no es válido");
        s.contenido(lab, tipo, titulo, justificacion, specs, cantidad, unidad, prioridad, valor, mapper.ahora());
    }

    // ------------------------------------------------------------------ Adjuntos

    @Transactional
    public SolicitudDetalleDto adjuntar(Long actorId, Long id, String tipo, MultipartFile f) {
        Usuario actor = acceso.usuario(actorId);
        Solicitud s = buscar(actor, id);
        SolicitudArchivo.Tipo t;
        try { t = SolicitudArchivo.Tipo.valueOf(tipo == null ? "SOPORTE" : tipo.trim().toUpperCase()); }
        catch (IllegalArgumentException e) { throw ApiException.badRequest("Tipo de adjunto no válido: COTIZACION, FOTOGRAFIA, SOPORTE o CONTRATO"); }
        if (t == SolicitudArchivo.Tipo.CONTRATO ? !puedePresupuesto(actor, s) : !puedeEditar(actor, s)) {
            throw ApiException.forbidden(t == SolicitudArchivo.Tipo.CONTRATO ? "Solo el Administrador adjunta los documentos contractuales"
                    : "No puedes adjuntar archivos a esta solicitud en su estado actual");
        }
        if (adjuntos.findBySolicitudIdOrderByAgregadoEnAsc(s.getId()).size() >= MAX_ADJUNTOS) {
            throw ApiException.badRequest("La solicitud ya tiene el máximo de " + MAX_ADJUNTOS + " adjuntos");
        }
        ArchivoInfo a = archivos.guardar(actor, f);
        adjuntos.save(new SolicitudArchivo(s, a, t, actor.getNombre(), mapper.ahora()));
        evento(s, actor, "ADJUNTO", null, null, t.texto() + ": " + a.getNombre());
        return detalle(actor, s);
    }

    @Transactional
    public SolicitudDetalleDto quitarAdjunto(Long actorId, Long id, Long adjuntoId) {
        Usuario actor = acceso.usuario(actorId);
        Solicitud s = buscar(actor, id);
        SolicitudArchivo a = adjuntos.findById(adjuntoId == null ? -1L : adjuntoId).filter(x -> x.getSolicitud().getId().equals(s.getId()))
                .orElseThrow(() -> ApiException.notFound("El adjunto no existe"));
        if (a.getTipo() == SolicitudArchivo.Tipo.CONTRATO ? !puedePresupuesto(actor, s) : !puedeEditar(actor, s)) {
            throw ApiException.forbidden("No puedes quitar este adjunto");
        }
        adjuntos.delete(a);
        evento(s, actor, "ADJUNTO_RETIRADO", null, null, a.getTipo().texto() + ": " + a.getArchivo().getNombre());
        return detalle(actor, s);
    }

    // ------------------------------------------------------------------ Flujo

    /** Monitor: la pasa a revisión del coordinador. Coordinador (o quien tenga SOLICITUD_ENVIAR): al Administrador. */
    @Transactional
    public SolicitudDetalleDto enviar(Long actorId, Long id, TextoRequest r) {
        Usuario actor = acceso.usuario(actorId);
        Solicitud s = buscar(actor, id);
        if (!puedeEnviar(actor, s)) throw ApiException.forbidden("No puedes enviar esta solicitud en su estado actual");
        String comentario = Texto.opcional(r == null ? null : r.texto(), 600, "El comentario");
        Estado antes = s.getEstado();
        if (acceso.puede(actor, Permiso.SOLICITUD_ENVIAR)) {
            completa(s);
            s.pasarA(Estado.ENVIADA, mapper.ahora());
            evento(s, actor, "ENVIADA", antes, Estado.ENVIADA, comentario);
            notificaciones.avisar("SOLICITUD_ENVIADA", notificaciones.correosAdministradores(), vars(s, actor, comentario));
        } else {
            if (Texto.limpio(s.getJustificacion()) == null) throw ApiException.badRequest("Escribe la justificación antes de enviarla al coordinador");
            s.pasarA(Estado.EN_REVISION, mapper.ahora());
            evento(s, actor, "EN_REVISION", antes, Estado.EN_REVISION, comentario);
            Set<String> para = notificaciones.correosDe(s.getLaboratorio().getId(), Rol.COORDINADOR_LAB);
            if (para.isEmpty()) para = notificaciones.correosAdministradores();
            notificaciones.avisar("SOLICITUD_EN_REVISION", para, vars(s, actor, comentario));
        }
        return detalle(actor, s);
    }

    /** El Administrador (ENVIADA) o el coordinador (EN_REVISION) la devuelven para ajuste con una observación. */
    @Transactional
    public SolicitudDetalleDto devolver(Long actorId, Long id, TextoRequest r) {
        Usuario actor = acceso.usuario(actorId);
        Solicitud s = buscar(actor, id);
        if (!puedeDevolver(actor, s)) throw ApiException.forbidden("No puedes devolver esta solicitud en su estado actual");
        String obs = Texto.obligatorio(r == null ? null : r.texto(), 5, 1000, "La observación para el ajuste");
        Estado antes = s.getEstado();
        s.devolver(obs, mapper.ahora());
        evento(s, actor, "DEVUELTA", antes, Estado.DEVUELTA, obs);
        Set<String> para = new LinkedHashSet<>(NotificacionService.correos(s.getCreadoPor()));
        if (antes == Estado.ENVIADA) para.addAll(notificaciones.correosDe(s.getLaboratorio().getId(), Rol.COORDINADOR_LAB));
        para.remove(actor.getCorreo());
        notificaciones.avisar("SOLICITUD_DEVUELTA", para, vars(s, actor, obs));
        return detalle(actor, s);
    }

    @Transactional
    public SolicitudDetalleDto priorizar(Long actorId, Long id, PriorizarRequest r) {
        Usuario actor = acceso.usuario(actorId);
        acceso.exigir(actor, Permiso.SOLICITUD_REVISAR);
        Solicitud s = buscar(actor, id);
        if (s.getEstado() != Estado.ENVIADA && s.getEstado() != Estado.APROBADA) throw ApiException.conflict("Solo se priorizan solicitudes enviadas o aprobadas");
        String p = r.prioridad() == null ? "" : r.prioridad().trim().toUpperCase(Locale.ROOT);
        if (!PRIORIDADES.contains(p)) throw ApiException.badRequest("Prioridad no válida: ALTA, MEDIA o BAJA");
        if (r.orden() != null && (r.orden() < 1 || r.orden() > 9999)) throw ApiException.badRequest("El orden debe estar entre 1 y 9999");
        s.priorizar(p, r.orden(), mapper.ahora());
        evento(s, actor, "PRIORIZADA", null, null, "Prioridad " + p + (r.orden() == null ? "" : " · orden " + r.orden())
                + (Texto.limpio(r.comentario()) == null ? "" : ". " + Texto.opcional(r.comentario(), 400, "El comentario")));
        return detalle(actor, s);
    }

    @Transactional
    public SolicitudDetalleDto decidir(Long actorId, Long id, DecisionRequest r) {
        Usuario actor = acceso.usuario(actorId);
        acceso.exigir(actor, Permiso.SOLICITUD_REVISAR);
        Solicitud s = buscar(actor, id);
        if (s.getEstado() != Estado.ENVIADA) throw ApiException.conflict("Solo se deciden solicitudes enviadas al Administrador");
        Estado d;
        try { d = Estado.valueOf(r.decision() == null ? "" : r.decision().trim().toUpperCase()); }
        catch (IllegalArgumentException e) { d = null; }
        if (d != Estado.APROBADA && d != Estado.RECHAZADA) throw ApiException.badRequest("Decisión no válida: APROBADA o RECHAZADA");
        String motivo = d == Estado.RECHAZADA ? Texto.obligatorio(r.motivo(), 5, 1000, "El motivo del rechazo")
                : Texto.opcional(r.motivo(), 1000, "La observación");
        s.decidir(d, motivo, actor.getNombre(), mapper.ahora());
        evento(s, actor, d.name(), Estado.ENVIADA, d, motivo);
        Set<String> para = new LinkedHashSet<>(NotificacionService.correos(s.getCreadoPor()));
        para.addAll(notificaciones.correosDe(s.getLaboratorio().getId(), Rol.COORDINADOR_LAB));
        para.remove(actor.getCorreo());
        Map<String, String> v = vars(s, actor, motivo == null ? "—" : motivo);
        v.put("estado", d == Estado.APROBADA ? "aprobada" : "rechazada");
        notificaciones.avisar("SOLICITUD_DECIDIDA", para, v);
        return detalle(actor, s);
    }

    @Transactional
    public SolicitudDetalleDto cerrar(Long actorId, Long id, TextoRequest r) {
        Usuario actor = acceso.usuario(actorId);
        acceso.exigir(actor, Permiso.SOLICITUD_REVISAR);
        Solicitud s = buscar(actor, id);
        if (s.getEstado() != Estado.APROBADA && s.getEstado() != Estado.RECHAZADA) throw ApiException.conflict("Solo se cierran solicitudes aprobadas o rechazadas");
        String nota = Texto.obligatorio(r == null ? null : r.texto(), 5, 1000, "La nota de cierre");
        Estado antes = s.getEstado();
        s.cerrar(nota, mapper.ahora());
        evento(s, actor, "CERRADA", antes, Estado.CERRADA, nota);
        Set<String> para = new LinkedHashSet<>(NotificacionService.correos(s.getCreadoPor()));
        para.addAll(notificaciones.correosDe(s.getLaboratorio().getId(), Rol.COORDINADOR_LAB));
        para.remove(actor.getCorreo());
        notificaciones.avisar("SOLICITUD_CERRADA", para, vars(s, actor, nota));
        return detalle(actor, s);
    }

    @Transactional
    public SolicitudDetalleDto cancelar(Long actorId, Long id, TextoRequest r) {
        Usuario actor = acceso.usuario(actorId);
        Solicitud s = buscar(actor, id);
        if (!puedeCancelar(actor, s)) throw ApiException.forbidden("No puedes cancelar esta solicitud en su estado actual");
        String motivo = Texto.obligatorio(r == null ? null : r.texto(), 5, 600, "El motivo de la cancelación");
        Estado antes = s.getEstado();
        s.pasarA(Estado.CANCELADA, mapper.ahora());
        evento(s, actor, "CANCELADA", antes, Estado.CANCELADA, motivo);
        return detalle(actor, s);
    }

    // ------------------------------------------------------------------ Presupuesto y contratación (Administrador)

    @Transactional
    public SolicitudDetalleDto presupuesto(Long actorId, Long id, PresupuestoRequest r) {
        Usuario actor = acceso.usuario(actorId);
        Solicitud s = buscar(actor, id);
        if (!puedePresupuesto(actor, s)) throw ApiException.forbidden("Solo el Administrador registra la información presupuestal, en solicitudes enviadas, aprobadas o cerradas");
        String fuente = Texto.limpio(r.fuenteFinanciacion()) == null ? null
                : configuracion.validar(Catalogo.Tipo.FUENTE_FINANCIACION, r.fuenteFinanciacion(), "la fuente de financiación");
        Long valor = r.valorAprobado();
        if (valor != null && (valor < 0 || valor > 100_000_000_000L)) throw ApiException.badRequest("El valor aprobado no es válido");
        s.presupuesto(Texto.opcional(r.rubro(), 120, "El rubro"), fuente, Texto.opcional(r.cdpNumero(), 40, "El número de CDP"),
                r.cdpFecha(), valor, mapper.ahora());
        evento(s, actor, "PRESUPUESTO", null, null, "Rubro: " + nz(s.getRubro()) + " · Fuente: " + nz(s.getFuenteFinanciacion())
                + " · CDP: " + nz(s.getCdpNumero()) + " · Valor aprobado: " + Mapper.pesos(s.getValorAprobado()));
        return detalle(actor, s);
    }

    @Transactional
    public SolicitudDetalleDto contratacion(Long actorId, Long id, ContratacionRequest r) {
        Usuario actor = acceso.usuario(actorId);
        Solicitud s = buscar(actor, id);
        if (!puedePresupuesto(actor, s)) throw ApiException.forbidden("Solo el Administrador registra la información contractual, en solicitudes enviadas, aprobadas o cerradas");
        String modalidad = Texto.limpio(r.modalidad()) == null ? null
                : configuracion.validar(Catalogo.Tipo.MODALIDAD_CONTRATACION, r.modalidad(), "la modalidad de contratación");
        Solicitud.EstadoContractual ec;
        try { ec = Solicitud.EstadoContractual.valueOf(r.estadoContractual() == null ? "SIN_INICIAR" : r.estadoContractual().trim().toUpperCase()); }
        catch (IllegalArgumentException e) { throw ApiException.badRequest("Estado contractual no válido"); }
        Long valor = r.valorContratado();
        if (valor != null && (valor < 0 || valor > 100_000_000_000L)) throw ApiException.badRequest("El valor contratado no es válido");
        if (Boolean.TRUE.equals(r.registrarGasto()) && (valor == null || valor <= 0)) throw ApiException.badRequest("Indica el valor contratado para registrar el gasto");
        if (Boolean.TRUE.equals(r.registrarGasto()) && s.getEstado() != Estado.APROBADA && s.getEstado() != Estado.CERRADA) {
            throw ApiException.conflict("Solo se registra el gasto de solicitudes aprobadas");
        }
        s.contratacion(modalidad, Texto.opcional(r.contratoNumero(), 40, "El número de contrato"), Texto.opcional(r.contratista(), 160, "El contratista"),
                valor, r.fechaContrato(), ec, Texto.opcional(r.observacion(), 1000, "La observación"), mapper.ahora());
        evento(s, actor, "CONTRATACION", null, null, ec.texto() + " · Contrato: " + nz(s.getContratoNumero()) + " · Contratista: "
                + nz(s.getContratista()) + " · Valor: " + Mapper.pesos(valor));
        if (Boolean.TRUE.equals(r.registrarGasto())) {
            presupuesto.gastoDeSolicitud(actor, s, valor, r.categoriaGasto());
            evento(s, actor, "GASTO", null, null, "Gasto registrado en el presupuesto de la sede: " + Mapper.pesos(valor));
        }
        return detalle(actor, s);
    }

    // ------------------------------------------------------------------ Reglas

    boolean visible(Usuario actor, Solicitud s) {
        if (!acceso.ve(actor, s.getLaboratorio())) return false;
        boolean propia = s.getCreadoPor().getId().equals(actor.getId());
        if (propia) return true;
        if (actor.esAdministrador()) return s.getEstado() != Estado.BORRADOR && s.getEstado() != Estado.EN_REVISION;
        if (s.getEstado() == Estado.BORRADOR) return acceso.puede(actor, Permiso.SOLICITUD_VALIDAR);
        return true;
    }

    private boolean propia(Usuario actor, Solicitud s) { return s.getCreadoPor().getId().equals(actor.getId()); }

    private boolean validador(Usuario actor, Solicitud s) {
        return acceso.puede(actor, Permiso.SOLICITUD_VALIDAR) && acceso.ve(actor, s.getLaboratorio());
    }

    private boolean puedeEditar(Usuario actor, Solicitud s) {
        return switch (s.getEstado()) {
            case BORRADOR, DEVUELTA -> (propia(actor, s) && acceso.puede(actor, Permiso.SOLICITUD_CREAR)) || validador(actor, s);
            case EN_REVISION -> validador(actor, s);
            default -> false;
        };
    }

    private boolean puedeEnviar(Usuario actor, Solicitud s) {
        if (acceso.puede(actor, Permiso.SOLICITUD_ENVIAR)) {
            return switch (s.getEstado()) {
                case BORRADOR, DEVUELTA -> propia(actor, s) || validador(actor, s);
                case EN_REVISION -> validador(actor, s);
                default -> false;
            };
        }
        return (s.getEstado() == Estado.BORRADOR || s.getEstado() == Estado.DEVUELTA) && propia(actor, s)
                && acceso.puede(actor, Permiso.SOLICITUD_CREAR);
    }

    private boolean puedeDevolver(Usuario actor, Solicitud s) {
        if (s.getEstado() == Estado.ENVIADA) return acceso.puede(actor, Permiso.SOLICITUD_REVISAR);
        if (s.getEstado() == Estado.EN_REVISION) return validador(actor, s);
        return false;
    }

    private boolean puedeCancelar(Usuario actor, Solicitud s) {
        if (!s.getEstado().editable()) return false;
        if (s.getEstado() == Estado.EN_REVISION) return validador(actor, s) || propia(actor, s);
        return propia(actor, s) || validador(actor, s);
    }

    private boolean puedePresupuesto(Usuario actor, Solicitud s) {
        return acceso.puede(actor, Permiso.PRESUPUESTO_GESTIONAR)
                && (s.getEstado() == Estado.ENVIADA || s.getEstado() == Estado.APROBADA || s.getEstado() == Estado.CERRADA);
    }

    /** Antes de enviarla al Administrador debe estar completa. */
    private static void completa(Solicitud s) {
        StringBuilder falta = new StringBuilder();
        if (Texto.limpio(s.getJustificacion()) == null || s.getJustificacion().length() < 20) falta.append("la justificación (mínimo 20 caracteres), ");
        if (Texto.limpio(s.getEspecificaciones()) == null) falta.append("las especificaciones técnicas, ");
        if (s.getPrioridadPropuesta() == null) falta.append("la prioridad propuesta, ");
        if (s.getValorEstimado() == null) falta.append("el valor estimado, ");
        if (!falta.isEmpty()) throw ApiException.badRequest("Para enviarla al Administrador completa: " + falta.substring(0, falta.length() - 2));
    }

    private Solicitud buscar(Usuario actor, Long id) {
        Solicitud s = solicitudes.findById(id == null ? -1L : id).orElse(null);
        if (s == null || !visible(actor, s)) throw ApiException.notFound("La solicitud no existe o no tienes acceso a ella");
        return s;
    }

    private static Comparator<Solicitud> orden(String estado) {
        if ("ENVIADA".equalsIgnoreCase(estado) || "APROBADA".equalsIgnoreCase(estado)) {
            // Lista de priorización: primero por orden asignado, luego por prioridad y fecha
            return Comparator.<Solicitud, Integer>comparing(s -> s.getOrdenPrioridad() == null ? Integer.MAX_VALUE : s.getOrdenPrioridad())
                    .thenComparing(s -> peso(s.getPrioridadAsignada() != null ? s.getPrioridadAsignada() : s.getPrioridadPropuesta()))
                    .thenComparing(Solicitud::getCreadoEn);
        }
        return Comparator.comparing(Solicitud::getCreadoEn).reversed();
    }

    private static int peso(String p) { return "ALTA".equals(p) ? 0 : "MEDIA".equals(p) ? 1 : "BAJA".equals(p) ? 2 : 3; }

    /** SR-2026-0001, consecutivo por año. */
    private String codigo() {
        String prefijo = "SR-" + mapper.hoy().getYear() + "-";
        long n = solicitudes.countByCodigoStartingWith(prefijo) + 1;
        String c;
        do { c = prefijo + String.format("%04d", n++); } while (solicitudes.existsByCodigo(c));
        return c;
    }

    private void evento(Solicitud s, Usuario actor, String accion, Estado anterior, Estado nuevo, String comentario) {
        String c = comentario == null ? null : comentario.length() > 1000 ? comentario.substring(0, 999) + "…" : comentario;
        eventos.save(new SolicitudEvento(s, mapper.ahora(), actor.getNombre(), accion, anterior, nuevo, c));
    }

    private Map<String, String> vars(Solicitud s, Usuario actor, String comentario) {
        Map<String, String> v = new HashMap<>();
        v.put("codigo", s.getCodigo());
        v.put("titulo", s.getTitulo());
        v.put("laboratorio", s.getLaboratorio().getNombre());
        v.put("tipo", nz(s.getTipo()));
        v.put("prioridad", s.getPrioridadPropuesta() == null ? "—" : s.getPrioridadPropuesta());
        v.put("valor", Mapper.pesos(s.getValorEstimado()));
        v.put("autor", actor.getNombre());
        v.put("comentario", comentario == null ? "—" : comentario);
        v.put("enlace", notificaciones.enlace(s.getSede().getSlug(), "/solicitudes/" + s.getId()));
        return v;
    }

    // ------------------------------------------------------------------ DTO

    private SolicitudResumenDto resumen(Usuario actor, Solicitud s) {
        SolicitudResumenDto d = mapper.solicitud(s);
        if (acceso.puede(actor, Permiso.PRESUPUESTO_CONSULTAR)) return d;
        // Sin permiso de presupuesto: se ocultan el valor aprobado y el estado contractual
        return new SolicitudResumenDto(d.id(), d.codigo(), d.laboratorioId(), d.laboratorio(), d.tipo(), d.titulo(), d.prioridadPropuesta(),
                d.prioridadAsignada(), d.ordenPrioridad(), d.valorEstimado(), null, d.estado(), d.estadoTexto(), d.creadoPor(),
                d.creadoPorRol(), d.creadoEn(), d.actualizadoEn(), d.enviadoEn(), null, null);
    }

    private SolicitudDetalleDto detalle(Usuario actor, Solicitud s) {
        PresupuestoSolicitudDto p = null;
        if (acceso.puede(actor, Permiso.PRESUPUESTO_CONSULTAR)) {
            p = new PresupuestoSolicitudDto(s.getRubro(), s.getFuenteFinanciacion(), s.getCdpNumero(), s.getCdpFecha(), s.getValorAprobado(),
                    s.getModalidadContratacion(), s.getContratoNumero(), s.getContratista(), s.getValorContratado(), s.getFechaContrato(),
                    s.getEstadoContractual().name(), s.getEstadoContractual().texto(), s.getObservacionContractual(),
                    s.getId() == null ? List.of() : movimientos.findBySolicitudIdOrderByFechaDesc(s.getId()).stream().map(mapper::movimiento).toList());
        }
        boolean verContratos = acceso.puede(actor, Permiso.PRESUPUESTO_CONSULTAR);
        List<AdjuntoDto> adj = s.getId() == null ? List.of() : adjuntos.findBySolicitudIdOrderByAgregadoEnAsc(s.getId()).stream()
                .filter(a -> verContratos || a.getTipo() != SolicitudArchivo.Tipo.CONTRATO)
                .map(a -> new AdjuntoDto(a.getId(), a.getTipo().name(), a.getTipo().texto(), ArchivoService.dto(a.getArchivo()),
                        a.getAgregadoPor(), a.getAgregadoEn())).toList();
        List<EventoSolicitudDto> evs = s.getId() == null ? List.of() : eventos.findBySolicitudIdOrderByFechaAsc(s.getId()).stream()
                .filter(e -> verContratos || !(e.getAccion().equals("PRESUPUESTO") || e.getAccion().equals("CONTRATACION") || e.getAccion().equals("GASTO")))
                .map(e -> new EventoSolicitudDto(e.getId(), e.getFecha(), e.getAutor(), e.getAccion(),
                        e.getEstadoAnterior() == null ? null : e.getEstadoAnterior().texto(),
                        e.getEstadoNuevo() == null ? null : e.getEstadoNuevo().texto(), e.getComentario())).toList();
        List<AvanceDto> avs = s.getId() == null ? List.of() : avances.findBySolicitudIdOrderByCreadoEnDesc(s.getId()).stream().map(mapper::avance).toList();
        boolean enviaDirecto = acceso.puede(actor, Permiso.SOLICITUD_ENVIAR);
        AccionesSolicitudDto acciones = new AccionesSolicitudDto(
                puedeEditar(actor, s),
                puedeEnviar(actor, s),
                enviaDirecto ? "Enviar al Administrador" : "Enviar al coordinador para validación",
                puedeDevolver(actor, s),
                acceso.puede(actor, Permiso.SOLICITUD_REVISAR) && (s.getEstado() == Estado.ENVIADA || s.getEstado() == Estado.APROBADA),
                acceso.puede(actor, Permiso.SOLICITUD_REVISAR) && s.getEstado() == Estado.ENVIADA,
                acceso.puede(actor, Permiso.SOLICITUD_REVISAR) && (s.getEstado() == Estado.APROBADA || s.getEstado() == Estado.RECHAZADA),
                puedeCancelar(actor, s),
                puedePresupuesto(actor, s),
                puedeEditar(actor, s) || puedePresupuesto(actor, s),
                acceso.puede(actor, Permiso.AVANCES_REGISTRAR) && s.getEstado() == Estado.APROBADA);
        return new SolicitudDetalleDto(resumen(actor, s), s.getJustificacion(), s.getEspecificaciones(), s.getCantidad(), s.getUnidadMedida(),
                s.getObservacionDevolucion(), s.getDecisionMotivo(), s.getDecididoPor(), s.getDecididoEn(), s.getCierreNota(), s.getCerradoEn(),
                p, adj, evs, avs, acciones);
    }

    private static String nz(String s) { return s == null ? "—" : s; }
}
