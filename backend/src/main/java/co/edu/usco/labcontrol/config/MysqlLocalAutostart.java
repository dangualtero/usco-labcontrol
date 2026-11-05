package co.edu.usco.labcontrol.config;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;

/**
 * Comodidad de desarrollo en Windows: si la base de datos apunta a un MySQL LOCAL (127.0.0.1 / localhost) que no está
 * escuchando, ejecuta {@code iniciar-mysql.ps1} para levantar la instancia portátil del proyecto antes de que Spring
 * abra la conexión. Así basta con pulsar «Run» en IntelliJ.
 *
 * <p>No hace nada si MySQL ya responde, si la URL no es local, si el sistema no es Windows o si el script no existe
 * (por ejemplo en producción). Se desactiva con {@code labcontrol.mysql.autostart=false}.
 */
public class MysqlLocalAutostart implements EnvironmentPostProcessor, Ordered {
    private static final Pattern URL_LOCAL = Pattern.compile("jdbc:mysql://(?:localhost|127\\.0\\.0\\.1):(\\d+)/");

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment env, SpringApplication app) {
        if (!Boolean.TRUE.equals(env.getProperty("labcontrol.mysql.autostart", Boolean.class, true))) return;
        if (!System.getProperty("os.name", "").toLowerCase().contains("win")) return;
        Matcher m = URL_LOCAL.matcher(env.getProperty("spring.datasource.url", ""));
        if (!m.find()) return;
        int puerto = Integer.parseInt(m.group(1));
        if (escuchando(puerto)) return;

        Path script = buscarScript(env.getProperty("labcontrol.mysql.script"));
        if (script == null) {
            log("MySQL no responde en 127.0.0.1:" + puerto + " y no se encontró iniciar-mysql.ps1; inícielo manualmente.");
            return;
        }
        log("MySQL no responde en 127.0.0.1:" + puerto + ". Iniciándolo con " + script + " ...");
        try {
            Process p = new ProcessBuilder("powershell", "-NoProfile", "-ExecutionPolicy", "Bypass", "-File",
                    script.toString(), "-Puerto", String.valueOf(puerto))
                    .redirectErrorStream(true).redirectOutput(ProcessBuilder.Redirect.DISCARD).start();
            if (!p.waitFor(120, TimeUnit.SECONDS)) p.destroy();
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) Thread.currentThread().interrupt();
            log("No se pudo ejecutar el script de MySQL: " + e.getMessage());
            return;
        }
        for (int i = 0; i < 40 && !escuchando(puerto); i++) pausa(500);
        log(escuchando(puerto) ? "MySQL listo en el puerto " + puerto + "." : "MySQL no quedó disponible en el puerto " + puerto + ".");
    }

    private static Path buscarScript(String configurado) {
        Path base = Paths.get(System.getProperty("user.dir")).toAbsolutePath();
        List<Path> candidatos = new java.util.ArrayList<>();
        if (configurado != null && !configurado.isBlank()) candidatos.add(Paths.get(configurado));
        candidatos.add(base.resolve("iniciar-mysql.ps1"));
        candidatos.add(base.resolve("..").resolve("iniciar-mysql.ps1"));
        candidatos.add(base.resolve("..").resolve("..").resolve("iniciar-mysql.ps1"));
        return candidatos.stream().map(Path::normalize).filter(Files::isRegularFile).findFirst().orElse(null);
    }

    private static boolean escuchando(int puerto) {
        try (Socket s = new Socket()) {
            s.connect(new InetSocketAddress("127.0.0.1", puerto), 700);
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    private static void pausa(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }

    private static void log(String msg) { System.out.println("[LabControl] " + msg); }

    @Override
    public int getOrder() { return Ordered.LOWEST_PRECEDENCE; }   // después de cargar application.yml
}
