package co.edu.usco.labcontrol.web;

import co.edu.usco.labcontrol.security.AuthUser;
import co.edu.usco.labcontrol.service.*;
import co.edu.usco.labcontrol.web.Dtos.*;
import jakarta.validation.Valid;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.springframework.format.annotation.DateTimeFormat;
import java.time.Duration;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/** Controladores de la API REST agrupados por rol (la autorización está en SecurityConfig). */
public final class Controllers {
    private Controllers() {}

    @RestController
    @RequestMapping("/api")
    public static class PublicoController {
        private final AuthService auth;
        private final SedeService sedes;
        private final ImagenService imagenes;
        private final RegistroService registro;
        public PublicoController(AuthService auth, SedeService sedes, ImagenService imagenes, RegistroService registro) {
            this.auth = auth; this.sedes = sedes; this.imagenes = imagenes; this.registro = registro;
        }

        /** Sedes (pública): la usa la pantalla «Elige tu sede» y la validación de /neiva, /pitalito… */
        @GetMapping("/sedes")
        public List<SedeDto> sedes() { return sedes.publicas(); }

        /** Fotografía subida (pública para que la etiqueta img del catálogo la pueda mostrar). */
        @GetMapping("/imagenes/{id}")
        public ResponseEntity<byte[]> imagen(@PathVariable String id) {
            return imagenes.buscar(id)
                    .map(i -> ResponseEntity.ok()
                            .contentType(MediaType.parseMediaType(i.getTipoContenido()))
                            .cacheControl(CacheControl.maxAge(Duration.ofDays(30)).cachePublic().immutable())
                            .header("X-Content-Type-Options", "nosniff")
                            .body(i.getDatos()))
                    .orElse(ResponseEntity.notFound().build());
        }

        @PostMapping("/auth/login")
        public LoginResponse login(@Valid @RequestBody LoginRequest r) { return auth.login(r); }

        /** Registro de estudiantes, paso 1: datos → código de 6 dígitos al correo @usco.edu.co. */
        @PostMapping("/auth/registro")
        public RegistroIniciadoDto registro(@RequestBody RegistroRequest r) { return registro.iniciar(r); }

        /** Paso 2: código correcto → se crea la cuenta y se devuelve la sesión iniciada. */
        @PostMapping("/auth/registro/verificar")
        public LoginResponse verificar(@RequestBody VerificarRegistroRequest r) { return registro.verificar(r); }

        @PostMapping("/auth/registro/reenviar")
        public RegistroIniciadoDto reenviar(@RequestBody ReenviarCodigoRequest r) { return registro.reenviar(r); }

        /** Datos del usuario de la sesión (el cliente lo usa para validar un token guardado). */
        @GetMapping("/auth/yo")
        public UsuarioDto yo(@AuthenticationPrincipal AuthUser u) { return auth.yo(u.id()); }

        @PostMapping("/auth/cambiar-clave")
        public Map<String, String> cambiarClave(@AuthenticationPrincipal AuthUser u, @RequestBody CambiarClaveRequest r) {
            auth.cambiarClave(u.id(), r);
            return Map.of("mensaje", "Contraseña actualizada");
        }

        @GetMapping("/salud")
        public Map<String, String> salud() { return Map.of("estado", "ok", "servicio", "USCO LabControl"); }
    }

    @RestController
    @RequestMapping("/api/equipos")
    public static class CatalogoController {
        private final CatalogoService catalogo;
        private final LaboratorioService laboratorios;
        public CatalogoController(CatalogoService catalogo, LaboratorioService laboratorios) {
            this.catalogo = catalogo; this.laboratorios = laboratorios;
        }

        /** tipo: EQUIPO, INSUMO o vacío (todos). */
        @GetMapping
        public List<EquipoDto> listar(@AuthenticationPrincipal AuthUser u,
                                      @RequestParam(required = false) String laboratorio,
                                      @RequestParam(required = false) String categoria,
                                      @RequestParam(required = false) String q,
                                      @RequestParam(required = false) String tipo) {
            return catalogo.listar(u.id(), laboratorio, categoria, q, tipo);
        }

        /** Laboratorios activos de la sede (filtros y formularios). */
        @GetMapping("/laboratorios")
        public List<String> laboratorios(@AuthenticationPrincipal AuthUser u) { return laboratorios.nombres(u.id()); }

