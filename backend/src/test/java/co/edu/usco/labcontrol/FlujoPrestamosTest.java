package co.edu.usco.labcontrol;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import co.edu.usco.labcontrol.domain.*;
import co.edu.usco.labcontrol.repo.*;
import co.edu.usco.labcontrol.service.*;
import co.edu.usco.labcontrol.web.ApiException;
import co.edu.usco.labcontrol.web.Dtos.*;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/** Reglas de negocio críticas del QA (PV-04, PV-05, PV-10, PF-08..PF-10, PS-05, PS-08) y RNF-02, contra la base de datos. */
@SpringBootTest
class FlujoPrestamosTest {
    @Autowired PrestamoService prestamos;
    @Autowired CatalogoService catalogo;
    @Autowired CoordinadorService coordinador;
    @Autowired InventarioService inventario;
    @Autowired UsuariosService usuariosSrv;
    @Autowired AuthService auth;
    @Autowired UsuarioRepository usuarios;
    @Autowired EquipoRepository equipos;

    Long id(String correo) { return usuarios.findByCorreoIgnoreCase(correo).orElseThrow().getId(); }

    @Test
    void loginIdentificaElRolDesdeLaBaseDeDatos() {
        assertThatThrownBy(() -> auth.login(new LoginRequest("", "x", false))).hasMessage("El correo institucional es obligatorio");
        assertThatThrownBy(() -> auth.login(new LoginRequest("usuario123", "x", false))).hasMessage("Formato de correo no válido (@usco.edu.co)");
        assertThat(auth.login(new LoginRequest("estudiante@usco.edu.co", "Est#USCO2026", false)).usuario().rol()).isEqualTo("ESTUDIANTE");
        assertThat(auth.login(new LoginRequest("auxiliar@usco.edu.co", "Aux#USCO2026", false)).usuario().grupo()).isEqualTo("AUXILIAR");
        assertThat(auth.login(new LoginRequest("coordinador@usco.edu.co", "Coord#USCO2026", false)).usuario().rol()).isEqualTo("COORDINADOR");
        assertThat(auth.login(new LoginRequest("docente@usco.edu.co", "Doc#USCO2026", false)).usuario().rol()).isEqualTo("DOCENTE");
        var cesar = auth.login(new LoginRequest("cesar.pimiento@usco.edu.co", "Cesar#USCO2026", true));
        assertThat(cesar.usuario().codigoEstudiantil()).isNotBlank();
        assertThat(cesar.expiraEnSegundos()).isGreaterThan(24 * 3600);   // «recordar sesión»
    }

    @Test
    void credencialesInvalidasDevuelvenMensajeGenerico() {
        String m1 = catchMsg(() -> auth.login(new LoginRequest("estudiante@usco.edu.co", "mala-clave1", false)));
        String m2 = catchMsg(() -> auth.login(new LoginRequest("nadie@usco.edu.co", "Est#USCO2026", false)));
        assertThat(m1).isEqualTo("Credenciales inválidas").isEqualTo(m2);
    }

    @Test
    void cambiarContrasenaExigeLaActualYUnaNuevaSegura() {
        Long camila = id("camila.rojas@usco.edu.co");
        assertThatThrownBy(() -> auth.cambiarClave(camila, new CambiarClaveRequest("incorrecta1", "Nueva#Clave2026"))).hasMessageContaining("actual");
        assertThatThrownBy(() -> auth.cambiarClave(camila, new CambiarClaveRequest("Camila#USCO2026", "corta"))).hasMessageContaining("8 y 72");
        auth.cambiarClave(camila, new CambiarClaveRequest("Camila#USCO2026", "Nueva#Clave2026"));
        assertThat(auth.login(new LoginRequest("camila.rojas@usco.edu.co", "Nueva#Clave2026", false)).token()).isNotBlank();
        auth.cambiarClave(camila, new CambiarClaveRequest("Nueva#Clave2026", "Camila#USCO2026"));
    }

    @Test
    void catalogoNoExponeQuienTieneElEquipoYMuestraLaFichaCompleta() {
        EquipoDetalleDto d = catalogo.detalle("USCO-EQ-103");
        assertThat(d.equipo().estado()).isEqualTo("DISPONIBLE");
        assertThat(d.equipo().fotoUrl()).isEqualTo("/images/equipos/usco-eq-103.jpg");
        assertThat(d.equipo().numeroSerie()).isNotBlank();
        assertThat(d.equipo().ubicacion()).contains("Electrónica");
        assertThat(d.equipo().accesorios()).isNotEmpty();
        assertThat(d.historialPublico()).allSatisfy(u -> assertThat(u.idAnonimo()).startsWith("PR-"));
        assertThat(catalogo.listar(null, null, "multimetro")).extracting(EquipoDto::placa).contains("USCO-EQ-103");
        assertThat(catalogo.listar(null, null, "Telescopio")).isEmpty();
        assertThat(catalogo.listar(null, null, null)).hasSize(18);
    }

