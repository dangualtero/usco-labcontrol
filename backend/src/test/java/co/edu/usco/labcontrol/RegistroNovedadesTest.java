package co.edu.usco.labcontrol;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import co.edu.usco.labcontrol.domain.EstadoEquipo;
import co.edu.usco.labcontrol.domain.Equipo;
import co.edu.usco.labcontrol.domain.ReporteNovedad;
import co.edu.usco.labcontrol.repo.EquipoRepository;
import co.edu.usco.labcontrol.repo.ReporteNovedadRepository;
import co.edu.usco.labcontrol.repo.UsuarioRepository;
import co.edu.usco.labcontrol.service.*;
import co.edu.usco.labcontrol.web.Dtos.*;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Registro de estudiantes con código al correo y novedades de equipos con aviso al correo institucional.
 * El agente de IA de n8n se simula con un servidor HTTP local que responde como lo haría el flujo real.
 */
@SpringBootTest
class RegistroNovedadesTest {
    static final AtomicReference<String> recibidoPorN8n = new AtomicReference<>();
    static final HttpServer n8n;
    static {
        try {
            n8n = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            n8n.createContext("/webhook/labcontrol-novedades", ex -> {
                String token = ex.getRequestHeaders().getFirst("X-LabControl-Token");
                recibidoPorN8n.set(new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
                byte[] r = ("secreto-prueba".equals(token)
                        ? "{\"prioridad\":\"ALTA\",\"asunto\":\"[ALTA] Osciloscopio dañado\",\"resumen\":\"Pantalla rota\",\"accionSugerida\":\"Retirar y cotizar\",\"correoEnviado\":true}"
                        : "{\"error\":\"token\"}").getBytes(StandardCharsets.UTF_8);
                ex.sendResponseHeaders("secreto-prueba".equals(token) ? 200 : 401, r.length);
                ex.getResponseBody().write(r);
                ex.close();
            });
            n8n.start();
        } catch (Exception e) { throw new IllegalStateException(e); }
    }

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", () -> "jdbc:h2:mem:labcontrol-registro;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
        r.add("labcontrol.novedades.n8n-webhook-url", () -> "http://127.0.0.1:" + n8n.getAddress().getPort() + "/webhook/labcontrol-novedades");
        r.add("labcontrol.novedades.n8n-token", () -> "secreto-prueba");
        r.add("labcontrol.novedades.destino", () -> "u20232215593@usco.edu.co");
    }

    @AfterAll static void apagar() { n8n.stop(0); }

    @MockBean CorreoService correo;
    @Autowired RegistroService registro;
    @Autowired AuthService auth;
    @Autowired NovedadService novedades;
    @Autowired UsuarioRepository usuarios;
    @Autowired EquipoRepository equipos;
    @Autowired ReporteNovedadRepository reportes;
    @Autowired JdbcTemplate jdbc;
    @Autowired CifradoContrasenas cifrado;

    String ultimoCodigo() {
        ArgumentCaptor<String> asunto = ArgumentCaptor.forClass(String.class);
        verify(correo, atLeastOnce()).enviar(anyString(), asunto.capture(), anyString(), any());
        Matcher m = Pattern.compile("(\\d{6})").matcher(asunto.getValue());
        assertThat(m.find()).isTrue();
        return m.group(1);
    }

    @Test
    void elEstudianteSoloQuedaRegistradoAlIngresarElCodigoDeSuCorreo() {
        when(correo.enviar(anyString(), anyString(), anyString(), any())).thenReturn(true);
        String mail = "u20239990001@usco.edu.co";
        RegistroRequest datos = new RegistroRequest("María Fernanda Rojas", mail, "1075999001", "20239990001", "Ingeniería de Software", "Maria#2026", "neiva");

        RegistroIniciadoDto r = registro.iniciar(datos);
        assertThat(r.correoEnviado()).isTrue();
        assertThat(usuarios.existsByCorreoIgnoreCase(mail)).isFalse();          // aún NO existe
        assertThatThrownBy(() -> auth.login(new LoginRequest(mail, "Maria#2026", false))).hasMessageContaining("Credenciales");

        String codigo = ultimoCodigo();
        String malo = codigo.equals("000000") ? "111111" : "000000";
        assertThatThrownBy(() -> registro.verificar(new VerificarRegistroRequest(mail, malo))).hasMessageContaining("Te quedan 4");
        assertThatThrownBy(() -> registro.reenviar(new ReenviarCodigoRequest(mail))).hasMessageContaining("Espera");

        LoginResponse sesion = registro.verificar(new VerificarRegistroRequest(mail, codigo));
        assertThat(sesion.token()).isNotBlank();
        assertThat(sesion.usuario().rol()).isEqualTo("ESTUDIANTE");
        assertThat(sesion.usuario().sede()).isEqualTo("neiva");
        assertThat(auth.login(new LoginRequest(mail, "Maria#2026", false)).usuario().correo()).isEqualTo(mail);

        // Datos repetidos y correos no institucionales
        assertThatThrownBy(() -> registro.iniciar(datos)).hasMessageContaining("Ya existe una cuenta");
        assertThatThrownBy(() -> registro.iniciar(new RegistroRequest("Pedro Pérez", "pedro@gmail.com", "1075999002", "20239990002", "Civil", "Pedro#2026", "neiva")))
                .hasMessageContaining("@usco.edu.co");
    }