        @GetMapping("/{placa}")
        public EquipoDetalleDto detalle(@AuthenticationPrincipal AuthUser u, @PathVariable String placa) { return catalogo.detalle(u.id(), placa); }
    }

    @RestController
    @RequestMapping("/api/prestamos")
    public static class UsuarioController {
        private final PrestamoService servicio;
        private final InsumoService insumos;
        public UsuarioController(PrestamoService servicio, InsumoService insumos) { this.servicio = servicio; this.insumos = insumos; }

        /** Pedir una cantidad de un insumo (el auxiliar la aprueba o la rechaza). */
        @PostMapping("/insumos/{placa}/solicitudes")
        public SolicitudInsumoDto solicitarInsumo(@AuthenticationPrincipal AuthUser u, @PathVariable String placa,
                                                  @RequestBody SolicitudInsumoRequest r) {
            return insumos.solicitar(u.id(), placa, r);
        }

        @GetMapping("/insumos/mis")
        public List<SolicitudInsumoDto> misInsumos(@AuthenticationPrincipal AuthUser u) { return insumos.mias(u.id()); }

        @DeleteMapping("/insumos/solicitudes/{id}")
        public SolicitudInsumoDto cancelarInsumo(@AuthenticationPrincipal AuthUser u, @PathVariable Long id) { return insumos.cancelar(u.id(), id); }

        @PostMapping("/reservas")
        public PrestamoDto reservar(@AuthenticationPrincipal AuthUser u, @RequestBody ReservaRequest r) {
            return servicio.reservar(u.id(), r);
        }

        @DeleteMapping("/reservas/{id}")
        public PrestamoDto cancelar(@AuthenticationPrincipal AuthUser u, @PathVariable Long id) {
            return servicio.cancelarReserva(u.id(), id);
        }

        @GetMapping("/mis")
        public List<PrestamoDto> mis(@AuthenticationPrincipal AuthUser u) { return servicio.misPrestamos(u.id()); }
    }

    @RestController
    @RequestMapping("/api/ventanilla")
    public static class VentanillaController {
        private final PrestamoService servicio;
        private final InventarioService inventario;
        private final NovedadService novedades;
        private final InsumoService insumos;
        private final AlertaService alertas;
        public VentanillaController(PrestamoService servicio, InventarioService inventario, NovedadService novedades,
                                    InsumoService insumos, AlertaService alertas) {
            this.servicio = servicio; this.inventario = inventario; this.novedades = novedades;
            this.insumos = insumos; this.alertas = alertas;
        }

        // ---- Solicitudes de insumos: el auxiliar aprueba (se descuenta) o rechaza
        @GetMapping("/insumos/solicitudes")
        public List<SolicitudInsumoDto> solicitudesInsumo(@AuthenticationPrincipal AuthUser u) { return insumos.deSede(u.id()); }

        @PostMapping("/insumos/solicitudes/{id}/aprobar")
        public SolicitudInsumoDto aprobarInsumo(@AuthenticationPrincipal AuthUser u, @PathVariable Long id,
                                                @RequestBody(required = false) ResolverSolicitudRequest r) {
            return insumos.aprobar(u.id(), id, r == null ? null : r.respuesta());
        }

        @PostMapping("/insumos/solicitudes/{id}/rechazar")
        public SolicitudInsumoDto rechazarInsumo(@AuthenticationPrincipal AuthUser u, @PathVariable Long id, @RequestBody ResolverSolicitudRequest r) {
            return insumos.rechazar(u.id(), id, r.respuesta());
        }

        @GetMapping("/alertas")
        public List<AlertaDto> alertas(@AuthenticationPrincipal AuthUser u) { return alertas.listar(u.id()); }

        @GetMapping("/novedades/{id}/seguimiento")
        public List<SeguimientoDto> seguimiento(@AuthenticationPrincipal AuthUser u, @PathVariable Long id) { return novedades.seguimiento(u.id(), id); }

        @PostMapping("/novedades/{id}/seguimiento")
        public NovedadDto seguir(@AuthenticationPrincipal AuthUser u, @PathVariable Long id, @RequestBody SeguimientoRequest r) {
            return novedades.seguir(u.id(), id, r);
        }

