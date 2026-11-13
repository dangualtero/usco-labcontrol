package co.edu.usco.labcontrol;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import co.edu.usco.labcontrol.domain.Unidad;
import co.edu.usco.labcontrol.repo.UsuarioRepository;
import co.edu.usco.labcontrol.service.*;
import co.edu.usco.labcontrol.web.Dtos.*;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.TestPropertySource;

/** Insumos con cantidades y unidades, solicitudes, alertas del 25 %, laboratorios por sede y seguimiento de reportes. */
@SpringBootTest
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:labcontrol-insumos;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "labcontrol.novedades.n8n-webhook-url="})
class InsumosLaboratoriosReportesTest {
    @Autowired InventarioService inventario;
    @Autowired InsumoService insumos;
    @Autowired AlertaService alertas;
    @Autowired LaboratorioService laboratorios;
    @Autowired CatalogoService catalogo;
    @Autowired PrestamoService prestamos;
    @Autowired NovedadService novedades;
    @Autowired UsuarioRepository usuarios;
    @MockBean CorreoService correo;

    Long id(String correo) { return usuarios.findByCorreoIgnoreCase(correo).orElseThrow().getId(); }

    @Test
    void conversionDeUnidades() {
        assertThat(Unidad.ML.convertirA(new BigDecimal("250"), Unidad.L)).isEqualByComparingTo("0.25");
        assertThat(Unidad.KG.convertirA(new BigDecimal("1.5"), Unidad.G)).isEqualByComparingTo("1500");
        assertThat(Unidad.CM.convertirA(new BigDecimal("150"), Unidad.M)).isEqualByComparingTo("1.5");
        assertThat(Unidad.L.compatible(Unidad.KG)).isFalse();
    }

    @Test
    void elInsumoSeSolicitaPorCantidadElAuxiliarApruebaYAlQuedar25PorCientoSaltaLaAlerta() {
        Long coord = id("coordinador@usco.edu.co");
        Long aux = id("auxiliar@usco.edu.co");
        Long est = id("estudiante@usco.edu.co");

        EquipoAdminDto alcohol = inventario.crear(coord, new EquipoRequest("USCO-IN-010", "Alcohol isopropílico", null, null, null, "Reactivos",
                "Electrónica", null, null, null, null, "Bueno", null, null, new BigDecimal("2"), "L", new BigDecimal("2")));
        assertThat(alcohol.equipo().tipo()).isEqualTo("INSUMO");
        assertThat(alcohol.equipo().porcentaje()).isEqualTo(100);
        // Equipos solo por unidades; insumos necesitan unidad válida
        assertThatThrownBy(() -> inventario.crear(coord, new EquipoRequest("USCO-EQ-950", "Pinza", null, null, null, "c", "Electrónica",
                null, null, null, null, null, null, null, new BigDecimal("2"), "L", null))).hasMessageContaining("unidades");
        assertThatThrownBy(() -> inventario.crear(coord, new EquipoRequest("USCO-IN-011", "Arena", null, null, null, "c", "Civil",
                null, null, null, null, null, null, null, new BigDecimal("5"), "LITROS", null))).hasMessageContaining("unidad");

        // Los insumos no se reservan
        assertThatThrownBy(() -> prestamos.reservar(est, new ReservaRequest("USCO-IN-010", "Circuitos", null,
                java.time.LocalDateTime.now().plusDays(1), java.time.LocalDateTime.now().plusDays(1).plusHours(2)))).hasMessageContaining("insumos");
        assertThat(catalogo.listar(est, null, null, null, "INSUMO")).extracting(EquipoDto::placa).contains("USCO-IN-010");
        assertThat(catalogo.listar(est, null, null, null, "EQUIPO")).extracting(EquipoDto::placa).doesNotContain("USCO-IN-010");

        // 1500 mL de 2 L → quedan 0,5 L = 25 %
        assertThatThrownBy(() -> insumos.solicitar(est, "USCO-IN-010", new SolicitudInsumoRequest(new BigDecimal("3"), "KG", "Limpieza de tarjetas", null)))
                .hasMessageContaining("compatible");
        assertThatThrownBy(() -> insumos.solicitar(est, "USCO-IN-010", new SolicitudInsumoRequest(new BigDecimal("3"), "L", "Limpieza de tarjetas", null)))
                .hasMessageContaining("No hay suficiente");
        SolicitudInsumoDto s = insumos.solicitar(est, "USCO-IN-010", new SolicitudInsumoRequest(new BigDecimal("1500"), "ML", "Limpieza de tarjetas", "Circuitos"));
        assertThat(s.estado()).isEqualTo("PENDIENTE");
        assertThat(s.cantidadEquivalente()).isEqualByComparingTo("1.5");
        assertThat(alertas.listar(aux)).extracting(AlertaDto::tipo).contains("SOLICITUD_PENDIENTE");

        SolicitudInsumoDto ok = insumos.aprobar(aux, s.id(), "Entregado en la ventanilla");
        assertThat(ok.estado()).isEqualTo("APROBADA");
        assertThat(ok.existencias()).isEqualByComparingTo("0.5");
        assertThat(alertas.listar(aux)).anySatisfy(a -> {
            assertThat(a.tipo()).isEqualTo("STOCK_BAJO");
            assertThat(a.placa()).isEqualTo("USCO-IN-010");
            assertThat(a.porcentaje()).isEqualTo(25);
        });
        assertThatThrownBy(() -> insumos.aprobar(aux, s.id(), null)).hasMessageContaining("ya fue atendida");

        // Rechazo y cancelación
        SolicitudInsumoDto otra = insumos.solicitar(est, "USCO-IN-010", new SolicitudInsumoRequest(new BigDecimal("100"), "ML", "Práctica de laboratorio", null));
        assertThatThrownBy(() -> insumos.rechazar(aux, otra.id(), "no")).hasMessageContaining("motivo");
        assertThat(insumos.rechazar(aux, otra.id(), "No hay práctica programada").estado()).isEqualTo("RECHAZADA");
        assertThat(insumos.mias(est)).extracting(SolicitudInsumoDto::estado).contains("APROBADA", "RECHAZADA");
    }

