package co.edu.usco.labcontrol;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import co.edu.usco.labcontrol.repo.LaboratorioRepository;
import co.edu.usco.labcontrol.repo.UsuarioRepository;
import co.edu.usco.labcontrol.service.*;
import co.edu.usco.labcontrol.web.Dtos.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.TestPropertySource;

/** Solicitudes (monitor → coordinador → Administrador), presupuesto y contratación, compromisos, avances y reportes. */
@SpringBootTest
@TestPropertySource(properties = "spring.datasource.url=jdbc:h2:mem:labcontrol-solicitudes;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1")
class SolicitudesSeguimientoTest {
    @MockBean CorreoService correo;
    @Autowired SolicitudService solicitudes;
    @Autowired PresupuestoService presupuesto;
    @Autowired SeguimientoService seguimiento;
    @Autowired ReporteService reportes;
    @Autowired AlertaService alertas;
    @Autowired UsuarioRepository usuarios;
    @Autowired LaboratorioRepository laboratorios;

    Long id(String correo) { return usuarios.findByCorreoIgnoreCase(correo).orElseThrow().getId(); }
    Long lab(String nombre) { return laboratorios.findAll().stream().filter(l -> l.getNombre().equals(nombre)).findFirst().orElseThrow().getId(); }

    static final byte[] PDF = "%PDF-1.4\n%prueba\n".getBytes();

    SolicitudRequest compra(String titulo) {
        return new SolicitudRequest(lab("Electrónica"), "Compra de equipos", titulo,
                "Los osciloscopios actuales no alcanzan para los grupos de Circuitos II; se necesitan dos equipos adicionales.",
                "Osciloscopio digital 100 MHz, 2 canales, 1 GS/s, pantalla de 7 pulgadas", new BigDecimal("2"), "unidades", "ALTA", 6_400_000L);
    }