        /** El auxiliar reporta que un equipo se agotó, se dañó, se perdió o falla: se avisa al correo institucional. */
        @PostMapping("/equipos/{placa}/novedades")
        public NovedadDto reportar(@AuthenticationPrincipal AuthUser u, @PathVariable String placa, @RequestBody NovedadRequest r) {
            return novedades.reportar(u.id(), placa, r);
        }

        @GetMapping("/novedades")
        public List<NovedadDto> novedades(@AuthenticationPrincipal AuthUser u) { return novedades.listar(u.id()); }

        @GetMapping("/panel")
        public PanelDto panel(@AuthenticationPrincipal AuthUser u) { return servicio.panel(u.id()); }

        @GetMapping("/usuarios/{documento}/solicitudes")
        public SolicitudesUsuarioDto solicitudes(@AuthenticationPrincipal AuthUser u, @PathVariable String documento) {
            return servicio.solicitudes(u.id(), documento);
        }

        @PostMapping("/salida")
        public PrestamoDto salida(@AuthenticationPrincipal AuthUser u, @RequestBody SalidaRequest r) {
            return servicio.salida(u.id(), r);
        }

        @PostMapping("/reservas/{id}/rechazar")
        public PrestamoDto rechazar(@AuthenticationPrincipal AuthUser u, @PathVariable Long id, @RequestBody RechazoRequest r) {
            return servicio.rechazarReserva(u.id(), id, r.motivo());
        }

        @PostMapping("/entrada")
        public EntradaResponse entrada(@AuthenticationPrincipal AuthUser u, @RequestBody EntradaRequest r) {
            return servicio.entrada(u.id(), r);
        }

        @GetMapping("/cierre-turno")
        public CierreTurnoDto cierre(@AuthenticationPrincipal AuthUser u) { return servicio.cierreTurno(u.id()); }

        @PostMapping("/cierre-turno")
        public TransferenciaResponse transferir(@AuthenticationPrincipal AuthUser u, @RequestBody TransferenciaRequest r) {
            return servicio.transferirCustodia(u.id(), r.auxiliarDestinoId());
        }

        /** Inventario operativo: el auxiliar puede enviar a mantenimiento o reactivar un equipo. */
        @PostMapping("/equipos/{placa}/estado")
        public EquipoDto estado(@AuthenticationPrincipal AuthUser u, @PathVariable String placa, @RequestBody EstadoEquipoRequest r) {
            return inventario.cambiarEstado(u.id(), placa, r);
        }
    }

    @RestController
    @RequestMapping("/api/coordinador")
    public static class CoordinadorController {
        private final CoordinadorService servicio;
        private final InventarioService inventario;
        private final UsuariosService usuarios;
        private final AlertaService alertas;
        private final InsumoService insumos;
        public CoordinadorController(CoordinadorService servicio, InventarioService inventario, UsuariosService usuarios,
                                     AlertaService alertas, InsumoService insumos) {
            this.servicio = servicio; this.inventario = inventario; this.usuarios = usuarios; this.alertas = alertas; this.insumos = insumos;
        }

        @GetMapping("/alertas")
        public List<AlertaDto> alertas(@AuthenticationPrincipal AuthUser u) { return alertas.listar(u.id()); }

        /** Solicitudes de insumos de la sede (consulta; las atiende el auxiliar). */
        @GetMapping("/insumos/solicitudes")
        public List<SolicitudInsumoDto> solicitudesInsumo(@AuthenticationPrincipal AuthUser u) { return insumos.deSede(u.id()); }

        @GetMapping("/kpis")
        public KpisDto kpis(@AuthenticationPrincipal AuthUser u,
                            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
                            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta) {
            return servicio.kpis(u.id(), desde, hasta);
        }

        @GetMapping("/historial")
        public List<HistorialDto> historial(@AuthenticationPrincipal AuthUser u,
                                            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
                                            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
                                            @RequestParam(required = false) String laboratorio,
                                            @RequestParam(required = false) String q) {
            return servicio.historial(u.id(), desde, hasta, laboratorio, q);
        }

        @GetMapping(value = "/reporte.csv", produces = "text/csv;charset=UTF-8")
        public ResponseEntity<byte[]> reporte(@AuthenticationPrincipal AuthUser u,
                                              @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
                                              @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
                                              @RequestParam(required = false) String laboratorio) {
            byte[] body = servicio.reporteCsv(u.id(), desde, hasta, laboratorio).getBytes(StandardCharsets.UTF_8);
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"reporte-uso-labcontrol.csv\"")
                    .contentType(MediaType.parseMediaType("text/csv;charset=UTF-8"))
                    .body(body);
        }

