package co.edu.usco.labcontrol;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import co.edu.usco.labcontrol.domain.Permiso;
import co.edu.usco.labcontrol.repo.LaboratorioRepository;
import co.edu.usco.labcontrol.repo.UsuarioRepository;
import co.edu.usco.labcontrol.service.*;
import co.edu.usco.labcontrol.web.Dtos.*;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.TestPropertySource;

/** Los tres roles, la matriz de permisos y el alcance por laboratorio asignado. */
@SpringBootTest
@TestPropertySource(properties = "spring.datasource.url=jdbc:h2:mem:labcontrol-roles;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1")
class RolesPermisosTest {
    @MockBean CorreoService correo;
    @Autowired AuthService auth;
    @Autowired UsuariosService usuariosSrv;
    @Autowired PermisoService permisos;
    @Autowired InventarioService inventario;
    @Autowired LaboratorioService laboratoriosSrv;
    @Autowired ImagenService imagenes;
    @Autowired UsuarioRepository usuarios;
    @Autowired LaboratorioRepository laboratorios;

    Long id(String correo) { return usuarios.findByCorreoIgnoreCase(correo).orElseThrow().getId(); }
    Long lab(String nombre) { return laboratorios.findAll().stream().filter(l -> l.getNombre().equals(nombre)).findFirst().orElseThrow().getId(); }

    @Test
    void cadaRolIngresaConSusPermisosYLaboratorios() {
        UsuarioDto admin = auth.login(new LoginRequest("admin@usco.edu.co", DatosDePrueba.CLAVE, false)).usuario();
        assertThat(admin.rol()).isEqualTo("ADMINISTRADOR");
        assertThat(admin.sede()).isNull();
        assertThat(admin.permisos()).contains("PARAMETRIZAR", "USUARIOS_GESTIONAR", "SOLICITUD_REVISAR", "PRESUPUESTO_GESTIONAR", "AVANCES_VERIFICAR");

        UsuarioDto coord = auth.login(new LoginRequest("coordinador@usco.edu.co", DatosDePrueba.CLAVE, false, "neiva")).usuario();
        assertThat(coord.rolTexto()).isEqualTo("Coordinador de Laboratorio");
        assertThat(coord.laboratorios()).extracting(LaboratorioRefDto::nombre).containsExactly("Electrónica", "Petróleos");
        assertThat(coord.permisos()).contains("SOLICITUD_ENVIAR", "NOVEDAD_VALIDAR", "CARACTERIZACION_EDITAR", "PRESUPUESTO_CONSULTAR")
                .doesNotContain("PARAMETRIZAR", "SOLICITUD_REVISAR", "PRESUPUESTO_GESTIONAR");

        UsuarioDto monitor = auth.login(new LoginRequest("monitor@usco.edu.co", DatosDePrueba.CLAVE, false)).usuario();
        assertThat(monitor.permisos()).contains("INVENTARIO_CONSULTAR", "NOVEDAD_REPORTAR", "SOLICITUD_CREAR", "AVANCES_REGISTRAR")
                .doesNotContain("NOVEDAD_VALIDAR", "SOLICITUD_ENVIAR", "INVENTARIO_GESTIONAR", "PRESUPUESTO_CONSULTAR");
    }

