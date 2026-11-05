package co.edu.usco.labcontrol;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import co.edu.usco.labcontrol.domain.Sede;
import co.edu.usco.labcontrol.repo.SedeRepository;
import co.edu.usco.labcontrol.repo.UsuarioRepository;
import co.edu.usco.labcontrol.service.*;
import co.edu.usco.labcontrol.web.Dtos.*;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.TestPropertySource;

/** Sedes (aislamiento de datos por sede), presupuesto por sede y subida de fotografías. */
@SpringBootTest
@TestPropertySource(properties = "spring.datasource.url=jdbc:h2:mem:labcontrol-sedes;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1")
class SedesPresupuestoImagenesTest {
    @Autowired SedeService sedeSrv;
    @Autowired PresupuestoService presupuesto;
    @Autowired ImagenService imagenes;
    @Autowired CatalogoService catalogo;
    @Autowired InventarioService inventario;
    @Autowired UsuariosService usuariosSrv;
    @Autowired PrestamoService prestamos;
    @Autowired AuthService auth;
    @Autowired UsuarioRepository usuarios;
    @Autowired SedeRepository sedes;

    Long id(String correo) { return usuarios.findByCorreoIgnoreCase(correo).orElseThrow().getId(); }

    @Test
    void losDatosExistentesQuedanEnLaSedeNeivaYSePuedenCrearSedesNuevas() {
        assertThat(sedeSrv.publicas()).extracting(SedeDto::slug).contains("neiva");
        assertThat(auth.login(new LoginRequest("estudiante@usco.edu.co", "Est#USCO2026", false)).usuario().sede()).isEqualTo("neiva");
        assertThat(auth.login(new LoginRequest("coordinador@usco.edu.co", "Coord#USCO2026", false)).usuario().sede()).isNull();

        SedeAdminDto pitalito = sedeSrv.crear(new SedeRequest(null, "Pitalito", "Km 1 vía Fátima"));
        assertThat(pitalito.sede().slug()).isEqualTo("pitalito");
        assertThat(pitalito.sede().nombre()).isEqualTo("Sede Pitalito");
        assertThat(sedeSrv.crear(new SedeRequest("Sede La Plata", "La Plata", null)).sede().slug()).isEqualTo("la-plata");
        assertThatThrownBy(() -> sedeSrv.crear(new SedeRequest(null, "Pitalito", null))).hasMessageContaining("Ya existe");
        assertThatThrownBy(() -> sedeSrv.crear(new SedeRequest(null, "Login", null))).hasMessageContaining("no es válida");
    }

    @Test
    void cadaSedeVeSoloSusEquiposYSusUsuarios() {
        Sede garzon = sedes.findBySlug("garzon").orElseGet(() -> {
            sedeSrv.crear(new SedeRequest(null, "Garzón", "Km 1 vía Vereda El Macal"));
            return sedes.findBySlug("garzon").orElseThrow();
        });
        Long coord = id("coordinador@usco.edu.co");
        // Un auxiliar y un estudiante de Garzón (se crean con el contexto de sede de Garzón)
        var aux = usuarios.findByCorreoIgnoreCase("aux.garzon@usco.edu.co").orElseGet(() -> {
            usuariosSrv.crear(coord, new UsuarioRequest("Auxiliar Garzón", "aux.garzon@usco.edu.co", "AUXILIAR", "1099800001", null, null, "Garzon#2026"));
            var u = usuarios.findByCorreoIgnoreCase("aux.garzon@usco.edu.co").orElseThrow(); u.setSede(garzon); return usuarios.save(u);
        });
        var est = usuarios.findByCorreoIgnoreCase("est.garzon@usco.edu.co").orElseGet(() -> {
            usuariosSrv.crear(coord, new UsuarioRequest("Estudiante Garzón", "est.garzon@usco.edu.co", "ESTUDIANTE", "1099800002", null, null, "Garzon#2026"));
            var u = usuarios.findByCorreoIgnoreCase("est.garzon@usco.edu.co").orElseThrow(); u.setSede(garzon); return usuarios.save(u);
        });
        inventario.crear(aux.getId(), new EquipoRequest("USCO-EQ-900", "Multímetro Garzón", "Fluke", "117", null, "Multímetros", "Electrónica",
                null, null, null, null, "Bueno", null, "DISPONIBLE"));

        // Garzón ve solo su equipo; Neiva no lo ve
        assertThat(catalogo.listar(est.getId(), null, null, null)).extracting(EquipoDto::placa).containsExactly("USCO-EQ-900");
        assertThat(catalogo.listar(id("estudiante@usco.edu.co"), null, null, null)).extracting(EquipoDto::placa).doesNotContain("USCO-EQ-900");
        // Un auxiliar de Neiva no puede prestar un equipo de Garzón ni atender a un estudiante de Garzón
        Long auxNeiva = id("auxiliar@usco.edu.co");
        assertThatThrownBy(() -> prestamos.salida(auxNeiva, new SalidaRequest("USCO-EQ-900", "1075123456", 2))).hasMessage("El equipo no existe en el inventario activo");
        assertThatThrownBy(() -> prestamos.solicitudes(auxNeiva, "1099800002")).hasMessageContaining("No se encontró");
        // El auxiliar de Garzón sí
        assertThat(prestamos.salida(aux.getId(), new SalidaRequest("USCO-EQ-900", "1099800002", 2)).estado()).isEqualTo("ACTIVO");
        // Ingreso por la URL de otra sede: mensaje claro
        assertThatThrownBy(() -> auth.login(new LoginRequest("est.garzon@usco.edu.co", "Garzon#2026", false, "neiva"))).hasMessageContaining("Sede Garzón");
        assertThat(auth.login(new LoginRequest("est.garzon@usco.edu.co", "Garzon#2026", false, "garzon")).usuario().sede()).isEqualTo("garzon");
        assertThat(auth.login(new LoginRequest("coordinador@usco.edu.co", "Coord#USCO2026", false, "garzon")).token()).isNotBlank();
    }

