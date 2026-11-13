package co.edu.usco.labcontrol.service;

import co.edu.usco.labcontrol.domain.Equipo;
import co.edu.usco.labcontrol.domain.ReporteNovedad;
import co.edu.usco.labcontrol.domain.ReporteNovedad.Notificacion;
import co.edu.usco.labcontrol.repo.ReporteNovedadRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Envía el aviso de cada novedad al correo institucional. Solo la prioridad ALTA se envía al instante; MEDIA y BAJA
 * se acumulan para el resumen diario.
 *
 * <ol>
 *   <li><b>Agente de IA en n8n</b> (si {@code N8N_WEBHOOK_URL} está definido): el backend le manda el reporte en JSON;
 *       el flujo de n8n lo analiza con un modelo de IA, decide la prioridad (ALTA, MEDIA, BAJA), redacta el correo, lo
 *       envía y responde con lo que hizo. Esa respuesta se guarda en el reporte.</li>
 *   <li><b>Respaldo</b>: si n8n no está configurado o no responde, el backend envía un correo con plantilla y una prioridad
 *       calculada por reglas, para que el aviso nunca se pierda.</li>
 * </ol>
 */
@Component
public class NotificadorNovedades {
    private static final Logger log = LoggerFactory.getLogger(NotificadorNovedades.class);
    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("d 'de' MMMM 'de' yyyy, h:mm a", Locale.forLanguageTag("es-CO"));

    private final ReporteNovedadRepository reportes;
    private final CorreoService correo;
    private final Mapper mapper;
    private final ObjectMapper json;
    private final TransactionTemplate tx;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    private final String destino;
    private final String webhook;
    private final String token;
    private final int timeout;
    private final String appUrl;

    public NotificadorNovedades(ReporteNovedadRepository reportes, CorreoService correo, Mapper mapper, ObjectMapper json,
                                TransactionTemplate tx,
                                @Value("${labcontrol.novedades.destino:u20232215593@usco.edu.co}") String destino,
                                @Value("${labcontrol.novedades.n8n-webhook-url:}") String webhook,
                                @Value("${labcontrol.novedades.n8n-token:}") String token,
                                @Value("${labcontrol.novedades.n8n-timeout-segundos:90}") int timeout,
                                @Value("${labcontrol.app-url:http://localhost:5173}") String appUrl) {
        this.reportes = reportes; this.correo = correo; this.mapper = mapper; this.json = json; this.tx = tx;
        this.destino = destino; this.webhook = webhook == null ? "" : webhook.trim(); this.token = token;
        this.timeout = timeout; this.appUrl = appUrl.replaceAll("/+$", "");
    }

    /** Se ejecuta en otro hilo, DESPUÉS de que el reporte quedó guardado (si la transacción falla, no se avisa). */
    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void alRegistrar(NovedadService.NovedadRegistrada ev) {
        try { notificar(ev.id(), ev.forzar()); }
        catch (Exception e) { log.error("No se pudo notificar la novedad {}", ev.id(), e); }
    }

    void notificar(Long id) { notificar(id, false); }

    /**
     * Política de correo: solo la prioridad ALTA se envía al instante. MEDIA y BAJA quedan en PENDIENTE_RESUMEN y
     * salen en el resumen diario ({@link ResumenDiarioService}). forzar = enviar ya, sea cual sea la prioridad.
     */
    void notificar(Long id, boolean forzar) {
        Map<String, Object> payload = tx.execute(s -> reportes.findById(id).map(this::payload).orElse(null));
        if (payload == null) return;
        payload.put("forzarCorreo", forzar);

        String errorN8n = null;
        if (!webhook.isEmpty()) {
            try {
                JsonNode r = llamarN8n(payload);
                String prioridad = prioridad(r.path("prioridad").asText(null));
                if (prioridad == null) prioridad = prioridadPorReglas((String) payload.get("tipo"));
                boolean enviado = r.has("correoEnviado") ? r.path("correoEnviado").asBoolean(false) : "ALTA".equals(prioridad) || forzar;
                String asunto = texto(r, "asunto"), resumen = texto(r, "resumen"), accion = texto(r, "accionSugerida");
                if (enviado) {
                    guardar(id, Notificacion.ENVIADA_IA, prioridad, asunto, resumen, accion, "Aviso redactado, priorizado y enviado por el agente de IA (n8n)");
                    return;
                }
                if (!"ALTA".equals(prioridad) && !forzar) {
                    paraResumen(id, prioridad, asunto, resumen, accion, "Prioridad " + prioridad + " (IA): irá en el resumen diario");
                    return;
                }
                errorN8n = "n8n no pudo enviar el correo";
            } catch (Exception e) {
                errorN8n = "n8n no respondió (" + e.getClass().getSimpleName() + (e.getMessage() == null ? "" : ": " + e.getMessage()) + ")";
                log.warn("Novedad {}: {}. Se usará el correo de respaldo.", id, errorN8n);
            }
        }

        // Respaldo: plantilla + prioridad por reglas
        String prioridad = prioridadPorReglas((String) payload.get("tipo"));
        @SuppressWarnings("unchecked") Map<String, Object> eq = (Map<String, Object>) payload.get("equipo");
        String asunto = "[" + prioridad + "] Novedad en " + eq.get("placa") + " · " + payload.get("tipoTexto") + " · " + ((Map<?, ?>) payload.get("sede")).get("nombre");
        String base = errorN8n == null ? "" : errorN8n + ". ";
        if (!"ALTA".equals(prioridad) && !forzar) {
            paraResumen(id, prioridad, asunto, null, null, base + "Prioridad " + prioridad + " (por reglas): irá en el resumen diario");
            return;
        }
        try {
            boolean enviado = correo.enviar(destino, asunto, textoPlantilla(payload, prioridad), null);
            guardar(id, enviado ? Notificacion.ENVIADA_PLANTILLA : Notificacion.SIN_CONFIGURAR, prioridad, asunto, null, null,
                    enviado ? base + "Se envió el aviso con plantilla" : base + "Correo sin configurar (MAIL_HOST): el aviso quedó en la consola del backend");
        } catch (IllegalStateException e) {
            guardar(id, Notificacion.FALLIDA, prioridad, asunto, null, null, base + "El servidor de correo falló: " + e.getMessage());
        }
    }