    @Test
    void salidaValidaPlacaYLimiteYEstado() {
        Long aux = id("auxiliar@usco.edu.co");
        assertThatThrownBy(() -> prestamos.salida(aux, new SalidaRequest("EQ-99999", "1075123456", 2))).hasMessageContaining("USCO-EQ-000");
        assertThatThrownBy(() -> prestamos.salida(aux, new SalidaRequest("USCO-EQ-999", "1075123456", 2))).hasMessage("El equipo no existe en el inventario activo");
        assertThatThrownBy(() -> prestamos.salida(aux, new SalidaRequest("USCO-EQ-102", "1075123456", 2))).hasMessage("El equipo ya se encuentra prestado");
        assertThatThrownBy(() -> prestamos.salida(aux, new SalidaRequest("USCO-EQ-105", "1075123456", 2))).hasMessageContaining("mantenimiento");
        // Andrés ya tiene 3 equipos activos: el 4.º se bloquea (PV-10). Se busca por documento o por código estudiantil.
        assertThatThrownBy(() -> prestamos.salida(aux, new SalidaRequest("USCO-EQ-104", "1075200011", 2))).hasMessage("Límite máximo de equipos alcanzado");
        assertThatThrownBy(() -> prestamos.salida(aux, new SalidaRequest("USCO-EQ-104", "20201100011", 2))).hasMessage("Límite máximo de equipos alcanzado");
    }

    @Test
    void devolucionConDanosEnviaAMantenimientoYAbreIncidencia() {
        Long aux = id("auxiliar@usco.edu.co");
        PrestamoDto p = prestamos.salida(aux, new SalidaRequest("USCO-EQ-101", "1075200014", 2));
        assertThat(p.estado()).isEqualTo("ACTIVO");
        assertThat(equipos.findByPlaca("USCO-EQ-101").orElseThrow().getEstado()).isEqualTo(EstadoEquipo.PRESTADO);
        assertThatThrownBy(() -> prestamos.entrada(aux, new EntradaRequest(p.id(), false, true, false, false, " "))).hasMessage("Describa la novedad en las observaciones");
        EntradaResponse r = prestamos.entrada(aux, new EntradaRequest(p.id(), false, true, false, false, "Pantalla rota"));
        assertThat(r.incidencia()).startsWith("INC-");
        assertThat(r.equipo().estado()).isEqualTo("MANTENIMIENTO");
        assertThat(r.prestamo().estado()).isEqualTo("DEVUELTO");
    }

    @Test
    void reservaYSalidaConvierteLaReservaSinDuplicar() {
        Long aux = id("auxiliar@usco.edu.co");
        SolicitudesUsuarioDto s = prestamos.solicitudes("1075123456");
        assertThat(s.reservas()).extracting(PrestamoDto::placa).contains("USCO-EQ-100");
        assertThat(s.codigoEstudiantil()).isEqualTo("20211100456");
        PrestamoDto salida = prestamos.salida(aux, new SalidaRequest("USCO-EQ-100", "1075123456", 2));
        assertThat(salida.codigo()).startsWith("RES-");
        assertThat(salida.estado()).isEqualTo("ACTIVO");
        assertThat(prestamos.solicitudes("1075123456").reservas()).isEmpty();
    }

    @Test
    void reservaGuardaDocenteYNoPermiteTraslapes() {
        Long camila = id("camila.rojas@usco.edu.co");
        Long sebastian = id("sebastian.medina@usco.edu.co");
        LocalDateTime ini = LocalDateTime.now().plusDays(2).withHour(10).withMinute(0).withSecond(0).withNano(0);
        assertThatThrownBy(() -> prestamos.reservar(camila, new ReservaRequest("USCO-EQ-400", "Suelos", null, null, null))).hasMessage("Seleccione fecha y franja horaria para continuar");
        PrestamoDto r = prestamos.reservar(camila, new ReservaRequest("USCO-EQ-400", "Suelos", "Ing. Perdomo", ini, ini.plusHours(2)));
        assertThat(r.docente()).isEqualTo("Ing. Perdomo");
        assertThatThrownBy(() -> prestamos.reservar(sebastian, new ReservaRequest("USCO-EQ-400", "Suelos", null, ini.plusHours(1), ini.plusHours(3)))).hasMessageContaining("reserva en esa franja");
        assertThatThrownBy(() -> prestamos.reservar(sebastian, new ReservaRequest("USCO-EQ-102", "Circuitos", null, ini, ini.plusHours(2)))).hasMessage("No disponible");
    }