    @Test
    void elCoordinadorYElMonitorSoloVenSusLaboratorios() {
        List<String> labsCoord = inventario.listar(id("coordinador@usco.edu.co"), null, null, null, false).stream()
                .map(e -> e.equipo().laboratorio()).distinct().toList();
        assertThat(labsCoord).containsExactlyInAnyOrder("Electrónica", "Petróleos");
        assertThat(inventario.listar(id("monitor.civil@usco.edu.co"), null, null, null, false))
                .allSatisfy(e -> assertThat(e.equipo().laboratorio()).isEqualTo("Civil"));
        assertThat(inventario.listar(id("admin@usco.edu.co"), null, null, null, false).stream().map(e -> e.equipo().laboratorio()).distinct())
                .contains("Electrónica", "Petróleos", "Civil", "Agrícola");
        // Hoja de vida de un equipo de otro laboratorio: no existe para él
        assertThatThrownBy(() -> inventario.hojaVida(id("monitor@usco.edu.co"), "USCO-EQ-300")).hasMessageContaining("no existe");
        assertThat(inventario.hojaVida(id("monitor@usco.edu.co"), "USCO-EQ-102").equipo().equipo().nombre()).contains("Osciloscopio");
        // Caracterización: el coordinador ve y edita la de su laboratorio, no la de otro
        assertThat(laboratoriosSrv.detalle(id("coordinador@usco.edu.co"), lab("Electrónica")).laboratorio().nombre()).isEqualTo("Electrónica");
        assertThatThrownBy(() -> laboratoriosSrv.detalle(id("coordinador@usco.edu.co"), lab("Civil"))).hasMessageContaining("no está asignado");
        assertThatThrownBy(() -> laboratoriosSrv.caracterizar(id("monitor@usco.edu.co"), lab("Electrónica"),
                new CaracterizacionRequest(null, null, 30, null, null, null, null, null, null, null, null))).hasMessageContaining("permiso");
    }

