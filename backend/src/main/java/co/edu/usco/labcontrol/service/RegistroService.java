package co.edu.usco.labcontrol.service;

import co.edu.usco.labcontrol.domain.RegistroPendiente;
import co.edu.usco.labcontrol.domain.Rol;
import co.edu.usco.labcontrol.domain.Sede;
import co.edu.usco.labcontrol.domain.Usuario;
import co.edu.usco.labcontrol.repo.RegistroPendienteRepository;
import co.edu.usco.labcontrol.repo.SedeRepository;
import co.edu.usco.labcontrol.repo.UsuarioRepository;
import co.edu.usco.labcontrol.security.JwtService;
import co.edu.usco.labcontrol.web.ApiException;
import co.edu.usco.labcontrol.web.Dtos.*;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.interceptor.TransactionAspectSupport;

/**
 * Autorregistro de estudiantes con verificación del correo institucional.
 *
 * <ol>
 *   <li>{@link #iniciar}: valida los datos, guarda una solicitud PENDIENTE y envía un código de 6 dígitos al @usco.edu.co.</li>
 *   <li>{@link #verificar}: si el código es correcto (y no venció), crea el usuario ESTUDIANTE en su sede y abre la sesión.</li>
 * </ol>
 * Protecciones: el código vence en 15 minutos, admite 5 intentos, se puede reenviar cada 60 s y como máximo 5 veces
 * por hora; código y contraseña se guardan con BCrypt. Solo se registran estudiantes: docentes, auxiliares y
 * coordinadores los sigue creando el Coordinador.
 */
@Service
public class RegistroService {
    private static final int MAX_INTENTOS = 5;
    private static final int MAX_ENVIOS_HORA = 5;
    private static final Duration ESPERA_REENVIO = Duration.ofSeconds(60);
    private static final SecureRandom AZAR = new SecureRandom();

    private final RegistroPendienteRepository pendientes;
    private final UsuarioRepository usuarios;
    private final SedeRepository sedes;
    private final PasswordEncoder encoder;
    private final CorreoService correo;
    private final JwtService jwt;
    private final Mapper mapper;
    private final boolean habilitado;
    private final int minutosCodigo;
    private final String appUrl;

    public RegistroService(RegistroPendienteRepository pendientes, UsuarioRepository usuarios, SedeRepository sedes,
                           PasswordEncoder encoder, CorreoService correo, JwtService jwt, Mapper mapper,
                           @Value("${labcontrol.registro.habilitado:true}") boolean habilitado,
                           @Value("${labcontrol.registro.minutos-codigo:15}") int minutosCodigo,
                           @Value("${labcontrol.app-url:http://localhost:5173}") String appUrl) {
        this.pendientes = pendientes; this.usuarios = usuarios; this.sedes = sedes; this.encoder = encoder;
        this.correo = correo; this.jwt = jwt; this.mapper = mapper; this.habilitado = habilitado;
        this.minutosCodigo = minutosCodigo; this.appUrl = appUrl.replaceAll("/+$", "");
    }