    @Test
    void flujoCompletoDeUnaSolicitudPreparadaPorElMonitor() {
        Long monitor = id("monitor@usco.edu.co");
        Long coord = id("coordinador@usco.edu.co");
        Long admin = id("admin@usco.edu.co");

        // 1. El monitor prepara el borrador con una cotización
        SolicitudDetalleDto s = solicitudes.crear(monitor, compra("Dos osciloscopios para Circuitos II"));
        assertThat(s.resumen().codigo()).matches("SR-\\d{4}-\\d{4}");
        assertThat(s.resumen().estado()).isEqualTo("BORRADOR");
        assertThat(s.acciones().enviarTexto()).contains("coordinador");
        assertThat(s.presupuesto()).isNull();
        s = solicitudes.adjuntar(monitor, s.resumen().id(), "COTIZACION", new MockMultipartFile("archivo", "cotizacion.pdf", "application/pdf", PDF));
        assertThat(s.adjuntos()).singleElement().satisfies(a -> assertThat(a.tipo()).isEqualTo("COTIZACION"));
        Long sid = s.resumen().id();
        // El Administrador aún no la ve
        assertThatThrownBy(() -> solicitudes.detalle(admin, sid)).hasMessageContaining("no existe");

        // 2. «Enviar» del monitor: queda en revisión del coordinador
        s = solicitudes.enviar(monitor, sid, null);
        assertThat(s.resumen().estado()).isEqualTo("EN_REVISION");
        assertThat(s.acciones().editar()).isFalse();
        assertThatThrownBy(() -> solicitudes.actualizar(monitor, sid, compra("Cambio"))).hasMessageContaining("No puedes editar");
        assertThat(alertas.listar(coord)).anySatisfy(a -> assertThat(a.tipo()).isEqualTo("SOLICITUD_POR_VALIDAR"));

        // 3. El coordinador ajusta y la envía al Administrador
        SolicitudDetalleDto vistaCoord = solicitudes.detalle(coord, sid);
        assertThat(vistaCoord.acciones().editar()).isTrue();
        assertThat(vistaCoord.acciones().enviarTexto()).isEqualTo("Enviar al Administrador");
        solicitudes.actualizar(coord, sid, compra("Dos osciloscopios de 100 MHz para Circuitos II"));
        s = solicitudes.enviar(coord, sid, new TextoRequest("Validado por coordinación"));
        assertThat(s.resumen().estado()).isEqualTo("ENVIADA");

        // 4. El Administrador la devuelve para ajuste
        assertThatThrownBy(() -> solicitudes.devolver(coord, sid, new TextoRequest("No"))).hasMessageContaining("No puedes devolver");
        s = solicitudes.devolver(admin, sid, new TextoRequest("Adjunte una segunda cotización para comparar precios"));
        assertThat(s.resumen().estado()).isEqualTo("DEVUELTA");
        assertThat(s.observacionDevolucion()).contains("segunda cotización");
        assertThat(alertas.listar(monitor)).anySatisfy(a -> assertThat(a.tipo()).isEqualTo("SOLICITUD_DEVUELTA"));
        solicitudes.adjuntar(coord, sid, "COTIZACION", new MockMultipartFile("archivo", "cotizacion2.pdf", "application/pdf", PDF));
        solicitudes.enviar(coord, sid, null);

        // 5. Priorización, decisión, presupuesto y contratación
        solicitudes.priorizar(admin, sid, new PriorizarRequest("ALTA", 1, "Primera de la vigencia"));
        assertThat(solicitudes.listar(admin, "ENVIADA", null, null)).first().satisfies(x -> assertThat(x.id()).isEqualTo(sid));
        s = solicitudes.decidir(admin, sid, new DecisionRequest("APROBADA", null));
        assertThat(s.resumen().estado()).isEqualTo("APROBADA");
        s = solicitudes.presupuesto(admin, sid, new PresupuestoRequest("2.1.2.02 Equipos de laboratorio", "Estampilla Pro-Universidad", "CDP-0451",
                LocalDate.now(), 6_200_000L));
        assertThat(s.presupuesto().valorAprobado()).isEqualTo(6_200_000L);
        presupuesto.registrar(admin, new MovimientoRequest("INGRESO", "Asignación presupuestal 2026", "Asignación", 50_000_000L, LocalDate.now(), null));
        s = solicitudes.contratacion(admin, sid, new ContratacionRequest("Mínima cuantía", "CT-2026-031", "Electro Huila S.A.S.", 6_150_000L,
                LocalDate.now(), "CONTRATADO", null, true, "Compra de equipos"));
        assertThat(s.presupuesto().movimientos()).singleElement().satisfies(m -> {
            assertThat(m.tipo()).isEqualTo("GASTO");
            assertThat(m.monto()).isEqualTo(6_150_000L);
            assertThat(m.solicitudCodigo()).isEqualTo(sid == null ? null : solicitudes.detalle(admin, sid).resumen().codigo());
        });
        Long sidFinal = sid;
        assertThatThrownBy(() -> solicitudes.contratacion(admin, sidFinal, new ContratacionRequest("Mínima cuantía", "CT-2026-031", "Electro Huila S.A.S.",
                6_150_000L, LocalDate.now(), "CONTRATADO", null, true, "Compra de equipos"))).hasMessageContaining("ya tiene un gasto");

        // 6. El coordinador consulta el estado presupuestal y contractual; el monitor no
        assertThat(solicitudes.detalle(coord, sid).presupuesto().estadoContractual()).isEqualTo("CONTRATADO");
        assertThat(solicitudes.detalle(monitor, sid).presupuesto()).isNull();
        assertThat(solicitudes.detalle(monitor, sid).resumen().valorAprobado()).isNull();
        assertThatThrownBy(() -> solicitudes.presupuesto(coord, sid, new PresupuestoRequest(null, null, null, null, 1L))).hasMessageContaining("Administrador");

        // 7. Avance con evidencia del monitor y verificación del Administrador
        AvanceDto av = seguimiento.registrarAvance(monitor, new AvanceRequest(null, null, sid, "Llegaron los equipos y se registraron en inventario", 100),
                List.of(new MockMultipartFile("evidencias", "acta.pdf", "application/pdf", PDF)));
        assertThat(av.laboratorio()).isEqualTo("Electrónica");
        assertThat(av.evidencias()).hasSize(1);
        assertThatThrownBy(() -> seguimiento.verificar(coord, av.id(), new VerificarRequest("VERIFICADO", null))).hasMessageContaining("permiso");
        assertThat(seguimiento.verificar(admin, av.id(), new VerificarRequest("VERIFICADO", "Acta revisada")).verificacion()).isEqualTo("VERIFICADO");

        // 8. Cierre y trazabilidad
        s = solicitudes.cerrar(admin, sid, new TextoRequest("Equipos entregados y en uso"));
        assertThat(s.resumen().estado()).isEqualTo("CERRADA");
        assertThat(s.eventos()).extracting(EventoSolicitudDto::accion)
                .containsSubsequence("CREADA", "EN_REVISION", "ENVIADA", "DEVUELTA", "ENVIADA", "PRIORIZADA", "APROBADA", "CERRADA");
        IndicadoresDto ind = reportes.indicadores(admin);
        assertThat(ind.total().solicitudesAprobadas()).isGreaterThanOrEqualTo(1);
    }