    @Test
    void elMonitorActualizaLaInformacionOperativaPeroNoLaFicha() {
        Long monitor = id("monitor@usco.edu.co");
        EquipoAdminDto e = inventario.operativo(monitor, "USCO-EQ-101", new OperativoRequest("Mesa 3", "Regular", "Perilla de amplitud floja"));
        assertThat(e.equipo().ubicacion()).isEqualTo("Mesa 3");
        assertThat(e.equipo().observaciones()).contains("floja");
        assertThat(inventario.hojaVida(monitor, "USCO-EQ-101").eventos()).anySatisfy(h -> assertThat(h.tipo()).isEqualTo("OPERATIVO"));
        assertThatThrownBy(() -> inventario.operativo(monitor, "USCO-EQ-101", new OperativoRequest("Mesa 3", "Inventado", null)))
                .hasMessageContaining("catálogo");
        assertThatThrownBy(() -> inventario.crear(monitor, new EquipoRequest("USCO-EQ-990", "Equipo nuevo", null, null, null, "Multímetros",
                "Electrónica", null, null, null, null, "Bueno", null, "DISPONIBLE"))).hasMessageContaining("permiso");
        assertThatThrownBy(() -> inventario.baja(monitor, "USCO-EQ-101", "Ya no sirve")).hasMessageContaining("permiso");
        byte[] jpg = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 0x10, 'J', 'F', 'I', 'F'};
        assertThat(imagenes.subir(monitor, new MockMultipartFile("archivo", "f.jpg", "image/jpeg", jpg)).url()).startsWith("/api/imagenes/");
    }

    @Test
    void elCoordinadorRegistraEquiposSoloEnSusLaboratoriosYConCategoriasDelCatalogo() {
        Long coord = id("coordinador@usco.edu.co");
        EquipoAdminDto nuevo = inventario.crear(coord, new EquipoRequest("USCO-EQ-901", "Analizador lógico", "Saleae", "Logic 8", null,
                "Equipos de medición", "Electrónica", null, null, List.of("8 canales"), List.of(), "Bueno", null, "DISPONIBLE"));
        assertThat(nuevo.equipo().estado()).isEqualTo("DISPONIBLE");
        assertThatThrownBy(() -> inventario.crear(coord, new EquipoRequest("USCO-EQ-902", "Nivel", null, null, null, "Topografía", "Civil",
                null, null, null, null, "Bueno", null, "DISPONIBLE"))).hasMessageContaining("laboratorio válido");
        assertThatThrownBy(() -> inventario.crear(coord, new EquipoRequest("USCO-EQ-903", "Cosa", null, null, null, "Categoría inventada",
                "Electrónica", null, null, null, null, "Bueno", null, "DISPONIBLE"))).hasMessageContaining("catálogo");
        EquipoAdminDto fuera = inventario.cambiarEstado(coord, "USCO-EQ-901", new EstadoEquipoRequest("FUERA_SERVICIO", "Se quemó el puerto USB"));
        assertThat(fuera.equipo().estado()).isEqualTo("FUERA_SERVICIO");
    }

    @Test
    void elAdministradorConfiguraLaMatrizDePermisosSinTocarLosExclusivos() {
        Long monitor = id("monitor@usco.edu.co");
        try {
            permisos.cambiar(new PermisoCambioRequest("MONITOR", "SOLICITUD_ENVIAR", true));
            assertThat(auth.yo(monitor).permisos()).contains("SOLICITUD_ENVIAR");
            assertThat(permisos.listar()).anySatisfy(p -> {
                assertThat(p.codigo()).isEqualTo("SOLICITUD_ENVIAR");
                assertThat(p.monitor()).isTrue();
                assertThat(p.monitorPorDefecto()).isFalse();
            });
            assertThatThrownBy(() -> permisos.cambiar(new PermisoCambioRequest("COORDINADOR_LAB", "PARAMETRIZAR", true)))
                    .hasMessageContaining("Administrador");
            assertThatThrownBy(() -> permisos.cambiar(new PermisoCambioRequest("ADMINISTRADOR", "TABLERO_VER", false)))
                    .hasMessageContaining("COORDINADOR_LAB y MONITOR");
        } finally {
            permisos.restablecer();
        }
        assertThat(auth.yo(monitor).permisos()).doesNotContain("SOLICITUD_ENVIAR");
        assertThat(Permiso.SOLICITUD_REVISAR.soloAdministrador()).isTrue();
    }

    @Test
    void elAdministradorCreaCuentasConRolYUnoOVariosLaboratorios() {
        Long admin = id("admin@usco.edu.co");
        assertThatThrownBy(() -> usuariosSrv.crear(admin, new UsuarioRequest("Monitor Sin Lab", "monitor.sinlab@usco.edu.co", "MONITOR",
                "1099000001", null, null, "Monitor#2026", List.of()))).hasMessageContaining("al menos un laboratorio");
        assertThatThrownBy(() -> usuariosSrv.crear(admin, new UsuarioRequest("Rol viejo", "viejo@usco.edu.co", "AUXILIAR",
                "1099000002", null, null, "Monitor#2026", List.of(lab("Civil"))))).hasMessageContaining("Rol no válido");
        UsuarioAdminDto nuevo = usuariosSrv.crear(admin, new UsuarioRequest("Coordinadora Agrícola", "coord.agricola@usco.edu.co",
                "COORDINADOR_LAB", "1099000003", "Ing. Agrícola", "3100000000", "Coord#Agri2026", List.of(lab("Agrícola"), lab("Civil"))));
        assertThat(nuevo.laboratorios()).extracting(LaboratorioRefDto::nombre).containsExactly("Agrícola", "Civil");
        assertThat(nuevo.sede()).isEqualTo("neiva");
        assertThat(auth.login(new LoginRequest("coord.agricola@usco.edu.co", "Coord#Agri2026", false)).usuario().laboratorios()).hasSize(2);
        // Un coordinador no gestiona usuarios
        assertThatThrownBy(() -> usuariosSrv.listar(id("coordinador@usco.edu.co"))).hasMessageContaining("permiso");
        // Cambio de rol: pasa a monitor solo de Agrícola
        UsuarioAdminDto cambiado = usuariosSrv.actualizar(admin, nuevo.id(), new UsuarioActualizarRequest("Coordinadora Agrícola", "MONITOR",
                null, null, null, List.of(lab("Agrícola"))));
        assertThat(cambiado.rol()).isEqualTo("MONITOR");
        assertThat(cambiado.laboratorios()).extracting(LaboratorioRefDto::nombre).containsExactly("Agrícola");
    }
}