    /** Paso 1: datos del estudiante → código al correo. Si ya había una solicitud para ese correo, se reemplaza. */
    @Transactional(noRollbackFor = ApiException.class)
    public RegistroIniciadoDto iniciar(RegistroRequest r) {
        if (!habilitado) throw new ApiException(HttpStatus.FORBIDDEN, "El registro en línea está deshabilitado. Solicita tu cuenta en la Coordinación de Laboratorios.");
        LocalDateTime ahora = mapper.ahora();
        pendientes.borrarVencidos(ahora.minusHours(24));

        String nombre = limpio(r.nombre());
        if (nombre == null || nombre.length() < 5 || !nombre.contains(" ")) throw ApiException.badRequest("Escribe tu nombre completo (nombres y apellidos)");
        if (nombre.length() > 120) throw ApiException.badRequest("El nombre es demasiado largo");
        String mail = r.correo() == null ? "" : r.correo().trim().toLowerCase();
        if (!AuthService.CORREO_USCO.matcher(mail).matches()) throw ApiException.badRequest("Usa tu correo institucional @usco.edu.co");
        if (usuarios.existsByCorreoIgnoreCase(mail)) throw ApiException.conflict("Ya existe una cuenta con este correo. Inicia sesión o usa «¿Olvidaste tu contraseña?» en la Coordinación.");
        String doc = r.documento() == null ? "" : r.documento().trim();
        if (!doc.matches("\\d{5,15}")) throw ApiException.badRequest("El documento debe tener entre 5 y 15 dígitos");
        if (usuarios.existsByDocumento(doc) || pendientes.existsByDocumentoAndCorreoNot(doc, mail)) {
            throw ApiException.conflict("Ya existe una cuenta o un registro en curso con ese documento");
        }
        String codigo = r.codigoEstudiantil() == null ? "" : r.codigoEstudiantil().trim();
        if (!codigo.matches("\\d{6,20}")) throw ApiException.badRequest("El código estudiantil debe tener solo números (ej. 20232215593)");
        if (usuarios.findByDocumentoOrCodigoEstudiantil(codigo, codigo).isPresent()) throw ApiException.conflict("Ya existe una cuenta con ese código estudiantil");
        String programa = limpio(r.programa());
        if (programa == null || programa.length() < 3) throw ApiException.badRequest("Indica tu programa académico");
        if (programa.length() > 80) throw ApiException.badRequest("El nombre del programa es demasiado largo");
        AuthService.validarClave(r.password());
        Sede sede = sedes.findBySlug(r.sede() == null ? "" : r.sede().trim().toLowerCase())
                .filter(Sede::isActiva).orElseThrow(() -> ApiException.badRequest("Elige una sede válida"));

        RegistroPendiente p = pendientes.findByCorreoIgnoreCase(mail).orElse(null);
        if (p != null) controlarReenvio(p, ahora);
        else p = new RegistroPendiente(mail, ahora);
        p.datos(nombre, doc, codigo, programa, encoder.encode(r.password()), sede);
        return enviarCodigo(pendientes.save(p), ahora);
    }

    /** Reenvía un código nuevo (el anterior deja de servir). */
    @Transactional(noRollbackFor = ApiException.class)
    public RegistroIniciadoDto reenviar(ReenviarCodigoRequest r) {
        String mail = r.correo() == null ? "" : r.correo().trim().toLowerCase();
        RegistroPendiente p = pendientes.findByCorreoIgnoreCase(mail)
                .orElseThrow(() -> ApiException.notFound("No hay un registro en curso para ese correo. Vuelve a llenar el formulario."));
        LocalDateTime ahora = mapper.ahora();
        controlarReenvio(p, ahora);
        return enviarCodigo(p, ahora);
    }

    /** Paso 2: código correcto → se crea el usuario ESTUDIANTE y se devuelve la sesión iniciada. */
    @Transactional(noRollbackFor = ApiException.class)
    public LoginResponse verificar(VerificarRegistroRequest r) {
        String mail = r.correo() == null ? "" : r.correo().trim().toLowerCase();
        String codigo = r.codigo() == null ? "" : r.codigo().replaceAll("\\s", "");
        RegistroPendiente p = pendientes.findByCorreoIgnoreCase(mail)
                .orElseThrow(() -> ApiException.notFound("No hay un registro en curso para ese correo. Vuelve a llenar el formulario."));
        if (p.getIntentos() >= MAX_INTENTOS) {
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "Superaste los " + MAX_INTENTOS + " intentos. Solicita un código nuevo.");
        }
        if (p.getExpiraEn().isBefore(mapper.ahora())) throw new ApiException(HttpStatus.GONE, "El código venció. Solicita uno nuevo.");
        if (!codigo.matches("\\d{6}") || !encoder.matches(codigo, p.getCodigoHash())) {
            p.fallo();
            int quedan = MAX_INTENTOS - p.getIntentos();
            throw ApiException.badRequest(quedan > 0 ? "Código incorrecto. Te quedan " + quedan + (quedan == 1 ? " intento." : " intentos.")
                    : "Código incorrecto. Solicita un código nuevo.");
        }
        // Se repiten las comprobaciones: otra persona pudo registrarse con esos datos mientras tanto.
        if (usuarios.existsByCorreoIgnoreCase(mail)) { pendientes.delete(p); throw ApiException.conflict("Este correo ya tiene una cuenta. Inicia sesión."); }
        if (usuarios.existsByDocumento(p.getDocumento())
                || usuarios.findByDocumentoOrCodigoEstudiantil(p.getCodigoEstudiantil(), p.getCodigoEstudiantil()).isPresent()) {
            pendientes.delete(p);
            throw ApiException.conflict("Ya existe una cuenta con ese documento o código estudiantil");
        }
        if (!p.getSede().isActiva()) throw new ApiException(HttpStatus.FORBIDDEN, "La " + p.getSede().getNombre() + " está inactiva.");

