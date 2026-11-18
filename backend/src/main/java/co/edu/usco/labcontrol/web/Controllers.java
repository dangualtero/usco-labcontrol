package co.edu.usco.labcontrol.web;

import co.edu.usco.labcontrol.domain.Archivo;
import co.edu.usco.labcontrol.domain.Permiso;
import co.edu.usco.labcontrol.security.AuthUser;
import co.edu.usco.labcontrol.service.*;
import co.edu.usco.labcontrol.web.Dtos.*;
import jakarta.validation.Valid;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

/**
 * API REST organizada por módulos. Todas las rutas (menos login, sedes públicas, imágenes y salud) exigen sesión; los
 * permisos de cada acción se comprueban en los servicios según el rol y la matriz de permisos (Parametrización).
 */
public final class Controllers {
    private Controllers() {}

    private static ResponseEntity<byte[]> csv(String contenido, String nombre) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + nombre + "\"")
                .contentType(MediaType.parseMediaType("text/csv;charset=UTF-8"))
                .body(contenido.getBytes(StandardCharsets.UTF_8));
    }

    // =====================================================================================================
    // Público y sesión
    // =====================================================================================================
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

        /** Fotografía de un equipo (pública para que la etiqueta img del inventario la pueda mostrar). */
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

        /** Datos de la cuenta de la sesión, con sus laboratorios y permisos. */
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

    // =====================================================================================================
    // Tablero de control y parametrización (Administrador)
    // =====================================================================================================
    @RestController
    @RequestMapping("/api/admin")
    public static class AdminController {
        private final UsuariosService usuarios;
        private final SedeService sedes;
        private final PermisoService permisos;
        private final ConfiguracionService configuracion;
        private final ResumenDiarioService resumen;
        private final Acceso acceso;
        public AdminController(UsuariosService usuarios, SedeService sedes, PermisoService permisos, ConfiguracionService configuracion,
                               ResumenDiarioService resumen, Acceso acceso) {
            this.usuarios = usuarios; this.sedes = sedes; this.permisos = permisos; this.configuracion = configuracion;
            this.resumen = resumen; this.acceso = acceso;
        }

        // ---- Usuarios, roles y laboratorios asignados
        @GetMapping("/usuarios")
        public List<UsuarioAdminDto> usuarios(@AuthenticationPrincipal AuthUser u) { return usuarios.listar(u.id()); }

        @PostMapping("/usuarios")
        public UsuarioAdminDto crearUsuario(@AuthenticationPrincipal AuthUser u, @RequestBody UsuarioRequest r) { return usuarios.crear(u.id(), r); }

        @PutMapping("/usuarios/{id}")
        public UsuarioAdminDto actualizarUsuario(@AuthenticationPrincipal AuthUser u, @PathVariable Long id, @RequestBody UsuarioActualizarRequest r) {
            return usuarios.actualizar(u.id(), id, r);
        }

        @PutMapping("/usuarios/{id}/activo")
        public UsuarioAdminDto activo(@AuthenticationPrincipal AuthUser u, @PathVariable Long id, @RequestBody ActivoRequest r) {
            return usuarios.cambiarActivo(u.id(), id, r.activo());
        }

        @PostMapping("/usuarios/{id}/clave")
        public Map<String, String> clave(@AuthenticationPrincipal AuthUser u, @PathVariable Long id, @RequestBody ClaveRequest r) {
            usuarios.restablecerClave(u.id(), id, r.nueva());
            return Map.of("mensaje", "Contraseña restablecida");
        }

        // ---- Sedes
        @GetMapping("/sedes")
        public List<SedeAdminDto> sedes(@AuthenticationPrincipal AuthUser u) {
            acceso.exigir(u.id(), Permiso.SEDES_GESTIONAR);
            return sedes.listarAdmin();
        }

        @PostMapping("/sedes")
        public SedeAdminDto crearSede(@AuthenticationPrincipal AuthUser u, @RequestBody SedeRequest r) {
            acceso.exigir(u.id(), Permiso.SEDES_GESTIONAR);
            return sedes.crear(r);
        }

        @PutMapping("/sedes/{slug}")
        public SedeAdminDto actualizarSede(@AuthenticationPrincipal AuthUser u, @PathVariable String slug, @RequestBody SedeRequest r) {
            acceso.exigir(u.id(), Permiso.SEDES_GESTIONAR);
            return sedes.actualizar(slug, r);
        }

        @PutMapping("/sedes/{slug}/activa")
        public SedeAdminDto activarSede(@AuthenticationPrincipal AuthUser u, @PathVariable String slug, @RequestBody ActivoRequest r) {
            acceso.exigir(u.id(), Permiso.SEDES_GESTIONAR);
            return sedes.activar(slug, r.activo());
        }

        // ---- Matriz de permisos por rol
        @GetMapping("/permisos")
        public List<PermisoDto> permisos(@AuthenticationPrincipal AuthUser u) {
            acceso.exigir(u.id(), Permiso.PARAMETRIZAR);
            return permisos.listar();
        }

        @PutMapping("/permisos")
        public List<PermisoDto> cambiarPermiso(@AuthenticationPrincipal AuthUser u, @RequestBody PermisoCambioRequest r) {
            acceso.exigir(u.id(), Permiso.PARAMETRIZAR);
            return permisos.cambiar(r);
        }

        @PostMapping("/permisos/restablecer")
        public List<PermisoDto> restablecerPermisos(@AuthenticationPrincipal AuthUser u) {
            acceso.exigir(u.id(), Permiso.PARAMETRIZAR);
            return permisos.restablecer();
        }

        // ---- Catálogos, parámetros y plantillas
        @GetMapping("/catalogos")
        public List<CatalogoDto> catalogos(@AuthenticationPrincipal AuthUser u, @RequestParam(required = false) String tipo) {
            acceso.exigir(u.id(), Permiso.PARAMETRIZAR);
            return configuracion.catalogos(tipo);
        }

        @PostMapping("/catalogos")
        public CatalogoDto crearCatalogo(@AuthenticationPrincipal AuthUser u, @RequestBody CatalogoRequest r) {
            acceso.exigir(u.id(), Permiso.PARAMETRIZAR);
            return configuracion.crearCatalogo(r);
        }

        @PutMapping("/catalogos/{id}")
        public CatalogoDto actualizarCatalogo(@AuthenticationPrincipal AuthUser u, @PathVariable Long id, @RequestBody CatalogoRequest r) {
            acceso.exigir(u.id(), Permiso.PARAMETRIZAR);
            return configuracion.actualizarCatalogo(id, r);
        }

        @GetMapping("/parametros")
        public List<ParametroDto> parametros(@AuthenticationPrincipal AuthUser u) {
            acceso.exigir(u.id(), Permiso.PARAMETRIZAR);
            return configuracion.parametros();
        }

        @PutMapping("/parametros/{clave}")
        public List<ParametroDto> cambiarParametro(@AuthenticationPrincipal AuthUser u, @PathVariable String clave, @RequestBody ParametroRequest r) {
            acceso.exigir(u.id(), Permiso.PARAMETRIZAR);
            return configuracion.cambiarParametro(clave, r);
        }

        @GetMapping("/plantillas")
        public List<PlantillaDto> plantillas(@AuthenticationPrincipal AuthUser u) {
            acceso.exigir(u.id(), Permiso.PARAMETRIZAR);
            return configuracion.plantillas();
        }

        @PutMapping("/plantillas/{codigo}")
        public PlantillaDto cambiarPlantilla(@AuthenticationPrincipal AuthUser u, @PathVariable String codigo, @RequestBody PlantillaRequest r) {
            acceso.exigir(u.id(), Permiso.PARAMETRIZAR);
            return configuracion.cambiarPlantilla(codigo, r);
        }

        /** Envía ya el resumen diario de la sede abierta (para probarlo sin esperar a la hora programada). */
        @PostMapping("/resumen")
        public ResumenEnviadoDto enviarResumen(@AuthenticationPrincipal AuthUser u) {
            var actor = acceso.exigir(u.id(), Permiso.PARAMETRIZAR);
            return resumen.enviar(acceso.sede(actor).getId(), true);
        }
    }

    /** Valores activos de un catálogo, para los formularios de cualquier rol. */
    @RestController
    @RequestMapping("/api/catalogos")
    public static class CatalogosController {
        private final ConfiguracionService configuracion;
        private final Acceso acceso;
        public CatalogosController(ConfiguracionService configuracion, Acceso acceso) { this.configuracion = configuracion; this.acceso = acceso; }

        @GetMapping
        public Map<String, List<String>> todos(@AuthenticationPrincipal AuthUser u) {
            acceso.usuario(u.id());
            Map<String, List<String>> out = new java.util.LinkedHashMap<>();
            for (var t : co.edu.usco.labcontrol.domain.Catalogo.Tipo.values()) out.put(t.name(), configuracion.valores(t));
            return out;
        }
    }

    // =====================================================================================================
    // Laboratorios: caracterización y documentación
    // =====================================================================================================
    @RestController
    @RequestMapping("/api/laboratorios")
    public static class LaboratoriosController {
        private final LaboratorioService laboratorios;
        public LaboratoriosController(LaboratorioService laboratorios) { this.laboratorios = laboratorios; }

        @GetMapping
        public List<LaboratorioDto> listar(@AuthenticationPrincipal AuthUser u) { return laboratorios.listar(u.id()); }

        /** Id y nombre de los laboratorios visibles (filtros y formularios). */
        @GetMapping("/referencias")
        public List<LaboratorioRefDto> referencias(@AuthenticationPrincipal AuthUser u) { return laboratorios.referencias(u.id()); }

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

        @GetMapping("/{id}")
        public CaracterizacionDto detalle(@AuthenticationPrincipal AuthUser u, @PathVariable Long id) { return laboratorios.detalle(u.id(), id); }

        @PutMapping("/{id}/caracterizacion")
        public CaracterizacionDto caracterizar(@AuthenticationPrincipal AuthUser u, @PathVariable Long id, @RequestBody CaracterizacionRequest r) {
            return laboratorios.caracterizar(u.id(), id, r);
        }

        @PostMapping(value = "/{id}/documentos", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
        public CaracterizacionDto documento(@AuthenticationPrincipal AuthUser u, @PathVariable Long id, @RequestParam String tipo,
                                            @RequestParam String titulo, @RequestParam(required = false) String descripcion,
                                            @RequestParam("archivo") MultipartFile archivo) {
            return laboratorios.agregarDocumento(u.id(), id, tipo, titulo, descripcion, archivo);
        }

        @DeleteMapping("/{id}/documentos/{documentoId}")
        public CaracterizacionDto quitarDocumento(@AuthenticationPrincipal AuthUser u, @PathVariable Long id, @PathVariable Long documentoId) {
            return laboratorios.quitarDocumento(u.id(), id, documentoId);
        }
    }

    // =====================================================================================================
    // Inventarios y mantenimientos
    // =====================================================================================================
    @RestController
    @RequestMapping("/api/inventario")
    public static class InventarioController {
        private final InventarioService inventario;
        private final ImagenService imagenes;
        private final Acceso acceso;
        public InventarioController(InventarioService inventario, ImagenService imagenes, Acceso acceso) {
            this.inventario = inventario; this.imagenes = imagenes; this.acceso = acceso;
        }

        /** tipo: EQUIPO, INSUMO o vacío. bajas: incluir los dados de baja (requiere gestionar el inventario). */
        @GetMapping
        public List<EquipoAdminDto> listar(@AuthenticationPrincipal AuthUser u, @RequestParam(required = false) String tipo,
                                           @RequestParam(required = false) String laboratorio, @RequestParam(required = false) String q,
                                           @RequestParam(defaultValue = "false") boolean bajas) {
            return inventario.listar(u.id(), tipo, laboratorio, q, bajas);
        }

        @GetMapping("/{placa}")
        public HojaVidaDto hojaVida(@AuthenticationPrincipal AuthUser u, @PathVariable String placa) { return inventario.hojaVida(u.id(), placa); }

        @PostMapping
        public EquipoAdminDto crear(@AuthenticationPrincipal AuthUser u, @RequestBody EquipoRequest r) { return inventario.crear(u.id(), r); }

        @PutMapping("/{placa}")
        public EquipoAdminDto actualizar(@AuthenticationPrincipal AuthUser u, @PathVariable String placa, @RequestBody EquipoRequest r) {
            return inventario.actualizar(u.id(), placa, r);
        }

        @PutMapping("/{placa}/operativo")
        public EquipoAdminDto operativo(@AuthenticationPrincipal AuthUser u, @PathVariable String placa, @RequestBody OperativoRequest r) {
            return inventario.operativo(u.id(), placa, r);
        }

        @PostMapping("/{placa}/estado")
        public EquipoAdminDto estado(@AuthenticationPrincipal AuthUser u, @PathVariable String placa, @RequestBody EstadoEquipoRequest r) {
            return inventario.cambiarEstado(u.id(), placa, r);
        }

        @PostMapping("/{placa}/baja")
        public EquipoAdminDto baja(@AuthenticationPrincipal AuthUser u, @PathVariable String placa, @RequestBody BajaRequest r) {
            return inventario.baja(u.id(), placa, r.motivo());
        }

        /** Fotografía del equipo desde el computador o el celular. */
        @PostMapping(value = "/imagenes", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
        public ImagenSubidaDto subirImagen(@AuthenticationPrincipal AuthUser u, @RequestParam("archivo") MultipartFile archivo) {
            acceso.exigir(u.id(), Permiso.INVENTARIO_GESTIONAR);
            return imagenes.subir(u.id(), archivo);
        }
    }

    @RestController
    @RequestMapping("/api/mantenimientos")
    public static class MantenimientosController {
        private final MantenimientoService mantenimientos;
        public MantenimientosController(MantenimientoService mantenimientos) { this.mantenimientos = mantenimientos; }

        /** estado: REQUERIDO, PROGRAMADO, EN_EJECUCION, REALIZADO, CANCELADO, ABIERTOS o vacío. */
        @GetMapping
        public List<MantenimientoDto> listar(@AuthenticationPrincipal AuthUser u, @RequestParam(required = false) String estado,
                                             @RequestParam(required = false) String laboratorio, @RequestParam(required = false) String placa) {
            return mantenimientos.listar(u.id(), estado, laboratorio, placa);
        }

        @PostMapping
        public MantenimientoDto crear(@AuthenticationPrincipal AuthUser u, @RequestBody MantenimientoRequest r) { return mantenimientos.crear(u.id(), r); }

        @PutMapping("/{id}")
        public MantenimientoDto actualizar(@AuthenticationPrincipal AuthUser u, @PathVariable Long id, @RequestBody MantenimientoRequest r) {
            return mantenimientos.actualizar(u.id(), id, r);
        }

        @PostMapping("/{id}/estado")
        public MantenimientoDto estado(@AuthenticationPrincipal AuthUser u, @PathVariable Long id, @RequestBody MantenimientoEstadoRequest r) {
            return mantenimientos.cambiarEstado(u.id(), id, r);
        }
    }

    /** Novedades: registro preliminar (monitor) y validación (coordinador). */
    @RestController
    @RequestMapping("/api/novedades")
    public static class NovedadesController {
        private final NovedadService novedades;
        public NovedadesController(NovedadService novedades) { this.novedades = novedades; }

        /** validacion: PENDIENTE, VALIDADA, RECHAZADA o vacío. */
        @GetMapping
        public List<NovedadDto> listar(@AuthenticationPrincipal AuthUser u, @RequestParam(required = false) String validacion,
                                       @RequestParam(required = false) String laboratorio, @RequestParam(required = false) String placa) {
            return novedades.listar(u.id(), validacion, laboratorio, placa);
        }

        /** Huella de las novedades: el navegador la consulta cada pocos segundos y recarga solo si cambió. */
        @GetMapping("/version")
        public VersionReportesDto version(@AuthenticationPrincipal AuthUser u) { return novedades.version(u.id()); }

        /** Registro con fotografías y soportes (multipart: tipo, descripcion, mantenimiento, cantidad, unidad, archivos). */
        @PostMapping(value = "/{placa}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
        public NovedadDto reportar(@AuthenticationPrincipal AuthUser u, @PathVariable String placa, @RequestParam String tipo,
                                   @RequestParam String descripcion, @RequestParam(required = false) Boolean mantenimiento,
                                   @RequestParam(required = false) java.math.BigDecimal cantidad, @RequestParam(required = false) String unidad,
                                   @RequestParam(value = "archivos", required = false) List<MultipartFile> archivos) {
            return novedades.reportar(u.id(), placa, new NovedadRequest(tipo, descripcion, mantenimiento, cantidad, unidad), archivos);
        }

        /** Registro sin archivos (JSON). */
        @PostMapping(value = "/{placa}", consumes = MediaType.APPLICATION_JSON_VALUE)
        public NovedadDto reportarJson(@AuthenticationPrincipal AuthUser u, @PathVariable String placa, @RequestBody NovedadRequest r) {
            return novedades.reportar(u.id(), placa, r, List.of());
        }

        @PostMapping("/{id}/validar")
        public NovedadDto validar(@AuthenticationPrincipal AuthUser u, @PathVariable Long id, @RequestBody(required = false) ValidarNovedadRequest r) {
            return novedades.validar(u.id(), id, r);
        }

        @PostMapping("/{id}/rechazar")
        public NovedadDto rechazar(@AuthenticationPrincipal AuthUser u, @PathVariable Long id, @RequestBody ValidarNovedadRequest r) {
            return novedades.rechazar(u.id(), id, r);
        }

        /** ahora=true: envía el correo de inmediato aunque la prioridad no sea ALTA. */
        @PostMapping("/{id}/reenviar")
        public NovedadDto reenviar(@AuthenticationPrincipal AuthUser u, @PathVariable Long id, @RequestParam(defaultValue = "false") boolean ahora) {
            return novedades.reenviar(u.id(), id, ahora);
        }

        @GetMapping("/{id}/seguimiento")
        public List<SeguimientoDto> seguimiento(@AuthenticationPrincipal AuthUser u, @PathVariable Long id) { return novedades.seguimiento(u.id(), id); }

        @PostMapping("/{id}/seguimiento")
        public NovedadDto seguir(@AuthenticationPrincipal AuthUser u, @PathVariable Long id, @RequestBody SeguimientoRequest r) {
            return novedades.seguir(u.id(), id, r);
        }
    }

    // =====================================================================================================
    // Solicitudes y requerimientos
    // =====================================================================================================
    @RestController
    @RequestMapping("/api/solicitudes")
    public static class SolicitudesController {
        private final SolicitudService solicitudes;
        public SolicitudesController(SolicitudService solicitudes) { this.solicitudes = solicitudes; }

        /** estado: un estado, ABIERTAS o vacío. */
        @GetMapping
        public List<SolicitudResumenDto> listar(@AuthenticationPrincipal AuthUser u, @RequestParam(required = false) String estado,
                                                @RequestParam(required = false) Long laboratorioId, @RequestParam(required = false) String q) {
            return solicitudes.listar(u.id(), estado, laboratorioId, q);
        }

        @GetMapping("/{id}")
        public SolicitudDetalleDto detalle(@AuthenticationPrincipal AuthUser u, @PathVariable Long id) { return solicitudes.detalle(u.id(), id); }

        @PostMapping
        public SolicitudDetalleDto crear(@AuthenticationPrincipal AuthUser u, @RequestBody SolicitudRequest r) { return solicitudes.crear(u.id(), r); }

        @PutMapping("/{id}")
        public SolicitudDetalleDto actualizar(@AuthenticationPrincipal AuthUser u, @PathVariable Long id, @RequestBody SolicitudRequest r) {
            return solicitudes.actualizar(u.id(), id, r);
        }

        @PostMapping(value = "/{id}/adjuntos", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
        public SolicitudDetalleDto adjuntar(@AuthenticationPrincipal AuthUser u, @PathVariable Long id, @RequestParam(defaultValue = "SOPORTE") String tipo,
                                            @RequestParam("archivo") MultipartFile archivo) {
            return solicitudes.adjuntar(u.id(), id, tipo, archivo);
        }

        @DeleteMapping("/{id}/adjuntos/{adjuntoId}")
        public SolicitudDetalleDto quitarAdjunto(@AuthenticationPrincipal AuthUser u, @PathVariable Long id, @PathVariable Long adjuntoId) {
            return solicitudes.quitarAdjunto(u.id(), id, adjuntoId);
        }

        @PostMapping("/{id}/enviar")
        public SolicitudDetalleDto enviar(@AuthenticationPrincipal AuthUser u, @PathVariable Long id, @RequestBody(required = false) TextoRequest r) {
            return solicitudes.enviar(u.id(), id, r);
        }

        @PostMapping("/{id}/devolver")
        public SolicitudDetalleDto devolver(@AuthenticationPrincipal AuthUser u, @PathVariable Long id, @RequestBody TextoRequest r) {
            return solicitudes.devolver(u.id(), id, r);
        }

        @PostMapping("/{id}/priorizar")
        public SolicitudDetalleDto priorizar(@AuthenticationPrincipal AuthUser u, @PathVariable Long id, @RequestBody PriorizarRequest r) {
            return solicitudes.priorizar(u.id(), id, r);
        }

        @PostMapping("/{id}/decidir")
        public SolicitudDetalleDto decidir(@AuthenticationPrincipal AuthUser u, @PathVariable Long id, @RequestBody DecisionRequest r) {
            return solicitudes.decidir(u.id(), id, r);
        }

        @PostMapping("/{id}/cerrar")
        public SolicitudDetalleDto cerrar(@AuthenticationPrincipal AuthUser u, @PathVariable Long id, @RequestBody TextoRequest r) {
            return solicitudes.cerrar(u.id(), id, r);
        }

        @PostMapping("/{id}/cancelar")
        public SolicitudDetalleDto cancelar(@AuthenticationPrincipal AuthUser u, @PathVariable Long id, @RequestBody TextoRequest r) {
            return solicitudes.cancelar(u.id(), id, r);
        }

        @PutMapping("/{id}/presupuesto")
        public SolicitudDetalleDto presupuesto(@AuthenticationPrincipal AuthUser u, @PathVariable Long id, @RequestBody PresupuestoRequest r) {
            return solicitudes.presupuesto(u.id(), id, r);
        }

        @PutMapping("/{id}/contratacion")
        public SolicitudDetalleDto contratacion(@AuthenticationPrincipal AuthUser u, @PathVariable Long id, @RequestBody ContratacionRequest r) {
            return solicitudes.contratacion(u.id(), id, r);
        }
    }

    // =====================================================================================================
    // Presupuestos y contratación (presupuesto de la sede)
    // =====================================================================================================
    @RestController
    @RequestMapping("/api/presupuesto")
    public static class PresupuestoController {
        private final PresupuestoService presupuesto;
        private final Acceso acceso;
        public PresupuestoController(PresupuestoService presupuesto, Acceso acceso) { this.presupuesto = presupuesto; this.acceso = acceso; }

        @GetMapping
        public PresupuestoDto sede(@AuthenticationPrincipal AuthUser u) {
            acceso.exigir(u.id(), Permiso.PRESUPUESTO_GESTIONAR);
            return presupuesto.deSede(u.id());
        }

        @GetMapping("/resumen")
        public ResumenPresupuestoDto resumen(@AuthenticationPrincipal AuthUser u) {
            acceso.exigir(u.id(), Permiso.PRESUPUESTO_GESTIONAR);
            return presupuesto.resumen();
        }

        @GetMapping("/categorias")
        public List<String> categorias() { return PresupuestoService.CATEGORIAS; }

        @PostMapping("/movimientos")
        public MovimientoDto registrar(@AuthenticationPrincipal AuthUser u, @RequestBody MovimientoRequest r) {
            acceso.exigir(u.id(), Permiso.PRESUPUESTO_GESTIONAR);
            return presupuesto.registrar(u.id(), r);
        }

        @PostMapping("/movimientos/{id}/anular")
        public MovimientoDto anular(@AuthenticationPrincipal AuthUser u, @PathVariable Long id, @RequestBody AnularRequest r) {
            acceso.exigir(u.id(), Permiso.PRESUPUESTO_GESTIONAR);
            return presupuesto.anular(u.id(), id, r.motivo());
        }
    }

    // =====================================================================================================
    // Reportes y seguimiento
    // =====================================================================================================
    @RestController
    @RequestMapping("/api/seguimiento")
    public static class SeguimientoController {
        private final SeguimientoService seguimiento;
        public SeguimientoController(SeguimientoService seguimiento) { this.seguimiento = seguimiento; }

        /** estado: PENDIENTE, EN_CURSO, CUMPLIDO, CANCELADO, ABIERTOS, VENCIDOS o vacío. */
        @GetMapping("/compromisos")
        public List<CompromisoDto> compromisos(@AuthenticationPrincipal AuthUser u, @RequestParam(required = false) String estado,
                                               @RequestParam(required = false) Long laboratorioId) {
            return seguimiento.compromisos(u.id(), estado, laboratorioId);
        }

        @PostMapping("/compromisos")
        public CompromisoDto crearCompromiso(@AuthenticationPrincipal AuthUser u, @RequestBody CompromisoRequest r) {
            return seguimiento.crearCompromiso(u.id(), r);
        }

        @PutMapping("/compromisos/{id}")
        public CompromisoDto actualizarCompromiso(@AuthenticationPrincipal AuthUser u, @PathVariable Long id, @RequestBody CompromisoRequest r) {
            return seguimiento.actualizarCompromiso(u.id(), id, r);
        }

        @PostMapping("/compromisos/{id}/estado")
        public CompromisoDto estadoCompromiso(@AuthenticationPrincipal AuthUser u, @PathVariable Long id, @RequestBody EstadoRequest r) {
            return seguimiento.estadoCompromiso(u.id(), id, r);
        }

        /** Posibles responsables de un compromiso (laboratorioId vacío = toda la sede). */
        @GetMapping("/personas")
        public List<PersonaDto> personas(@AuthenticationPrincipal AuthUser u, @RequestParam(required = false) Long laboratorioId) {
            return seguimiento.personas(u.id(), laboratorioId);
        }

        @GetMapping("/avances")
        public List<AvanceDto> avances(@AuthenticationPrincipal AuthUser u, @RequestParam(required = false) Long laboratorioId,
                                       @RequestParam(required = false) Long compromisoId, @RequestParam(required = false) Long solicitudId,
                                       @RequestParam(required = false) String verificacion) {
            return seguimiento.avances(u.id(), laboratorioId, compromisoId, solicitudId, verificacion);
        }

        /** Avance con evidencias (multipart: laboratorioId, compromisoId, solicitudId, descripcion, porcentaje, evidencias). */
        @PostMapping(value = "/avances", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
        public AvanceDto registrarAvance(@AuthenticationPrincipal AuthUser u, @RequestParam(required = false) Long laboratorioId,
                                         @RequestParam(required = false) Long compromisoId, @RequestParam(required = false) Long solicitudId,
                                         @RequestParam String descripcion, @RequestParam(required = false) Integer porcentaje,
                                         @RequestParam(value = "evidencias", required = false) List<MultipartFile> evidencias) {
            return seguimiento.registrarAvance(u.id(), new AvanceRequest(laboratorioId, compromisoId, solicitudId, descripcion, porcentaje), evidencias);
        }

        @PostMapping("/avances/{id}/verificar")
        public AvanceDto verificar(@AuthenticationPrincipal AuthUser u, @PathVariable Long id, @RequestBody VerificarRequest r) {
            return seguimiento.verificar(u.id(), id, r);
        }
    }

    @RestController
    @RequestMapping("/api")
    public static class ReportesController {
        private final ReporteService reportes;
        private final AlertaService alertas;
        public ReportesController(ReporteService reportes, AlertaService alertas) { this.reportes = reportes; this.alertas = alertas; }

        @GetMapping("/tablero")
        public TableroDto tablero(@AuthenticationPrincipal AuthUser u) { return reportes.tablero(u.id()); }

        @GetMapping("/reportes/indicadores")
        public IndicadoresDto indicadores(@AuthenticationPrincipal AuthUser u) { return reportes.indicadores(u.id()); }

        /** tipo: inventario, mantenimientos, novedades, solicitudes, compromisos, avances, indicadores o historial. */
        @GetMapping(value = "/reportes/{tipo}.csv", produces = "text/csv;charset=UTF-8")
        public ResponseEntity<byte[]> csv(@AuthenticationPrincipal AuthUser u, @PathVariable String tipo,
                                          @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
                                          @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
                                          @RequestParam(required = false) String laboratorio) {
            String limpio = tipo == null ? "reporte" : tipo.replaceAll("[^a-z]", "");
            return Controllers.csv(reportes.csv(u.id(), tipo, desde, hasta, laboratorio), "labcontrol-" + limpio + "-" + LocalDate.now() + ".csv");
        }

        @GetMapping("/historial")
        public List<HistorialDto> historial(@AuthenticationPrincipal AuthUser u,
                                            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
                                            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
                                            @RequestParam(required = false) String laboratorio, @RequestParam(required = false) String q) {
            return reportes.historial(u.id(), desde, hasta, laboratorio, q);
        }

        @GetMapping("/alertas")
        public List<AlertaDto> alertas(@AuthenticationPrincipal AuthUser u) { return alertas.listar(u.id()); }
    }

    /** Descarga de archivos privados (documentos, cotizaciones, fotografías de novedades y evidencias). */
    @RestController
    @RequestMapping("/api/archivos")
    public static class ArchivosController {
        private final ArchivoService archivos;
        public ArchivosController(ArchivoService archivos) { this.archivos = archivos; }

        @GetMapping("/{id}")
        public ResponseEntity<byte[]> descargar(@AuthenticationPrincipal AuthUser u, @PathVariable String id) {
            Archivo a = archivos.descargar(u.id(), id);
            String nombre = URLEncoder.encode(a.getNombre(), StandardCharsets.UTF_8).replace("+", "%20");
            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType(a.getTipoContenido()))
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + nombre)
                    .header("X-Content-Type-Options", "nosniff")
                    .cacheControl(CacheControl.noStore())
                    .body(a.getDatos());
        }
    }
}