    @Test
    void cadaSedeAdministraSusLaboratorios() {
        Long coord = id("coordinador@usco.edu.co");
        assertThat(laboratorios.nombres(coord)).contains("Electrónica", "Petróleos", "Civil", "Agrícola");
        LaboratorioDto quimica = laboratorios.crear(coord, new LaboratorioRequest("Química", "Bloque C", "Química general"));
        assertThatThrownBy(() -> laboratorios.crear(coord, new LaboratorioRequest("química", null, null))).hasMessageContaining("Ya existe");
        inventario.crear(coord, new EquipoRequest("USCO-EQ-960", "Balanza analítica", null, null, null, "Pesaje", "Química",
                null, null, null, null, null, null, null));
        assertThatThrownBy(() -> laboratorios.activar(coord, quimica.id(), false)).hasMessageContaining("elementos activos");
        LaboratorioDto renombrado = laboratorios.actualizar(coord, quimica.id(), new LaboratorioRequest("Química Analítica", "Bloque C", null));
        assertThat(renombrado.equipos()).isEqualTo(1);
        assertThat(catalogo.listar(coord, "Química Analítica", null, null)).extracting(EquipoDto::placa).containsExactly("USCO-EQ-960");
    }

    @Test
    void losReportesTienenSeguimientoYLaPrioridadMediaVaAlResumenDiario() throws Exception {
        Long aux = id("auxiliar@usco.edu.co");
        Long coord = id("coordinador@usco.edu.co");
        NovedadDto n = novedades.reportar(aux, "USCO-EQ-104", new NovedadRequest("FALLA", "El canal 2 no muestra la señal", false));
        // Sin n8n: prioridad por reglas (FALLA = MEDIA) → no se envía al instante
        NovedadDto r = null;
        for (int i = 0; i < 50; i++) {
            r = novedades.todos(coord).stream().filter(x -> x.id().equals(n.id())).findFirst().orElseThrow();
            if (!"PENDIENTE".equals(r.notificacion())) break;
            Thread.sleep(100);
        }
        assertThat(r.notificacion()).isEqualTo("PENDIENTE_RESUMEN");
        assertThat(r.prioridad()).isEqualTo("MEDIA");

        String v1 = novedades.version(coord).version();
        NovedadDto enProceso = novedades.seguir(coord, n.id(), new SeguimientoRequest("EN_PROCESO", "Se pidió cotización del repuesto"));
        assertThat(enProceso.estado()).isEqualTo("EN_PROCESO");
        assertThat(novedades.version(coord).version()).isNotEqualTo(v1);
        novedades.seguir(aux, n.id(), new SeguimientoRequest("RESUELTO", "Se cambió la sonda"));
        List<SeguimientoDto> seg = novedades.seguimiento(coord, n.id());
        assertThat(seg).extracting(SeguimientoDto::estadoNuevo).containsExactly("EN_PROCESO", "RESUELTO");
        assertThatThrownBy(() -> novedades.seguir(coord, n.id(), new SeguimientoRequest("CERRADO", "x y z"))).hasMessageContaining("Estado no válido");
    }
}