        Usuario u = new Usuario(p.getNombre(), p.getCorreo(), p.getPasswordHash(), Rol.ESTUDIANTE, p.getDocumento(),
                p.getCodigoEstudiantil(), p.getPrograma());
        u.setSede(p.getSede());
        u = usuarios.save(u);
        pendientes.delete(p);
        return new LoginResponse(jwt.generar(u, false), mapper.usuario(u), jwt.expiraEnSegundos(false));
    }

    private void controlarReenvio(RegistroPendiente p, LocalDateTime ahora) {
        long espera = ESPERA_REENVIO.getSeconds() - Duration.between(p.getUltimoEnvio(), ahora).getSeconds();
        if (espera > 0) throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "Espera " + espera + " segundos para pedir otro código.");
        if (p.getEnviosVentana() >= MAX_ENVIOS_HORA && p.getVentanaDesde().plusHours(1).isAfter(ahora)) {
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "Pediste demasiados códigos. Intenta de nuevo en una hora.");
        }
    }

    private RegistroIniciadoDto enviarCodigo(RegistroPendiente p, LocalDateTime ahora) {
        String codigo = String.format("%06d", AZAR.nextInt(1_000_000));
        p.nuevoCodigo(encoder.encode(codigo), ahora, ahora.plusMinutes(minutosCodigo));
        String primerNombre = p.getNombre().split(" ")[0];
        String enlace = appUrl + "/" + p.getSede().getSlug() + "/registro";
        String texto = """
                Hola %s:

                Tu código de verificación de USCO LabControl es: %s

                Escríbelo en la pantalla de registro (%s). Vence en %d minutos.
                Si no solicitaste esta cuenta, ignora este mensaje: no se creará ningún usuario.

                Universidad Surcolombiana · %s""".formatted(primerNombre, codigo, enlace, minutosCodigo, p.getSede().getNombre());
        String html = """
                <div style="font-family:Arial,sans-serif;max-width:480px;margin:auto;border:1px solid #e2e8f0;border-radius:12px;overflow:hidden">
                  <div style="background:#8F141B;color:#fff;padding:16px 20px;font-weight:bold">USCO LabControl · %s</div>
                  <div style="padding:20px;color:#1e293b">
                    <p>Hola %s:</p>
                    <p>Tu código de verificación es:</p>
                    <p style="font-size:32px;font-weight:bold;letter-spacing:8px;text-align:center;background:#f1f5f9;border-radius:8px;padding:12px">%s</p>
                    <p>Escríbelo en la pantalla de registro. Vence en <b>%d minutos</b>.</p>
                    <p style="font-size:12px;color:#64748b">Si no solicitaste esta cuenta, ignora este mensaje: no se creará ningún usuario.</p>
                  </div>
                </div>""".formatted(CorreoService.html(p.getSede().getNombre()), CorreoService.html(primerNombre), codigo, minutosCodigo);
        boolean enviado;
        try {
            enviado = correo.enviar(p.getCorreo(), "Tu código de verificación: " + codigo + " · USCO LabControl", texto, html);
        } catch (IllegalStateException e) {
            // No se guarda la solicitud: así la persona puede reintentar de inmediato, sin esperar el minuto de reenvío.
            TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "No pudimos enviar el código a tu correo. Intenta de nuevo en unos minutos.");
        }
        return new RegistroIniciadoDto(p.getCorreo(), minutosCodigo, (int) ESPERA_REENVIO.getSeconds(), enviado);
    }

    private static String limpio(String s) { return s == null || s.isBlank() ? null : s.trim().replaceAll("\\s+", " "); }
}
