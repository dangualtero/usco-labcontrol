package co.edu.usco.labcontrol.service;

import co.edu.usco.labcontrol.domain.Archivo;
import co.edu.usco.labcontrol.domain.ArchivoInfo;
import co.edu.usco.labcontrol.domain.Usuario;
import co.edu.usco.labcontrol.repo.ArchivoInfoRepository;
import co.edu.usco.labcontrol.repo.ArchivoRepository;
import co.edu.usco.labcontrol.web.ApiException;
import co.edu.usco.labcontrol.web.Dtos.ArchivoDto;
import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/**
 * Archivos privados (cotizaciones, fotografías, documentos y evidencias): PDF, JPG, PNG, WEBP, Word (.docx) o Excel (.xlsx)
 * de hasta 10 MB. El tipo se comprueba por el contenido real del archivo, no por la extensión. Solo se descargan con sesión.
 */
@Service
public class ArchivoService {
    public static final long MAXIMO = 10L * 1024 * 1024;
    private static final Pattern ID = Pattern.compile("^[0-9a-f]{32}$");
    private static final String DOCX = "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
    private static final String XLSX = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

    private final ArchivoRepository archivos;
    private final ArchivoInfoRepository infos;
    private final Mapper mapper;
    private final Acceso acceso;
    private final JdbcTemplate jdbc;

    public ArchivoService(ArchivoRepository archivos, ArchivoInfoRepository infos, Mapper mapper, Acceso acceso, JdbcTemplate jdbc) {
        this.archivos = archivos; this.infos = infos; this.mapper = mapper; this.acceso = acceso; this.jdbc = jdbc;
    }

    /**
     * Descarga con control de acceso: el archivo debe pertenecer a un documento, novedad, solicitud o avance de un
     * laboratorio visible para la cuenta. Los documentos contractuales exigen permiso de presupuesto.
     */
    @Transactional(readOnly = true)
    public Archivo descargar(Long actorId, String id) {
        Usuario actor = acceso.usuario(actorId);
        if (id == null || !ID.matcher(id).matches()) throw ApiException.notFound("Archivo no encontrado");
        List<Long> labs = jdbc.queryForList("""
                SELECT d.laboratorio_id FROM laboratorio_documentos d WHERE d.archivo_id = ?
                UNION SELECT s.laboratorio_id FROM solicitud_archivos sa JOIN solicitudes s ON s.id = sa.solicitud_id
                      WHERE sa.archivo_id = ? AND (sa.tipo <> 'CONTRATO' OR ?)
                UNION SELECT a.laboratorio_id FROM avance_archivos aa JOIN avances a ON a.id = aa.avance_id WHERE aa.archivo_id = ?
                UNION SELECT l.id FROM novedad_archivos na JOIN reportes_novedad r ON r.id = na.novedad_id
                      JOIN equipos e ON e.id = r.equipo_id
                      JOIN laboratorios l ON l.sede_id = e.sede_id AND LOWER(l.nombre) = LOWER(e.laboratorio)
                      WHERE na.archivo_id = ?
                """, Long.class, id, id, acceso.puede(actor, co.edu.usco.labcontrol.domain.Permiso.PRESUPUESTO_CONSULTAR), id, id);
        boolean puede = labs.stream().anyMatch(labId -> {
            try { acceso.laboratorio(actor, labId); return true; } catch (ApiException e) { return false; }
        });
        if (!puede) throw ApiException.notFound("Archivo no encontrado");
        return descargar(id);
    }

    @Transactional
    public ArchivoInfo guardar(Usuario actor, MultipartFile f) {
        if (f == null || f.isEmpty()) throw ApiException.badRequest("Selecciona un archivo");
        if (f.getSize() > MAXIMO) throw ApiException.badRequest("El archivo supera los 10 MB permitidos");
        byte[] datos;
        try { datos = f.getBytes(); } catch (IOException e) { throw ApiException.badRequest("No se pudo leer el archivo"); }
        String nombre = f.getOriginalFilename() == null || f.getOriginalFilename().isBlank() ? "archivo" : f.getOriginalFilename().trim();
        nombre = nombre.replaceAll("[\\\\/:*?\"<>|\\r\\n]+", "_");
        if (nombre.length() > 180) nombre = nombre.substring(nombre.length() - 180);
        String tipo = tipo(datos, nombre);
        String id = UUID.randomUUID().toString().replace("-", "");
        archivos.saveAndFlush(new Archivo(id, nombre, tipo, datos, actor.getNombre(), mapper.ahora()));
        return infos.findById(id).orElseThrow();
    }

    @Transactional(readOnly = true)
    public Archivo descargar(String id) {
        if (id == null || !ID.matcher(id).matches()) throw ApiException.notFound("Archivo no encontrado");
        Archivo a = archivos.findById(id).orElseThrow(() -> ApiException.notFound("Archivo no encontrado"));
        a.getDatos();   // carga el contenido dentro de la transacción
        return a;
    }

    public static ArchivoDto dto(ArchivoInfo a) {
        return a == null ? null : new ArchivoDto(a.getId(), a.getNombre(), a.getTipoContenido(), a.getTamano(), a.getSubidoPor(), a.getSubidoEn());
    }

    /** Tipo real por la firma del archivo (magic bytes). */
    static String tipo(byte[] b, String nombre) {
        var imagen = ImagenService.tipoReal(b);
        if (imagen.isPresent()) return imagen.get();
        if (b.length >= 5 && b[0] == '%' && b[1] == 'P' && b[2] == 'D' && b[3] == 'F' && b[4] == '-') return "application/pdf";
        boolean zip = b.length >= 4 && b[0] == 'P' && b[1] == 'K' && b[2] == 3 && b[3] == 4;
        String ext = nombre.toLowerCase(Locale.ROOT);
        if (zip && ext.endsWith(".docx")) return DOCX;
        if (zip && ext.endsWith(".xlsx")) return XLSX;
        throw ApiException.badRequest("Formato no permitido: sube PDF, JPG, PNG, WEBP, Word (.docx) o Excel (.xlsx)");
    }
}