    private void paraResumen(Long id, String prioridad, String asunto, String resumen, String accion, String detalle) {
        tx.executeWithoutResult(s -> reportes.findById(id).ifPresent(r -> r.paraResumen(prioridad, asunto, resumen, accion, detalle, destino)));
    }

    private JsonNode llamarN8n(Map<String, Object> payload) throws Exception {
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(webhook))
                .timeout(Duration.ofSeconds(timeout))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(payload), StandardCharsets.UTF_8));
        if (token != null && !token.isBlank()) b.header("X-LabControl-Token", token);
        HttpResponse<String> res = http.send(b.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (res.statusCode() / 100 != 2) throw new IllegalStateException("HTTP " + res.statusCode());
        String cuerpo = res.body() == null ? "" : res.body().trim();
        JsonNode n = cuerpo.isEmpty() ? json.createObjectNode() : json.readTree(cuerpo);
        return n.isArray() && !n.isEmpty() ? n.get(0) : n;   // n8n a veces responde [ {...} ]
    }

    private void guardar(Long id, Notificacion n, String prioridad, String asunto, String resumen, String accion, String detalle) {
        tx.executeWithoutResult(s -> reportes.findById(id).ifPresent(r ->
                r.resultado(n, prioridad, asunto, resumen, accion, detalle, destino, mapper.ahora())));
    }

    /** Datos que recibe el agente de IA en n8n (y que usa la plantilla de respaldo). */
    private Map<String, Object> payload(ReporteNovedad n) {
        Equipo e = n.getEquipo();
        Map<String, Object> equipo = new LinkedHashMap<>();
        equipo.put("placa", e.getPlaca());
        equipo.put("nombre", e.getNombre());
        equipo.put("marca", e.getMarca());
        equipo.put("modelo", e.getModelo());
        equipo.put("categoria", e.getCategoria());
        equipo.put("laboratorio", e.getLaboratorio());
        equipo.put("ubicacion", e.getUbicacion());
        equipo.put("estadoActual", e.getEstado().name());
        Map<String, Object> sede = new LinkedHashMap<>();
        sede.put("slug", n.getSede().getSlug());
        sede.put("nombre", n.getSede().getNombre());
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("reporteId", n.getId());
        p.put("tipo", n.getTipo().name());
        p.put("tipoTexto", n.getTipo().texto());
        p.put("descripcion", n.getDescripcion());
        p.put("enviadoAMantenimiento", n.isEnMantenimiento());
        p.put("reportadoPor", n.getReportadoPor());
        p.put("reportadoPorCorreo", n.getReportadoPorCorreo());
        p.put("fecha", n.getCreadoEn().toString());
        p.put("fechaTexto", FECHA.format(n.getCreadoEn()));
        p.put("sede", sede);
        p.put("equipo", equipo);
        p.put("destinatario", destino);
        p.put("enlace", appUrl + "/" + n.getSede().getSlug() + "/coordinador/reportes");
        p.put("politicaCorreo", "Solo la prioridad ALTA se envía al instante; MEDIA y BAJA van en el resumen diario");
        return p;
    }

    private static String textoPlantilla(Map<String, Object> p, String prioridad) {
        @SuppressWarnings("unchecked") Map<String, Object> eq = (Map<String, Object>) p.get("equipo");
        return """
                Novedad reportada en USCO LabControl

                Prioridad:   %s (calculada por reglas; el agente de IA no estaba disponible)
                Sede:        %s
                Equipo:      %s · %s
                Laboratorio: %s%s
                Novedad:     %s
                Reportó:     %s
                Fecha:       %s
                Mantenimiento: %s

                Descripción del auxiliar:
                %s

                Ver reportes: %s
                """.formatted(prioridad, ((Map<?, ?>) p.get("sede")).get("nombre"), eq.get("placa"), eq.get("nombre"), eq.get("laboratorio"),
                eq.get("ubicacion") == null ? "" : " · " + eq.get("ubicacion"), p.get("tipoTexto"), p.get("reportadoPor"), p.get("fechaTexto"),
                Boolean.TRUE.equals(p.get("enviadoAMantenimiento")) ? "sí, se retiró del préstamo" : "no", p.get("descripcion"), p.get("enlace"));
    }

    static String prioridadPorReglas(String tipo) {
        return switch (tipo) {
            case "PERDIDO", "DANADO" -> "ALTA";
            case "FALLA", "AGOTADO" -> "MEDIA";
            default -> "BAJA";
        };
    }

    /** Solo se aceptan ALTA, MEDIA o BAJA (la IA podría responder otra cosa). */
    static String prioridad(String s) {
        if (s == null) return null;
        String p = s.trim().toUpperCase(Locale.ROOT);
        return p.startsWith("ALTA") || p.startsWith("URGENTE") ? "ALTA" : p.startsWith("MEDIA") ? "MEDIA" : p.startsWith("BAJA") ? "BAJA" : null;
    }

    private static String texto(JsonNode n, String campo) {
        JsonNode v = n.get(campo);
        return v == null || v.isNull() ? null : v.isTextual() ? v.asText() : v.toString();
    }
}
