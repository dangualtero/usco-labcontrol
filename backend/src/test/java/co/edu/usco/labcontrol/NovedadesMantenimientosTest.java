package co.edu.usco.labcontrol;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import co.edu.usco.labcontrol.repo.EquipoRepository;
import co.edu.usco.labcontrol.repo.UsuarioRepository;
import co.edu.usco.labcontrol.service.*;
import co.edu.usco.labcontrol.web.Dtos.*;
import com.sun.net.httpserver.HttpServer;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Registro preliminar del monitor → validación del coordinador → efectos en el inventario y aviso por n8n.
 * El agente de IA de n8n se simula con un servidor HTTP local.
 */
@SpringBootTest
class NovedadesMantenimientosTest {
    static final List<String> recibidoPorN8n = new CopyOnWriteArrayList<>();
    static final HttpServer n8n;
    static {
        try {
            n8n = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            n8n.createContext("/webhook/labcontrol-novedades", ex -> {
                recibidoPorN8n.add(new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
                byte[] r = "{\"prioridad\":\"ALTA\",\"asunto\":\"[ALTA] Novedad\",\"resumen\":\"Resumen\",\"correoEnviado\":true}"
                        .getBytes(StandardCharsets.UTF_8);
                ex.sendResponseHeaders(200, r.length);
                ex.getResponseBody().write(r);
                ex.close();
            });
            n8n.start();
        } catch (Exception e) { throw new IllegalStateException(e); }
    }

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", () -> "jdbc:h2:mem:labcontrol-novedades;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
        r.add("labcontrol.novedades.n8n-webhook-url", () -> "http://127.0.0.1:" + n8n.getAddress().getPort() + "/webhook/labcontrol-novedades");
    }

    @AfterAll static void apagar() { n8n.stop(0); }

    @MockBean CorreoService correo;
    @Autowired NovedadService novedades;
    @Autowired MantenimientoService mantenimientos;
    @Autowired InventarioService inventario;
    @Autowired AlertaService alertas;
    @Autowired ArchivoService archivos;
    @Autowired UsuarioRepository usuarios;
    @Autowired EquipoRepository equipos;

    Long id(String correo) { return usuarios.findByCorreoIgnoreCase(correo).orElseThrow().getId(); }

