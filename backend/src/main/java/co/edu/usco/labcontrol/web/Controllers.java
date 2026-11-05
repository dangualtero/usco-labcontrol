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
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/** Controladores de la API REST agrupados por rol (la autorización está en SecurityConfig). */
public final class Controllers {
    private Controllers() {}

    @RestController
    @RequestMapping("/api")
    public static class PublicoController {
        private final AuthService auth;
        public PublicoController(AuthService auth) { this.auth = auth; }

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
        public List<EquipoDto> listar(@RequestParam(required = false) String laboratorio,
                                      @RequestParam(required = false) String categoria,
                                      @RequestParam(required = false) String q) {
            return catalogo.listar(laboratorio, categoria, q);
        }

        @GetMapping("/{placa}")
        public EquipoDetalleDto detalle(@PathVariable String placa) { return catalogo.detalle(placa); }
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
        public PanelDto panel() { return servicio.panel(); }

        @GetMapping("/usuarios/{documento}/solicitudes")
        public SolicitudesUsuarioDto solicitudes(@PathVariable String documento) { return servicio.solicitudes(documento); }

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
        public KpisDto kpis(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
                            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta) {
            return servicio.kpis(desde, hasta);
        }

        @GetMapping("/historial")
        public List<HistorialDto> historial(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
                                            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
                                            @RequestParam(required = false) String laboratorio,
                                            @RequestParam(required = false) String q) {
            return servicio.historial(desde, hasta, laboratorio, q);
        }

        @GetMapping(value = "/reporte.csv", produces = "text/csv;charset=UTF-8")
        public ResponseEntity<byte[]> reporte(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
                                              @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
                                              @RequestParam(required = false) String laboratorio) {
            byte[] body = servicio.reporteCsv(desde, hasta, laboratorio).getBytes(StandardCharsets.UTF_8);
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"reporte-uso-labcontrol.csv\"")
                    .contentType(MediaType.parseMediaType("text/csv;charset=UTF-8"))
                    .body(body);
        }

        // ---- Inventario (CRUD de equipos)
        @GetMapping("/equipos")
        public List<EquipoAdminDto> equipos() { return servicio.equiposAdmin(); }

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
        public List<UsuarioAdminDto> usuarios() { return usuarios.listar(); }

        @PostMapping("/usuarios")
        public UsuarioAdminDto crearUsuario(@RequestBody UsuarioRequest r) { return usuarios.crear(r); }

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
}