        // ---- Inventario (CRUD de equipos)
        @GetMapping("/equipos")
        public List<EquipoAdminDto> equipos(@AuthenticationPrincipal AuthUser u) { return servicio.equiposAdmin(u.id()); }

        @PostMapping("/equipos")
        public EquipoAdminDto crear(@AuthenticationPrincipal AuthUser u, @RequestBody EquipoRequest r) { return inventario.crear(u.id(), r); }

        @PutMapping("/equipos/{placa}")
        public EquipoAdminDto actualizar(@AuthenticationPrincipal AuthUser u, @PathVariable String placa, @RequestBody EquipoRequest r) {
            return inventario.actualizar(u.id(), placa, r);
        }

        @PostMapping("/equipos/{placa}/estado")
        public EquipoDto estado(@AuthenticationPrincipal AuthUser u, @PathVariable String placa, @RequestBody EstadoEquipoRequest r) {
            return inventario.cambiarEstado(u.id(), placa, r);
        }

        @PostMapping("/equipos/{placa}/baja")
        public EquipoAdminDto baja(@AuthenticationPrincipal AuthUser u, @PathVariable String placa, @RequestBody BajaRequest r) {
            return servicio.baja(u.id(), placa, r.motivo());
        }

        // ---- Usuarios
        @GetMapping("/usuarios")
        public List<UsuarioAdminDto> usuarios(@AuthenticationPrincipal AuthUser u) { return usuarios.listar(u.id()); }

        @PostMapping("/usuarios")
        public UsuarioAdminDto crearUsuario(@AuthenticationPrincipal AuthUser u, @RequestBody UsuarioRequest r) { return usuarios.crear(u.id(), r); }

        @PutMapping("/usuarios/{id}/activo")
        public UsuarioAdminDto activo(@AuthenticationPrincipal AuthUser u, @PathVariable Long id, @RequestBody ActivoRequest r) {
            return usuarios.cambiarActivo(u.id(), id, r.activo());
        }