    @Test
    void laNovedadDelAuxiliarLaPriorizaElAgenteDeN8nYQuedaRegistrada() throws Exception {
        Long aux = usuarios.findByCorreoIgnoreCase("auxiliar@usco.edu.co").orElseThrow().getId();
        Equipo e = equipos.findAll().stream().filter(x -> x.isActivo() && x.getEstado() == EstadoEquipo.DISPONIBLE).findFirst().orElseThrow();

        NovedadDto n = novedades.reportar(aux, e.getPlaca(), new NovedadRequest("DANADO", "Se cayó de la mesa y la pantalla quedó rota", true));
        assertThat(n.tipoTexto()).isEqualTo("Dañado");
        assertThat(n.enMantenimiento()).isTrue();
        assertThat(equipos.findByPlaca(e.getPlaca()).orElseThrow().getEstado()).isEqualTo(EstadoEquipo.MANTENIMIENTO);

        ReporteNovedad r = esperarAviso(n.id());
        assertThat(r.getNotificacion()).isEqualTo(ReporteNovedad.Notificacion.ENVIADA_IA);
        assertThat(r.getPrioridad()).isEqualTo("ALTA");
        assertThat(r.getAccionSugerida()).isEqualTo("Retirar y cotizar");
        assertThat(r.getDestinatario()).isEqualTo("u20232215593@usco.edu.co");
        assertThat(recibidoPorN8n.get()).contains(e.getPlaca()).contains("\"tipo\":\"DANADO\"").contains("u20232215593@usco.edu.co");

        assertThatThrownBy(() -> novedades.reportar(aux, e.getPlaca(), new NovedadRequest("DANADO", "Otra vez la pantalla rota", false)))
                .hasMessageContaining("hace menos de 10 minutos");
        assertThatThrownBy(() -> novedades.reportar(aux, e.getPlaca(), new NovedadRequest("ROTO", "Descripción suficiente", false)))
                .hasMessageContaining("Tipo de novedad no válido");
        assertThat(novedades.listar(aux)).extracting(NovedadDto::id).contains(n.id());
    }

    ReporteNovedad esperarAviso(Long id) throws InterruptedException {
        for (int i = 0; i < 100; i++) {
            ReporteNovedad r = reportes.findById(id).orElseThrow();
            if (r.getNotificacion() != ReporteNovedad.Notificacion.PENDIENTE) return r;
            Thread.sleep(100);
        }
        throw new AssertionError("El aviso de la novedad no se procesó");
    }

    @Test
    void unaContrasenaEscritaNormalEnMysqlSeCifraYPermiteIngresar() {
        String sql = "INSERT INTO usuarios (nombre, correo, password_hash, rol, documento, codigo_estudiantil, programa, activo, sede_id) "
                + "VALUES (?, ?, 'Usco#2026', ?, ?, ?, 'Ingeniería de Software', TRUE, (SELECT id FROM sedes WHERE slug = 'neiva'))";
        jdbc.update(sql, "Pedro Gómez", "pedro.gomez@usco.edu.co", "AUXILIAR", "1075400001", null);
        jdbc.update(sql, "Ana Torres", "u20231100555@usco.edu.co", "ESTUDIANTE", "1075400002", "20231100555");

        // 1) La tarea periódica la cifra
        cifrado.cifrar();
        String guardada = jdbc.queryForObject("SELECT password_hash FROM usuarios WHERE correo = 'pedro.gomez@usco.edu.co'", String.class);
        assertThat(CifradoContrasenas.esHash(guardada)).isTrue();
        assertThat(auth.login(new LoginRequest("pedro.gomez@usco.edu.co", "Usco#2026", false)).usuario().rol()).isEqualTo("AUXILIAR");
        assertThatThrownBy(() -> auth.login(new LoginRequest("pedro.gomez@usco.edu.co", "otraClave1", false))).hasMessageContaining("Credenciales");

        // 2) Si alguien ingresa antes de la tarea, el login también la cifra
        jdbc.update("UPDATE usuarios SET password_hash = 'Usco#2026' WHERE correo = 'u20231100555@usco.edu.co'");
        assertThat(auth.login(new LoginRequest("u20231100555@usco.edu.co", "Usco#2026", false)).usuario().rol()).isEqualTo("ESTUDIANTE");
        assertThat(CifradoContrasenas.esHash(jdbc.queryForObject(
                "SELECT password_hash FROM usuarios WHERE correo = 'u20231100555@usco.edu.co'", String.class))).isTrue();
    }
}
