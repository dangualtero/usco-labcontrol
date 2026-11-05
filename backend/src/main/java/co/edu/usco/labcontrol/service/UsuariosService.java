package co.edu.usco.labcontrol.service;

import co.edu.usco.labcontrol.domain.Rol;
import co.edu.usco.labcontrol.domain.Usuario;
import co.edu.usco.labcontrol.repo.UsuarioRepository;
import co.edu.usco.labcontrol.web.ApiException;
import co.edu.usco.labcontrol.web.Dtos.*;
import java.util.List;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Gestión de usuarios por el Coordinador. Las contraseñas se guardan únicamente con BCrypt. */
@Service
public class UsuariosService {
    private final UsuarioRepository usuarios;
    private final PasswordEncoder encoder;
    private final Mapper mapper;
    private final SedeContexto sedes;

    public UsuariosService(UsuarioRepository usuarios, PasswordEncoder encoder, Mapper mapper, SedeContexto sedes) {
        this.usuarios = usuarios; this.encoder = encoder; this.mapper = mapper; this.sedes = sedes;
    }

    /** Usuarios de la sede seleccionada + los coordinadores generales (que no tienen sede). */
    @Transactional(readOnly = true)
    public List<UsuarioAdminDto> listar(Long actorId) {
        var sede = sedes.de(actorId);
        return usuarios.findAllByOrderByRolAscNombreAsc().stream()
                .filter(u -> u.getSede() == null || sede.es(u.getSede()))
                .map(mapper::usuarioAdmin).toList();
    }

    /** Crea el usuario en la sede seleccionada. El rol COORDINADOR es general: queda sin sede y administra todas. */
    @Transactional
    public UsuarioAdminDto crear(Long actorId, UsuarioRequest r) {
        String nombre = r.nombre() == null ? "" : r.nombre().trim();
        if (nombre.length() < 3) throw ApiException.badRequest("El nombre es obligatorio");
        String correo = r.correo() == null ? "" : r.correo().trim().toLowerCase();
        if (!AuthService.CORREO_USCO.matcher(correo).matches()) throw ApiException.badRequest("Formato de correo no válido (@usco.edu.co)");
        if (usuarios.existsByCorreoIgnoreCase(correo)) throw ApiException.conflict("Ya existe un usuario con ese correo");
        String doc = r.documento() == null ? "" : r.documento().trim();
        if (!doc.matches("\\d{5,15}")) throw ApiException.badRequest("El documento debe tener entre 5 y 15 dígitos");
        if (usuarios.existsByDocumento(doc)) throw ApiException.conflict("Ya existe un usuario con ese documento");
        Rol rol;
        try { rol = Rol.valueOf(r.rol() == null ? "" : r.rol().trim().toUpperCase()); }
        catch (IllegalArgumentException e) { throw ApiException.badRequest("Rol no válido: ESTUDIANTE, DOCENTE, AUXILIAR o COORDINADOR"); }
        String codigo = r.codigoEstudiantil() == null || r.codigoEstudiantil().isBlank() ? null : r.codigoEstudiantil().trim();
        if (codigo != null && usuarios.findByDocumentoOrCodigoEstudiantil(codigo, codigo).isPresent()) throw ApiException.conflict("Ya existe un usuario con ese código");
        AuthService.validarClave(r.password());
        Usuario u = new Usuario(nombre, correo, encoder.encode(r.password()), rol, doc, codigo,
                r.programa() == null || r.programa().isBlank() ? null : r.programa().trim());
        if (rol != Rol.COORDINADOR) u.setSede(sedes.de(actorId));
        return mapper.usuarioAdmin(usuarios.save(u));
    }

    @Transactional
    public UsuarioAdminDto cambiarActivo(Long actorId, Long id, boolean activo) {
        Usuario u = usuarios.findById(id).orElseThrow(() -> ApiException.notFound("Usuario no encontrado"));
        if (u.getId().equals(actorId) && !activo) throw ApiException.conflict("No puedes desactivar tu propia cuenta");
        u.setActivo(activo);
        return mapper.usuarioAdmin(u);
    }

    @Transactional
    public void restablecerClave(Long id, String nueva) {
        Usuario u = usuarios.findById(id).orElseThrow(() -> ApiException.notFound("Usuario no encontrado"));
        AuthService.validarClave(nueva);
        u.setPasswordHash(encoder.encode(nueva));
    }
}
