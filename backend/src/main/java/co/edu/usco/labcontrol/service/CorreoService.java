package co.edu.usco.labcontrol.service;

import jakarta.mail.internet.MimeMessage;
import java.nio.charset.StandardCharsets;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

/**
 * Envío de correos por SMTP (códigos de verificación y respaldo de los avisos de novedades).
 *
 * <p>Si no hay servidor configurado ({@code MAIL_HOST} vacío) no falla: escribe el correo en la consola del backend y
 * devuelve {@code false}. Así se puede probar el registro en local sin una cuenta de correo.
 */
@Service
public class CorreoService {
    private static final Logger log = LoggerFactory.getLogger(CorreoService.class);

    private final ObjectProvider<JavaMailSender> mailSender;
    private final String host;
    private final String remitente;
    private final String nombreRemitente;

    public CorreoService(ObjectProvider<JavaMailSender> mailSender,
                         @Value("${spring.mail.host:}") String host,
                         @Value("${labcontrol.correo.remitente:}") String remitente,
                         @Value("${labcontrol.correo.nombre-remitente:USCO LabControl}") String nombreRemitente) {
        this.mailSender = mailSender; this.host = host; this.remitente = remitente; this.nombreRemitente = nombreRemitente;
    }

    public boolean configurado() {
        return host != null && !host.isBlank() && mailSender.getIfAvailable() != null;
    }

    /**
     * Envía un correo en texto plano con una versión HTML opcional.
     * @return true si se entregó al servidor SMTP; false si el correo no está configurado (modo desarrollo).
     * @throws IllegalStateException si el servidor SMTP rechaza o no responde.
     */
    public boolean enviar(String para, String asunto, String texto, String html) {
        if (!configurado()) {
            log.warn("""

                    ===== CORREO NO ENVIADO (MAIL_HOST sin configurar) =====
                    Para:   {}
                    Asunto: {}
                    {}
                    ========================================================""", para, asunto, texto);
            return false;
        }
        try {
            JavaMailSender sender = mailSender.getObject();
            MimeMessage m = sender.createMimeMessage();
            MimeMessageHelper h = new MimeMessageHelper(m, html != null, StandardCharsets.UTF_8.name());
            h.setTo(para);
            if (remitente != null && !remitente.isBlank()) h.setFrom(remitente, nombreRemitente);
            h.setSubject(asunto);
            if (html != null) h.setText(texto, html); else h.setText(texto);
            sender.send(m);
            log.info("Correo enviado a {}: {}", para, asunto);
            return true;
        } catch (Exception e) {
            log.error("No se pudo enviar el correo a {}: {}", para, e.getMessage());
            throw new IllegalStateException("El servidor de correo no aceptó el mensaje: " + e.getMessage(), e);
        }
    }

    /** Escapa texto para insertarlo en el HTML del correo. */
    public static String html(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }
}