    @Test
    void elAuxiliarPuedeRechazarUnaSolicitudPendienteConMotivo() {
        Long aux = id("auxiliar@usco.edu.co");
        Long sebastian = id("sebastian.medina@usco.edu.co");
        LocalDateTime ini = LocalDateTime.now().plusDays(3).withHour(14).withMinute(0).withSecond(0).withNano(0);
        PrestamoDto r = prestamos.reservar(sebastian, new ReservaRequest("USCO-EQ-104", "Electrónica de Potencia", null, ini, ini.plusHours(2)));
        assertThatThrownBy(() -> prestamos.rechazarReserva(aux, r.id(), "no")).hasMessageContaining("motivo");
        PrestamoDto rech = prestamos.rechazarReserva(aux, r.id(), "El equipo estará en campo esa fecha");
        assertThat(rech.estado()).isEqualTo("RECHAZADO");
        assertThat(rech.observaciones()).contains("estará en campo");
        assertThat(prestamos.misPrestamos(sebastian)).extracting(PrestamoDto::estado).contains("RECHAZADO");
    }

    @Test
    void concurrenciaSoloUnAuxiliarLograElPrestamo() throws Exception {
        Long aux1 = id("auxiliar@usco.edu.co");
        Long aux2 = id("auxiliar2@usco.edu.co");
        int hilos = 6;
        ExecutorService pool = Executors.newFixedThreadPool(hilos);
        CountDownLatch salida = new CountDownLatch(1);
        AtomicInteger ok = new AtomicInteger();
        AtomicInteger rechazados = new AtomicInteger();
        String[] docs = {"1075123456", "1075200012", "1075200013", "1075200014", "36600200", "1075100003"};
        List<Future<?>> fs = new java.util.ArrayList<>();
        for (int i = 0; i < hilos; i++) {
            final int k = i;
            fs.add(pool.submit(() -> {
                try {
                    salida.await();
                    prestamos.salida(k % 2 == 0 ? aux1 : aux2, new SalidaRequest("USCO-EQ-200", docs[k], 2));
                    ok.incrementAndGet();
                } catch (Exception e) {
                    rechazados.incrementAndGet();
                }
                return null;
            }));
        }
        salida.countDown();
        for (Future<?> f : fs) f.get(30, TimeUnit.SECONDS);
        pool.shutdown();
        assertThat(ok.get()).as("exactamente un préstamo exitoso (0 duplicados)").isEqualTo(1);
        assertThat(rechazados.get()).isEqualTo(hilos - 1);
    }

