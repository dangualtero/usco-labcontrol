package co.edu.usco.labcontrol.service;

import co.edu.usco.labcontrol.domain.Rol;
import co.edu.usco.labcontrol.domain.Usuario;
import co.edu.usco.labcontrol.repo.UsuarioRepository;
import co.edu.usco.labcontrol.web.Dtos.PlantillaDto;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Correos automáticos a partir de las plantillas de Parametrización. Se envían en segundo plano y DESPUÉS de que la
 * transacción se confirma (si algo falla al guardar, no se avisa). Si el correo no está configurado, se registra en
 * la consola del backend.
 */
@Service
public class NotificacionService {
    private static final Logger log = LoggerFactory.getLogger(NotificacionService.class);
    private final ExecutorService hilos = Executors.newFixedThreadPool(2, r -> {
        Thread t = new Thread(r, "labcontrol-correo");
        t.setDaemon(true);
        return t;
    });

    private final ConfiguracionService configuracion;
    private final CorreoService correo;
    private final UsuarioRepository usuarios;
    private final String appUrl;

    public NotificacionService(ConfiguracionService configuracion, CorreoService correo, UsuarioRepository usuarios,
                               @Value("${labcontrol.app-url:http://localhost:5173}") String appUrl) {
        this.configuracion = configuracion; this.correo = correo; this.usuarios = usuarios; this.appUrl = appUrl.replaceAll("/+$", "");
    }

    /** Arma la dirección completa del frontend: enlace("neiva", "/solicitudes/5") → https://…/neiva/solicitudes/5 */
    public String enlace(String sedeSlug, String ruta) { return appUrl + "/" + sedeSlug + ruta; }

    /** Correos de los usuarios activos con ese rol asignados al laboratorio. */
    public Set<String> correosDe(Long laboratorioId, Rol rol) {
        Set<String> out = new LinkedHashSet<>();
        if (laboratorioId != null) usuarios.asignados(laboratorioId, rol).forEach(u -> out.add(u.getCorreo()));
        return out;
    }

    public Set<String> correosAdministradores() {
        Set<String> out = new LinkedHashSet<>();
        usuarios.findByRolAndActivoTrue(Rol.ADMINISTRADOR).forEach(u -> out.add(u.getCorreo()));
        return out;
    }

    public static Set<String> correos(Usuario... us) {
        Set<String> out = new LinkedHashSet<>();
        for (Usuario u : us) if (u != null && u.isActivo()) out.add(u.getCorreo());
        return out;
    }

    /** Envía la plantilla a cada destinatario (después del commit). */
    public void avisar(String plantilla, Collection<String> destinatarios, Map<String, String> vars) {
        if (destinatarios == null || destinatarios.isEmpty()) return;
        Set<String> para = new LinkedHashSet<>(destinatarios);
        Runnable tarea = () -> hilos.submit(() -> enviar(plantilla, para, vars));
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void afterCommit() { tarea.run(); }
            });
        } else {
            tarea.run();
        }
    }

    private void enviar(String codigo, Set<String> para, Map<String, String> vars) {
        try {
            PlantillaDto p = configuracion.plantilla(codigo);
            if (!p.activa()) return;
            String asunto = ConfiguracionService.llenar(p.asunto(), vars);
            String cuerpo = ConfiguracionService.llenar(p.cuerpo(), vars);
            for (String d : para) {
                try { correo.enviar(d, asunto, cuerpo, null); }
                catch (RuntimeException e) { log.warn("No se pudo enviar «{}» a {}: {}", codigo, d, e.getMessage()); }
            }
        } catch (RuntimeException e) {
            log.warn("No se pudo preparar el aviso «{}»: {}", codigo, e.getMessage());
        }
    }
}