        @PostMapping("/usuarios/{id}/clave")
        public Map<String, String> clave(@PathVariable Long id, @RequestBody ClaveRequest r) {
            usuarios.restablecerClave(id, r.nueva());
            return Map.of("mensaje", "Contraseña restablecida");
        }
    }

    /** Sedes (solo Coordinador general). */
    @RestController
    @RequestMapping("/api/coordinador/sedes")
    public static class SedesController {
        private final SedeService sedes;
        public SedesController(SedeService sedes) { this.sedes = sedes; }

        @GetMapping
        public List<SedeAdminDto> listar() { return sedes.listarAdmin(); }

        @PostMapping
        public SedeAdminDto crear(@RequestBody SedeRequest r) { return sedes.crear(r); }

        @PutMapping("/{slug}")
        public SedeAdminDto actualizar(@PathVariable String slug, @RequestBody SedeRequest r) { return sedes.actualizar(slug, r); }

        @PutMapping("/{slug}/activa")
        public SedeAdminDto activa(@PathVariable String slug, @RequestBody ActivoRequest r) { return sedes.activar(slug, r.activo()); }
    }

    /** Presupuesto por sede (solo Coordinador general). La sede llega en la cabecera X-Sede. */
    @RestController
    @RequestMapping("/api/coordinador/presupuesto")
    public static class PresupuestoController {
        private final PresupuestoService presupuesto;
        public PresupuestoController(PresupuestoService presupuesto) { this.presupuesto = presupuesto; }

        @GetMapping
        public PresupuestoDto sede(@AuthenticationPrincipal AuthUser u) { return presupuesto.deSede(u.id()); }

        @GetMapping("/resumen")
        public ResumenPresupuestoDto resumen() { return presupuesto.resumen(); }

        @GetMapping("/categorias")
        public List<String> categorias() { return PresupuestoService.CATEGORIAS; }

        @PostMapping("/movimientos")
        public MovimientoDto registrar(@AuthenticationPrincipal AuthUser u, @RequestBody MovimientoRequest r) {
            return presupuesto.registrar(u.id(), r);
        }

        @PostMapping("/movimientos/{id}/anular")
        public MovimientoDto anular(@AuthenticationPrincipal AuthUser u, @PathVariable Long id, @RequestBody AnularRequest r) {
            return presupuesto.anular(u.id(), id, r.motivo());
        }
    }

    /** Subida de fotografías desde el computador o el celular (solo Coordinador). */
    @RestController
    @RequestMapping("/api/coordinador/imagenes")
    public static class ImagenesController {
        private final ImagenService imagenes;
        public ImagenesController(ImagenService imagenes) { this.imagenes = imagenes; }

        @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
        public ImagenSubidaDto subir(@AuthenticationPrincipal AuthUser u, @RequestParam("archivo") MultipartFile archivo) {
            return imagenes.subir(u.id(), archivo);
        }
    }

    /** Novedades reportadas por los auxiliares de la sede abierta, con el resultado del aviso (Coordinador). */
    @RestController
    @RequestMapping("/api/coordinador/novedades")
    public static class NovedadesController {
        private final NovedadService novedades;
        private final ResumenDiarioService resumen;
        private final SedeContexto sedes;
        public NovedadesController(NovedadService novedades, ResumenDiarioService resumen, SedeContexto sedes) {
            this.novedades = novedades; this.resumen = resumen; this.sedes = sedes;
        }

        @GetMapping
        public List<NovedadDto> listar(@AuthenticationPrincipal AuthUser u) { return novedades.listar(u.id()); }

        /** Vista «Reportes»: todos los reportes de la sede. */
        @GetMapping("/todas")
        public List<NovedadDto> todas(@AuthenticationPrincipal AuthUser u) { return novedades.todos(u.id()); }

        /** Huella liviana para actualizar la vista en tiempo real (el navegador la consulta cada pocos segundos). */
        @GetMapping("/version")
        public VersionReportesDto version(@AuthenticationPrincipal AuthUser u) { return novedades.version(u.id()); }

        /** ahora=true: envía el correo de inmediato aunque la prioridad no sea ALTA. */
        @PostMapping("/{id}/reenviar")
        public NovedadDto reenviar(@AuthenticationPrincipal AuthUser u, @PathVariable Long id,
                                   @RequestParam(defaultValue = "false") boolean ahora) {
            return novedades.reenviar(u.id(), id, ahora);
        }

        @GetMapping("/{id}/seguimiento")
        public List<SeguimientoDto> seguimiento(@AuthenticationPrincipal AuthUser u, @PathVariable Long id) { return novedades.seguimiento(u.id(), id); }

        @PostMapping("/{id}/seguimiento")
        public NovedadDto seguir(@AuthenticationPrincipal AuthUser u, @PathVariable Long id, @RequestBody SeguimientoRequest r) {
            return novedades.seguir(u.id(), id, r);
        }

        /** Envía ya el resumen diario de la sede abierta (para probarlo sin esperar a la hora programada). */
        @PostMapping("/resumen")
        public ResumenEnviadoDto enviarResumen(@AuthenticationPrincipal AuthUser u) { return resumen.enviar(sedes.de(u.id()).getId(), true); }
    }

    /** Laboratorios de la sede abierta (solo Coordinador). */
    @RestController
    @RequestMapping("/api/coordinador/laboratorios")
    public static class LaboratoriosController {
        private final LaboratorioService laboratorios;
        public LaboratoriosController(LaboratorioService laboratorios) { this.laboratorios = laboratorios; }

        @GetMapping
        public List<LaboratorioDto> listar(@AuthenticationPrincipal AuthUser u) { return laboratorios.listar(u.id()); }

        @PostMapping
        public LaboratorioDto crear(@AuthenticationPrincipal AuthUser u, @RequestBody LaboratorioRequest r) { return laboratorios.crear(u.id(), r); }

        @PutMapping("/{id}")
        public LaboratorioDto actualizar(@AuthenticationPrincipal AuthUser u, @PathVariable Long id, @RequestBody LaboratorioRequest r) {
            return laboratorios.actualizar(u.id(), id, r);
        }

        @PutMapping("/{id}/activo")
        public LaboratorioDto activo(@AuthenticationPrincipal AuthUser u, @PathVariable Long id, @RequestBody ActivoRequest r) {
            return laboratorios.activar(u.id(), id, r.activo());
        }
    }
}