    @Test
    void crudDeEquiposAltaEdicionMantenimientoYBaja() {
        Long coord = id("coordinador@usco.edu.co");
        Long aux = id("auxiliar@usco.edu.co");
        EquipoRequest nuevo = new EquipoRequest("USCO-EQ-410", "Medidor de Clorofila", "Konica Minolta", "SPAD-502", "SPAD-12345", "Análisis de cultivos", "Agrícola",
                "Sede Neiva · Bodega", "Medición no destructiva de clorofila foliar.", List.of("Rango 0-99 SPAD"), List.of("Estuche", "Baterías"), "Excelente", "/images/equipos/usco-eq-410.jpg", "DISPONIBLE");
        assertThatThrownBy(() -> inventario.crear(coord, new EquipoRequest("EQ-1", "x", null, null, null, "c", "Civil", null, null, null, null, null, null, null))).hasMessageContaining("USCO-EQ-000");
        assertThatThrownBy(() -> inventario.crear(coord, new EquipoRequest("USCO-EQ-411", "x", null, null, null, "c", "Marte", null, null, null, null, null, null, null))).hasMessageContaining("laboratorio");
        assertThatThrownBy(() -> inventario.crear(coord, new EquipoRequest("USCO-EQ-411", "x", null, null, null, "c", "Civil", null, null, null, null, null, "javascript:alert(1)", null))).hasMessageContaining("fotografía");
        EquipoAdminDto creado = inventario.crear(coord, nuevo);
        assertThat(creado.equipo().accesorios()).containsExactly("Estuche", "Baterías");
        assertThatThrownBy(() -> inventario.crear(coord, nuevo)).hasMessageContaining("Ya existe");
        assertThat(catalogo.listar(null, null, "clorofila")).hasSize(1);

        assertThat(inventario.actualizar(coord, "USCO-EQ-410", new EquipoRequest(null, "Medidor de Clorofila SPAD", "Konica Minolta", "SPAD-502", "SPAD-12345",
                "Análisis de cultivos", "Agrícola", "Sede Neiva · Bodega 2", null, null, List.of("Estuche"), "Bueno", null, null)).equipo().nombre()).isEqualTo("Medidor de Clorofila SPAD");

        assertThatThrownBy(() -> inventario.cambiarEstado(aux, "USCO-EQ-410", new EstadoEquipoRequest("MANTENIMIENTO", ""))).hasMessageContaining("motivo");
        assertThat(inventario.cambiarEstado(aux, "USCO-EQ-410", new EstadoEquipoRequest("MANTENIMIENTO", "Calibración anual")).estado()).isEqualTo("MANTENIMIENTO");
        assertThat(inventario.cambiarEstado(aux, "USCO-EQ-410", new EstadoEquipoRequest("DISPONIBLE", null)).estado()).isEqualTo("DISPONIBLE");
        assertThatThrownBy(() -> inventario.cambiarEstado(aux, "USCO-EQ-300", new EstadoEquipoRequest("MANTENIMIENTO", "Revisión"))).hasMessageContaining("prestado");

        assertThatThrownBy(() -> coordinador.baja(coord, "USCO-EQ-300", "Daño total")).hasMessageContaining("prestado");
        coordinador.baja(coord, "USCO-EQ-403", "Pantalla irreparable y batería inservible");
        assertThat(catalogo.listar(null, null, null)).extracting(EquipoDto::placa).doesNotContain("USCO-EQ-403");
        assertThat(equipos.findByPlaca("USCO-EQ-403")).isPresent();
        assertThat(coordinador.equiposAdmin()).anySatisfy(e -> {
            assertThat(e.equipo().placa()).isEqualTo("USCO-EQ-403");
            assertThat(e.activo()).isFalse();
        });
    }

    @Test
    void gestionDeUsuariosCreaDesactivaYRestableceClave() {
        Long coord = id("coordinador@usco.edu.co");
        UsuarioRequest req = new UsuarioRequest("Pedro Pérez", "pedro.perez@usco.edu.co", "ESTUDIANTE", "1099000111", "20231100111", "Ing. Civil", "Pedro#2026ok");
        assertThatThrownBy(() -> usuariosSrv.crear(new UsuarioRequest("Pedro", "pedro@gmail.com", "ESTUDIANTE", "1099000112", null, null, "Pedro#2026ok"))).hasMessageContaining("@usco.edu.co");
        assertThatThrownBy(() -> usuariosSrv.crear(new UsuarioRequest("Pedro Pérez", "pedro2@usco.edu.co", "ADMIN", "1099000113", null, null, "Pedro#2026ok"))).hasMessageContaining("Rol");
        assertThatThrownBy(() -> usuariosSrv.crear(new UsuarioRequest("Pedro Pérez", "pedro3@usco.edu.co", "ESTUDIANTE", "1099000114", null, null, "corta"))).hasMessageContaining("contraseña");
        UsuarioAdminDto u = usuariosSrv.crear(req);
        assertThat(auth.login(new LoginRequest("pedro.perez@usco.edu.co", "Pedro#2026ok", false)).usuario().rol()).isEqualTo("ESTUDIANTE");
        assertThatThrownBy(() -> usuariosSrv.crear(req)).hasMessageContaining("Ya existe");

        usuariosSrv.restablecerClave(u.id(), "Otra#Clave2026");
        assertThat(auth.login(new LoginRequest("pedro.perez@usco.edu.co", "Otra#Clave2026", false)).token()).isNotBlank();
        usuariosSrv.cambiarActivo(coord, u.id(), false);
        assertThatThrownBy(() -> auth.login(new LoginRequest("pedro.perez@usco.edu.co", "Otra#Clave2026", false))).hasMessage("Credenciales inválidas");
        assertThatThrownBy(() -> usuariosSrv.cambiarActivo(coord, coord, false)).hasMessageContaining("propia");
    }

    @Test
    void reporteExportadoEnmascaraDatosPersonalesNoPertinentes() {
        String csv = coordinador.reporteCsv(null, null, null);
        assertThat(csv).contains("Documento;Teléfono;Dirección").contains("••••••;••••••;••••••");
        assertThat(csv).doesNotContain("1075123456");
    }

    private static String catchMsg(Runnable r) {
        try { r.run(); return ""; } catch (ApiException e) { return e.getMessage(); }
    }
}
