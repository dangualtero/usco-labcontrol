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
        public PublicoController(AuthService auth, SedeService sedes, ImagenService imagenes) {
            this.auth = auth; this.sedes = sedes; this.imagenes = imagenes;
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
        public CatalogoController(CatalogoService catalogo) { this.catalogo = catalogo; }

        @GetMapping
        public List<EquipoDto> listar(@AuthenticationPrincipal AuthUser u,
                                      @RequestParam(required = false) String laboratorio,
                                      @RequestParam(required = false) String categoria,
                                      @RequestParam(required = false) String q) {
            return catalogo.listar(u.id(), laboratorio, categoria, q);
        }

        @GetMapping("/{placa}")
        public EquipoDetalleDto detalle(@AuthenticationPrincipal AuthUser u, @PathVariable String placa) { return catalogo.detalle(u.id(), placa); }
    }

    @RestController
    @RequestMapping("/api/prestamos")
    public static class UsuarioController {
        private final PrestamoService servicio;
        public UsuarioController(PrestamoService servicio) { this.servicio = servicio; }

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
        public VentanillaController(PrestamoService servicio, InventarioService inventario) {
            this.servicio = servicio; this.inventario = inventario;
        }

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
        public CoordinadorController(CoordinadorService servicio, InventarioService inventario, UsuariosService usuarios) {
            this.servicio = servicio; this.inventario = inventario; this.usuarios = usuarios;
        }

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
}
