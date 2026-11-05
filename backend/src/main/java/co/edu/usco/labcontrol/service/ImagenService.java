package co.edu.usco.labcontrol.service;

import co.edu.usco.labcontrol.domain.Imagen;
import co.edu.usco.labcontrol.repo.ImagenRepository;
import co.edu.usco.labcontrol.repo.UsuarioRepository;
import co.edu.usco.labcontrol.web.ApiException;
import co.edu.usco.labcontrol.web.Dtos.ImagenSubidaDto;
import java.io.IOException;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/**
 * Fotografías de los equipos subidas desde un computador o un celular. Solo se aceptan JPG, PNG y WEBP de hasta 5 MB,
 * y el tipo se verifica leyendo los primeros bytes del archivo (no basta con cambiarle la extensión a otro archivo).
 */
@Service
public class ImagenService {
    public static final String PREFIJO = "/api/imagenes/";
    public static final long MAXIMO = 5L * 1024 * 1024;
    private static final Pattern ID = Pattern.compile("^[a-f0-9]{32}$");

    private final ImagenRepository imagenes;
    private final UsuarioRepository usuarios;
    private final Mapper mapper;

    public ImagenService(ImagenRepository imagenes, UsuarioRepository usuarios, Mapper mapper) {
        this.imagenes = imagenes; this.usuarios = usuarios; this.mapper = mapper;
    }

    /** ¿La ruta apunta a una imagen subida a este sistema? (se acepta como fotoUrl de un equipo). */
    public static boolean esRutaPropia(String ruta) {
        return ruta != null && ruta.startsWith(PREFIJO) && ID.matcher(ruta.substring(PREFIJO.length())).matches();
    }

    @Transactional
    public ImagenSubidaDto subir(Long actorId, MultipartFile archivo) {
        if (archivo == null || archivo.isEmpty()) throw ApiException.badRequest("Selecciona una fotografía");
        if (archivo.getSize() > MAXIMO) throw ApiException.badRequest("La fotografía supera los 5 MB permitidos");
        byte[] datos;
        try { datos = archivo.getBytes(); }
        catch (IOException e) { throw ApiException.badRequest("No se pudo leer el archivo"); }
        String tipo = tipoReal(datos)
                .orElseThrow(() -> ApiException.badRequest("Formato no permitido: sube una fotografía JPG, PNG o WEBP"));
        String nombre = archivo.getOriginalFilename();
        if (nombre != null && nombre.length() > 160) nombre = nombre.substring(0, 160);
        String quien = usuarios.findById(actorId).map(u -> u.getNombre()).orElse("—");
        String id = UUID.randomUUID().toString().replace("-", "");
        imagenes.save(new Imagen(id, tipo, datos, nombre, quien, mapper.ahora()));
        return new ImagenSubidaDto(id, PREFIJO + id, datos.length, tipo);
    }

    @Transactional(readOnly = true)
    public Optional<Imagen> buscar(String id) {
        if (id == null || !ID.matcher(id).matches()) return Optional.empty();
        return imagenes.findById(id).map(i -> { i.getDatos(); return i; });   // carga los bytes dentro de la transacción
    }

    /** Identifica el formato por su «firma» (magic bytes). */
    static Optional<String> tipoReal(byte[] b) {
        if (b.length >= 3 && (b[0] & 0xFF) == 0xFF && (b[1] & 0xFF) == 0xD8 && (b[2] & 0xFF) == 0xFF) return Optional.of("image/jpeg");
        if (b.length >= 8 && (b[0] & 0xFF) == 0x89 && b[1] == 'P' && b[2] == 'N' && b[3] == 'G') return Optional.of("image/png");
        if (b.length >= 12 && b[0] == 'R' && b[1] == 'I' && b[2] == 'F' && b[3] == 'F'
                && b[8] == 'W' && b[9] == 'E' && b[10] == 'B' && b[11] == 'P') return Optional.of("image/webp");
        return Optional.empty();
    }
}