    static final byte[] JPG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 0x10, 'J', 'F', 'I', 'F'};

    void esperarN8n(int cuantos) throws InterruptedException {
        for (int i = 0; i < 100 && recibidoPorN8n.size() < cuantos; i++) Thread.sleep(50);
    }

    @Test
    void elMonitorReportaConFotoElCoordinadorValidaYSeAvisaAN8n() throws Exception {
        Long monitor = id("monitor@usco.edu.co");
        Long coord = id("coordinador@usco.edu.co");
        int antes = recibidoPorN8n.size();
        NovedadDto n = novedades.reportar(monitor, "USCO-EQ-102",
                new NovedadRequest("DANADO", "La pantalla del osciloscopio no enciende después de la práctica", true),
                List.of(new MockMultipartFile("archivos", "pantalla.jpg", "image/jpeg", JPG)));
        assertThat(n.validacion()).isEqualTo("PENDIENTE");
        assertThat(n.archivos()).hasSize(1);
        // Hasta que el coordinador valide, el equipo no cambia y no se avisa
        assertThat(equipos.findByPlaca("USCO-EQ-102").orElseThrow().getEstado().name()).isEqualTo("DISPONIBLE");
        Thread.sleep(300);
        assertThat(recibidoPorN8n).hasSize(antes);
        // El monitor no puede validar; el coordinador de Civil no la ve
        assertThatThrownBy(() -> novedades.validar(monitor, n.id(), null)).hasMessageContaining("permiso");
        assertThatThrownBy(() -> novedades.validar(id("coordinador.civil@usco.edu.co"), n.id(), null)).hasMessageContaining("no existe");
        // Las fotografías solo las descarga quien ve el laboratorio
        String archivoId = n.archivos().get(0).id();
        assertThat(archivos.descargar(coord, archivoId).getDatos()).isEqualTo(JPG);
        assertThatThrownBy(() -> archivos.descargar(id("monitor.civil@usco.edu.co"), archivoId)).hasMessageContaining("no encontrado");
        // Aparece como alerta para el coordinador, no para el monitor
        assertThat(alertas.listar(coord)).anySatisfy(a -> assertThat(a.tipo()).isEqualTo("NOVEDAD_POR_VALIDAR"));
        assertThat(alertas.listar(monitor)).noneSatisfy(a -> assertThat(a.tipo()).isEqualTo("NOVEDAD_POR_VALIDAR"));

        NovedadDto v = novedades.validar(coord, n.id(), new ValidarNovedadRequest("Confirmado en revisión visual"));
        assertThat(v.validacion()).isEqualTo("VALIDADA");
        assertThat(v.validadoPor()).isEqualTo("Mauricio Perdomo");
        assertThat(equipos.findByPlaca("USCO-EQ-102").orElseThrow().getEstado().name()).isEqualTo("MANTENIMIENTO");
        List<MantenimientoDto> ms = mantenimientos.listar(coord, "ABIERTOS", null, "USCO-EQ-102");
        assertThat(ms).singleElement().satisfies(m -> {
            assertThat(m.estado()).isEqualTo("REQUERIDO");
            assertThat(m.tipo()).isEqualTo("CORRECTIVO");
            assertThat(m.novedadId()).isEqualTo(n.id());
        });
        esperarN8n(antes + 1);
        assertThat(recibidoPorN8n).hasSize(antes + 1);
        assertThat(recibidoPorN8n.get(antes)).contains("USCO-EQ-102").contains("\"validadoPor\":\"Mauricio Perdomo\"");
        assertThatThrownBy(() -> novedades.validar(coord, n.id(), null)).hasMessageContaining("ya fue");
    }

    @Test
    void elCoordinadorRechazaUnaNovedadSinEfectos() {
        Long monitor = id("monitor@usco.edu.co");
        NovedadDto n = novedades.reportar(monitor, "USCO-EQ-100", new NovedadRequest("PERDIDO", "No encuentro la fuente en la mesa 1 al cierre", false), List.of());
        assertThatThrownBy(() -> novedades.rechazar(id("coordinador@usco.edu.co"), n.id(), new ValidarNovedadRequest(""))).hasMessageContaining("motivo");
        NovedadDto r = novedades.rechazar(id("coordinador@usco.edu.co"), n.id(), new ValidarNovedadRequest("Estaba en préstamo interno del docente"));
        assertThat(r.validacion()).isEqualTo("RECHAZADA");
        assertThat(r.notificacion()).isEqualTo("NO_APLICA");
        assertThat(equipos.findByPlaca("USCO-EQ-100").orElseThrow().getEstado().name()).isEqualTo("DISPONIBLE");
    }

    @Test
    void conteosYConsumosDeInsumosAjustanLasExistenciasAlValidarse() {
        Long monitor = id("monitor@usco.edu.co");
        Long coord = id("coordinador@usco.edu.co");
        // 2 L de alcohol: el monitor reporta un consumo de 500 mL
        NovedadDto consumo = novedades.reportar(monitor, "USCO-IN-101", new NovedadRequest("CONSUMO", "Limpieza de tarjetas", null,
                new BigDecimal("500"), "ML"), List.of());
        assertThat(equipos.findByPlaca("USCO-IN-101").orElseThrow().getCantidad()).isEqualByComparingTo("2");
        novedades.validar(coord, consumo.id(), null);
        assertThat(equipos.findByPlaca("USCO-IN-101").orElseThrow().getCantidad()).isEqualByComparingTo("1.5");
        // Conteo físico: quedan 300 mL → 15 % → alerta de existencias
        NovedadDto conteo = novedades.reportar(monitor, "USCO-IN-101", new NovedadRequest("EXISTENCIAS", "Conteo de fin de semestre", null,
                new BigDecimal("300"), "ML"), List.of());
        NovedadDto v = novedades.validar(coord, conteo.id(), null);
        assertThat(v.notificacion()).isEqualTo("NO_APLICA");
        assertThat(equipos.findByPlaca("USCO-IN-101").orElseThrow().getCantidad()).isEqualByComparingTo("0.3");
        assertThat(alertas.listar(monitor)).anySatisfy(a -> {
            assertThat(a.tipo()).isEqualTo("STOCK_BAJO");
            assertThat(a.placa()).isEqualTo("USCO-IN-101");
        });
        // Validaciones de entrada
        assertThatThrownBy(() -> novedades.reportar(monitor, "USCO-EQ-103", new NovedadRequest("CONSUMO", "Consumo de multímetro", null,
                BigDecimal.ONE, "UNIDAD"), List.of())).hasMessageContaining("solo aplican a insumos");
        assertThatThrownBy(() -> novedades.reportar(monitor, "USCO-IN-101", new NovedadRequest("CONSUMO", "Mucho alcohol", null,
                new BigDecimal("5"), "KG"), List.of())).hasMessageContaining("compatible");
        assertThatThrownBy(() -> novedades.reportar(monitor, "USCO-IN-101", new NovedadRequest("CONSUMO", "Mucho alcohol", null,
                new BigDecimal("9"), "L"), List.of())).hasMessageContaining("supera");
    }

    @Test
    void elCoordinadorProgramaYCierraMantenimientosYQuedaEnLaHojaDeVida() {
        Long monitor = id("monitor@usco.edu.co");
        Long coord = id("coordinador@usco.edu.co");
        // El monitor registra el requerimiento, pero no lo programa
        assertThatThrownBy(() -> mantenimientos.crear(monitor, new MantenimientoRequest("USCO-EQ-103", "PREVENTIVO", "Calibración anual",
                LocalDate.now().plusDays(3), null, null))).hasMessageContaining("coordinador");
        MantenimientoDto req = mantenimientos.crear(monitor, new MantenimientoRequest("USCO-EQ-103", "CALIBRACION", "Calibración anual del multímetro",
                null, null, null));
        assertThat(req.estado()).isEqualTo("REQUERIDO");
        assertThatThrownBy(() -> mantenimientos.cambiarEstado(monitor, req.id(), new MantenimientoEstadoRequest("EN_EJECUCION", null, null, null)))
                .hasMessageContaining("permiso");

        MantenimientoDto prog = mantenimientos.actualizar(coord, req.id(), new MantenimientoRequest(null, null, null,
                LocalDate.now().plusDays(2), "Metrología Huila S.A.S.", 350_000L));
        assertThat(prog.estado()).isEqualTo("PROGRAMADO");
        assertThat(alertas.listar(monitor)).anySatisfy(a -> assertThat(a.tipo()).isEqualTo("MANTENIMIENTO_PROXIMO"));
        mantenimientos.cambiarEstado(coord, req.id(), new MantenimientoEstadoRequest("EN_EJECUCION", null, null, null));
        assertThat(equipos.findByPlaca("USCO-EQ-103").orElseThrow().getEstado().name()).isEqualTo("MANTENIMIENTO");
        MantenimientoDto hecho = mantenimientos.cambiarEstado(coord, req.id(), new MantenimientoEstadoRequest("REALIZADO", LocalDate.now(),
                "Calibrado dentro de tolerancia; certificado adjunto", true));
        assertThat(hecho.estado()).isEqualTo("REALIZADO");
        assertThat(equipos.findByPlaca("USCO-EQ-103").orElseThrow().getEstado().name()).isEqualTo("DISPONIBLE");

        HojaVidaDto hv = inventario.hojaVida(monitor, "USCO-EQ-103");
        assertThat(hv.mantenimientos()).anySatisfy(m -> assertThat(m.codigo()).isEqualTo(req.codigo()));
        assertThat(hv.eventos()).filteredOn(e -> e.tipo().equals("MANTENIMIENTO")).hasSizeGreaterThanOrEqualTo(3);
        assertThatThrownBy(() -> mantenimientos.crear(coord, new MantenimientoRequest("USCO-IN-100", "PREVENTIVO", "Revisión", null, null, null)))
                .hasMessageContaining("insumos");
    }
}
