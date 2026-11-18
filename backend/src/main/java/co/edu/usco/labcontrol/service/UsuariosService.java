package co.edu.usco.labcontrol.service;

import co.edu.usco.labcontrol.domain.*;
import co.edu.usco.labcontrol.repo.LaboratorioRepository;
import co.edu.usco.labcontrol.repo.UsuarioRepository;
import co.edu.usco.labcontrol.web.ApiException;
import co.edu.usco.labcontrol.web.Dtos.*;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Gestión de usuarios por el Administrador: crea cuentas, asigna el rol (ADMINISTRADOR, COORDINADOR_LAB o MONITOR) y
 * los laboratorios (uno o varios) de coordinadores y monitores. Las contraseñas se guardan únicamente con BCrypt.
 */
@Service
public class UsuariosService {
    private final UsuarioRepository usuarios;
    private final LaboratorioRepository laboratorios;
    private final PasswordEncoder encoder;
    private final Mapper mapper;
    private final Acceso acceso;

    public UsuariosService(UsuarioRepository usuarios, LaboratorioRepository laboratorios, PasswordEncoder encoder, Mapper mapper,
                           Acceso acceso) {
        this.usuarios = usuarios; this.laboratorios = laboratorios; this.encoder = encoder; this.mapper = mapper; this.acceso = acceso;
    }

    /** Usuarios de la sede abierta + los administradores generales (que no tienen sede). */
    @Transactional(readOnly = true)
    public List<UsuarioAdminDto> listar(Long actorId) {
        Usuario actor = acceso.usuario(actorId);
        acceso.exigir(actor, Permiso.USUARIOS_GESTIONAR);
        Sede sede = acceso.sede(actor);
        return usuarios.findAllByOrderByRolAscNombreAsc().stream()
                .filter(u -> u.getSede() == null || sede.es(u.getSede()))
                .map(mapper::usuarioAdmin).toList();
    }

    @Transactional
    public UsuarioAdminDto crear(Long actorId, UsuarioRequest r) {
        Usuario actor = acceso.usuario(actorId);
        acceso.exigir(actor, Permiso.USUARIOS_GESTIONAR);
        String nombre = Texto.obligatorio(r.nombre(), 3, 120, "El nombre");
        String correo = r.correo() == null ? "" : r.correo().trim().toLowerCase();
        if (!AuthService.CORREO_USCO.matcher(correo).matches()) throw ApiException.badRequest("Formato de correo no válido (@usco.edu.co)");
        if (usuarios.existsByCorreoIgnoreCase(correo)) throw ApiException.conflict("Ya existe un usuario con ese correo");
        String doc = documento(r.documento());
        if (usuarios.existsByDocumento(doc)) throw ApiException.conflict("Ya existe un usuario con ese documento");
        Rol rol = rol(r.rol());
        AuthService.validarClave(r.password());
        Usuario u = new Usuario(nombre, correo, encoder.encode(r.password()), rol, doc, Texto.opcional(r.programa(), 80, "El cargo o dependencia"));
        u.setTelefono(Texto.opcional(r.telefono(), 30, "El teléfono"));
        asignar(actor, u, rol, r.laboratorios());
        return mapper.usuarioAdmin(usuarios.save(u));
    }

    @Transactional
    public UsuarioAdminDto actualizar(Long actorId, Long id, UsuarioActualizarRequest r) {
        Usuario actor = acceso.usuario(actorId);
        acceso.exigir(actor, Permiso.USUARIOS_GESTIONAR);
        Usuario u = usuarios.findById(id == null ? -1L : id).orElseThrow(() -> ApiException.notFound("Usuario no encontrado"));
        Rol rol = rol(r.rol());
        if (u.getId().equals(actor.getId()) && rol != Rol.ADMINISTRADOR) throw ApiException.conflict("No puedes quitarte el rol de Administrador");
        u.setNombre(Texto.obligatorio(r.nombre(), 3, 120, "El nombre"));
        if (r.documento() != null && !r.documento().isBlank()) {
            String doc = documento(r.documento());
            usuarios.findByDocumento(doc).filter(o -> !o.getId().equals(u.getId()))
                    .ifPresent(o -> { throw ApiException.conflict("Ya existe un usuario con ese documento"); });
            u.setDocumento(doc);
        }
        u.setPrograma(Texto.opcional(r.programa(), 80, "El cargo o dependencia"));
        u.setTelefono(Texto.opcional(r.telefono(), 30, "El teléfono"));
        u.setRol(rol);
        asignar(actor, u, rol, r.laboratorios());
        return mapper.usuarioAdmin(u);
    }

    @Transactional
    public UsuarioAdminDto cambiarActivo(Long actorId, Long id, boolean activo) {
        Usuario actor = acceso.usuario(actorId);
        acceso.exigir(actor, Permiso.USUARIOS_GESTIONAR);
        Usuario u = usuarios.findById(id == null ? -1L : id).orElseThrow(() -> ApiException.notFound("Usuario no encontrado"));
        if (u.getId().equals(actorId) && !activo) throw ApiException.conflict("No puedes desactivar tu propia cuenta");
        if (activo && u.getRol() != Rol.ADMINISTRADOR && u.getLaboratorios().isEmpty()) {
            throw ApiException.conflict("Asígnale al menos un laboratorio antes de activarlo (botón Editar)");
        }
        u.setActivo(activo);
        return mapper.usuarioAdmin(u);
    }

    @Transactional
    public void restablecerClave(Long actorId, Long id, String nueva) {
        Usuario actor = acceso.usuario(actorId);
        acceso.exigir(actor, Permiso.USUARIOS_GESTIONAR);
        Usuario u = usuarios.findById(id == null ? -1L : id).orElseThrow(() -> ApiException.notFound("Usuario no encontrado"));
        AuthService.validarClave(nueva);
        u.setPasswordHash(encoder.encode(nueva));
    }

    /**
     * El Administrador es general: sin sede ni laboratorios. Coordinador y Monitor: uno o varios laboratorios activos de la
     * sede abierta, y su sede pasa a ser esa.
     */
    private void asignar(Usuario actor, Usuario u, Rol rol, List<Long> ids) {
        u.getLaboratorios().clear();
        if (rol == Rol.ADMINISTRADOR) { u.setSede(null); return; }
        Sede sede = acceso.sede(actor);
        Set<Long> unicos = new HashSet<>(ids == null ? List.of() : ids);
        if (unicos.isEmpty()) throw ApiException.badRequest("Selecciona al menos un laboratorio para el " + rol.texto());
        for (Long labId : unicos) {
            Laboratorio l = laboratorios.findById(labId == null ? -1L : labId)
                    .filter(x -> x.getSede().es(sede))
                    .orElseThrow(() -> ApiException.badRequest("Uno de los laboratorios no pertenece a la " + sede.getNombre()));
            if (!l.isActivo()) throw ApiException.badRequest("El laboratorio «" + l.getNombre() + "» está inactivo");
            u.getLaboratorios().add(l);
        }
        u.setSede(sede);
    }

    private static Rol rol(String s) {
        Rol r = Rol.de(s);
        if (r == null) throw ApiException.badRequest("Rol no válido: ADMINISTRADOR, COORDINADOR_LAB o MONITOR");
        return r;
    }

    private static String documento(String s) {
        String doc = s == null ? "" : s.trim();
        if (!doc.matches("\\d{5,15}")) throw ApiException.badRequest("El documento debe tener entre 5 y 15 dígitos");
        return doc;
    }
}