    @Test
    void elPresupuestoSumaIngresosRestaGastosYNoPermiteSaldoNegativo() {
        Long coord = id("coordinador@usco.edu.co");   // sin petición HTTP trabaja sobre la sede principal (Neiva)
        long inicial = presupuesto.deSede(coord).saldo();
        presupuesto.registrar(coord, new MovimientoRequest("INGRESO", "Asignación presupuestal 2026-2", "Asignación", 10_000_000L, LocalDate.now(), null));
        MovimientoDto gasto = presupuesto.registrar(coord, new MovimientoRequest("GASTO", "Compra de puntas de osciloscopio", "Compra de equipos", 2_500_000L, null, "USCO-EQ-102"));
        assertThat(presupuesto.deSede(coord).saldo()).isEqualTo(inicial + 7_500_000L);
        assertThatThrownBy(() -> presupuesto.registrar(coord, new MovimientoRequest("GASTO", "Compra grande", "Compra de equipos", inicial + 999_999_999L, null, null))).hasMessageContaining("Saldo insuficiente");
        assertThatThrownBy(() -> presupuesto.registrar(coord, new MovimientoRequest("GASTO", "x", "Compra de equipos", 1000L, null, null))).hasMessageContaining("concepto");
        assertThatThrownBy(() -> presupuesto.registrar(coord, new MovimientoRequest("PRESTAMO", "Algo", "Otro", 1000L, null, null))).hasMessageContaining("INGRESO");
        assertThatThrownBy(() -> presupuesto.registrar(coord, new MovimientoRequest("GASTO", "Algo raro", "Viajes", 1000L, null, null))).hasMessageContaining("Categoría");
        assertThatThrownBy(() -> presupuesto.registrar(coord, new MovimientoRequest("INGRESO", "Futuro", "Asignación", 1000L, LocalDate.now().plusDays(3), null))).hasMessageContaining("futura");

        // Anular un gasto devuelve el dinero al saldo y deja el registro visible
        MovimientoDto anulado = presupuesto.anular(coord, gasto.id(), "Factura registrada dos veces");
        assertThat(anulado.anulado()).isTrue();
        PresupuestoDto p = presupuesto.deSede(coord);
        assertThat(p.saldo()).isEqualTo(inicial + 10_000_000L);
        assertThat(p.movimientos()).anySatisfy(m -> assertThat(m.anulacionMotivo()).contains("dos veces"));
        assertThatThrownBy(() -> presupuesto.anular(coord, gasto.id(), "Otra vez")).hasMessageContaining("ya está anulado");
        assertThat(presupuesto.resumen().sedes()).extracting(x -> x.sede().slug()).contains("neiva");
    }

    @Test
    void soloSeAceptanFotografiasRealesDeHasta5MB() {
        Long coord = id("coordinador@usco.edu.co");
        byte[] jpg = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 0x10, 'J', 'F', 'I', 'F'};
        ImagenSubidaDto ok = imagenes.subir(coord, new MockMultipartFile("archivo", "foto.jpg", "image/jpeg", jpg));
        assertThat(ok.url()).startsWith("/api/imagenes/").hasSize("/api/imagenes/".length() + 32);
        assertThat(ImagenService.esRutaPropia(ok.url())).isTrue();
        assertThat(imagenes.buscar(ok.id())).hasValueSatisfying(i -> assertThat(i.getTipoContenido()).isEqualTo("image/jpeg"));

        // Un archivo que no es imagen aunque diga .jpg
        assertThatThrownBy(() -> imagenes.subir(coord, new MockMultipartFile("archivo", "virus.jpg", "image/jpeg", "<script>".getBytes())))
                .hasMessageContaining("JPG, PNG o WEBP");
        assertThatThrownBy(() -> imagenes.subir(coord, new MockMultipartFile("archivo", "grande.jpg", "image/jpeg", new byte[6 * 1024 * 1024])))
                .hasMessageContaining("5 MB");
        assertThat(imagenes.buscar("../../etc/passwd")).isEmpty();

        // La ruta subida se acepta como fotografía de un equipo
        EquipoAdminDto e = inventario.crear(coord, new EquipoRequest("USCO-EQ-950", "Equipo con foto subida", null, null, null, "Otros", "Civil",
                null, null, List.of(), List.of(), "Bueno", ok.url(), "DISPONIBLE"));
        assertThat(e.equipo().fotoUrl()).isEqualTo(ok.url());
    }
}