    @Test
    void elMonitorNoVeBorradoresAjenosYLaSolicitudDebeEstarCompletaParaEnviarse() {
        Long coord = id("coordinador@usco.edu.co");
        SolicitudDetalleDto borrador = solicitudes.crear(coord, new SolicitudRequest(lab("Petróleos"), "Insumos y reactivos", "Reactivos para lodos",
                null, null, null, null, null, null));
        assertThatThrownBy(() -> solicitudes.detalle(id("monitor@usco.edu.co"), borrador.resumen().id())).hasMessageContaining("no existe");
        assertThatThrownBy(() -> solicitudes.enviar(coord, borrador.resumen().id(), null)).hasMessageContaining("justificación");
        assertThatThrownBy(() -> solicitudes.crear(coord, new SolicitudRequest(lab("Civil"), "Otro", "Algo para Civil", null, null, null, null, null, null)))
                .hasMessageContaining("no está asignado");
        assertThatThrownBy(() -> solicitudes.crear(coord, new SolicitudRequest(lab("Petróleos"), "Tipo inventado", "Algo raro", null, null, null, null, null, null)))
                .hasMessageContaining("catálogo");
        SolicitudDetalleDto c = solicitudes.cancelar(coord, borrador.resumen().id(), new TextoRequest("Se compró con caja menor"));
        assertThat(c.resumen().estado()).isEqualTo("CANCELADA");
    }

    @Test
    void compromisosConResponsableYReportesExportables() {
        Long coord = id("coordinador@usco.edu.co");
        Long monitor = id("monitor@usco.edu.co");
        Long admin = id("admin@usco.edu.co");
        CompromisoDto c = seguimiento.crearCompromiso(coord, new CompromisoRequest(lab("Electrónica"), "Actualizar guías de práctica",
                "Revisar y publicar las guías de Circuitos II", monitor, LocalDate.now().plusDays(20)));
        assertThat(c.responsable()).isEqualTo("Juan Pablo Trujillo");
        assertThatThrownBy(() -> seguimiento.crearCompromiso(coord, new CompromisoRequest(null, "Compromiso de sede", null, null,
                LocalDate.now().plusDays(5)))).hasMessageContaining("laboratorio");
        assertThatThrownBy(() -> seguimiento.crearCompromiso(coord, new CompromisoRequest(lab("Electrónica"), "Responsable de otro lab", null,
                id("monitor.civil@usco.edu.co"), LocalDate.now().plusDays(5)))).hasMessageContaining("asignado");
        assertThat(seguimiento.crearCompromiso(admin, new CompromisoRequest(null, "Informe institucional semestral", null, null,
                LocalDate.now().plusDays(30))).laboratorio()).isEqualTo("Toda la sede");

        AvanceDto av = seguimiento.registrarAvance(monitor, new AvanceRequest(null, c.id(), null, "Guías 1 a 4 revisadas y enviadas al docente", 40), List.of());
        assertThat(av.compromiso()).isEqualTo("Actualizar guías de práctica");
        assertThat(seguimiento.compromisos(coord, "ABIERTOS", null)).anySatisfy(x -> {
            assertThat(x.id()).isEqualTo(c.id());
            assertThat(x.estado()).isEqualTo("EN_CURSO");
            assertThat(x.ultimoPorcentaje()).isEqualTo(40);
        });
        // El monitor de Civil no ve los compromisos de Electrónica ni el institucional
        assertThat(seguimiento.compromisos(id("monitor.civil@usco.edu.co"), null, null)).isEmpty();

        String inv = reportes.csv(coord, "inventario", null, null, null);
        assertThat(inv).startsWith("﻿Placa;Tipo;Nombre").contains("USCO-EQ-102").doesNotContain("USCO-EQ-300");
        assertThat(reportes.csv(coord, "compromisos", null, null, null)).contains("Actualizar guías de práctica");
        assertThat(reportes.csv(admin, "indicadores", null, null, null)).contains("Total");
        assertThatThrownBy(() -> reportes.csv(monitor, "historial", null, null, null)).hasMessageContaining("permiso");
        assertThatThrownBy(() -> reportes.csv(coord, "usuarios", null, null, null)).hasMessageContaining("no válido");
        TableroDto t = reportes.tablero(coord);
        assertThat(t.indicadores().laboratorios()).extracting(IndicadorLabDto::laboratorio).containsExactly("Electrónica", "Petróleos");
    }
}
