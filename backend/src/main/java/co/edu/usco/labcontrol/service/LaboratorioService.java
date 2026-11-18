package co.edu.usco.labcontrol.service;

import co.edu.usco.labcontrol.domain.*;
import co.edu.usco.labcontrol.repo.EquipoRepository;
import co.edu.usco.labcontrol.repo.LaboratorioDocumentoRepository;
import co.edu.usco.labcontrol.repo.LaboratorioRepository;
import co.edu.usco.labcontrol.repo.UsuarioRepository;
import co.edu.usco.labcontrol.web.ApiException;
import co.edu.usco.labcontrol.web.Dtos.*;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/**
 * Laboratorios de cada sede. El Administrador los crea, edita y desactiva; el Coordinador de Laboratorio actualiza su
 * caracterización y carga documentos y evidencias. Coordinadores y monitores solo ven sus laboratorios asignados.
 */
@Service
public class LaboratorioService {
    /** Laboratorios con los que arranca la sede principal si no tiene ninguno. */
    public static final List<String> LABORATORIOS = List.of("Electrónica", "Petróleos", "Civil", "Agrícola");

    private final LaboratorioRepository labs;
    private final EquipoRepository equipos;
    private final UsuarioRepository usuarios;
    private final LaboratorioDocumentoRepository documentos;
    private final ArchivoService archivos;
    private final ConfiguracionService configuracion;
    private final Acceso acceso;
    private final Mapper mapper;

    public LaboratorioService(LaboratorioRepository labs, EquipoRepository equipos, UsuarioRepository usuarios,
                              LaboratorioDocumentoRepository documentos, ArchivoService archivos, ConfiguracionService configuracion,
                              Acceso acceso, Mapper mapper) {
        this.labs = labs; this.equipos = equipos; this.usuarios = usuarios; this.documentos = documentos; this.archivos = archivos;
        this.configuracion = configuracion; this.acceso = acceso; this.mapper = mapper;
    }

    /** Laboratorios visibles para la cuenta, con su número de equipos e insumos y sus responsables. */
    @Transactional(readOnly = true)
    public List<LaboratorioDto> listar(Long actorId) {
        Usuario actor = acceso.usuario(actorId);
        var inventario = equipos.findBySedeIdAndActivoTrueOrderByLaboratorioAscPlacaAsc(acceso.sede(actor).getId());
        return acceso.laboratorios(actor).stream().map(l -> dto(l, inventario)).toList();
    }

    /** Nombres de los laboratorios activos visibles (filtros y formularios). */
    @Transactional(readOnly = true)
    public List<LaboratorioRefDto> referencias(Long actorId) {
        Usuario actor = acceso.usuario(actorId);
        return acceso.laboratorios(actor).stream().filter(Laboratorio::isActivo).map(l -> new LaboratorioRefDto(l.getId(), l.getNombre())).toList();
    }

    @Transactional
    public LaboratorioDto crear(Long actorId, LaboratorioRequest r) {
        Usuario actor = acceso.usuario(actorId);
        acceso.exigir(actor, Permiso.LABORATORIOS_GESTIONAR);
        Sede sede = acceso.sede(actor);
        String nombre = nombre(r.nombre());
        if (labs.findBySedeIdAndNombreIgnoreCase(sede.getId(), nombre).isPresent()) {
            throw ApiException.conflict("Ya existe el laboratorio «" + nombre + "» en la " + sede.getNombre());
        }
        Laboratorio l = labs.save(new Laboratorio(sede, nombre, Texto.opcional(r.ubicacion(), 160, "La ubicación"),
                Texto.opcional(r.descripcion(), 300, "La descripción"), mapper.ahora()));
        return dto(l, List.of());
    }

    /** Si cambia el nombre, los equipos e insumos de la sede que lo usaban pasan al nombre nuevo. */
    @Transactional
    public LaboratorioDto actualizar(Long actorId, Long id, LaboratorioRequest r) {
        Usuario actor = acceso.usuario(actorId);
        acceso.exigir(actor, Permiso.LABORATORIOS_GESTIONAR);
        Laboratorio l = acceso.laboratorio(actor, id);
        Sede sede = l.getSede();
        String nombre = nombre(r.nombre());
        labs.findBySedeIdAndNombreIgnoreCase(sede.getId(), nombre).filter(o -> !o.getId().equals(l.getId())).ifPresent(o -> {
            throw ApiException.conflict("Ya existe el laboratorio «" + nombre + "» en la " + sede.getNombre());
        });
        String anterior = l.getNombre();
        l.actualizar(nombre, Texto.opcional(r.ubicacion(), 160, "La ubicación"), Texto.opcional(r.descripcion(), 300, "La descripción"));
        if (!anterior.equals(nombre)) {
            equipos.findBySedeIdOrderByLaboratorioAscPlacaAsc(sede.getId()).stream()
                    .filter(e -> anterior.equalsIgnoreCase(e.getLaboratorio())).forEach(e -> e.cambiarLaboratorio(nombre));
        }
        return dto(l, equipos.findBySedeIdAndActivoTrueOrderByLaboratorioAscPlacaAsc(sede.getId()));
    }

    /** Un laboratorio con equipos o insumos activos no se puede desactivar (primero hay que moverlos). */
    @Transactional
    public LaboratorioDto activar(Long actorId, Long id, boolean activo) {
        Usuario actor = acceso.usuario(actorId);
        acceso.exigir(actor, Permiso.LABORATORIOS_GESTIONAR);
        Laboratorio l = acceso.laboratorio(actor, id);
        var inventario = equipos.findBySedeIdAndActivoTrueOrderByLaboratorioAscPlacaAsc(l.getSede().getId());
        if (!activo) {
            long enUso = inventario.stream().filter(e -> l.getNombre().equalsIgnoreCase(e.getLaboratorio())).count();
            if (enUso > 0) throw ApiException.conflict("«" + l.getNombre() + "» tiene " + enUso + " elementos activos. Muévelos a otro laboratorio antes de desactivarlo");
        }
        l.setActivo(activo);
        return dto(l, inventario);
    }

    // ------------------------------------------------------------------ Caracterización y documentación

    @Transactional(readOnly = true)
    public CaracterizacionDto detalle(Long actorId, Long id) {
        Usuario actor = acceso.usuario(actorId);
        acceso.exigir(actor, Permiso.CARACTERIZACION_CONSULTAR);
        Laboratorio l = acceso.laboratorio(actor, id);
        return caracterizacion(actor, l);
    }

    @Transactional
    public CaracterizacionDto caracterizar(Long actorId, Long id, CaracterizacionRequest r) {
        Usuario actor = acceso.usuario(actorId);
        acceso.exigir(actor, Permiso.CARACTERIZACION_EDITAR);
        Laboratorio l = acceso.laboratorio(actor, id);
        if (r.areaM2() != null && (r.areaM2().signum() <= 0 || r.areaM2().compareTo(new BigDecimal("100000")) > 0)) {
            throw ApiException.badRequest("El área debe ser mayor que cero");
        }
        if (r.capacidad() != null && (r.capacidad() <= 0 || r.capacidad() > 10000)) throw ApiException.badRequest("La capacidad debe ser mayor que cero");
        String correo = Texto.opcional(r.correo(), 120, "El correo");
        if (correo != null && !correo.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")) throw ApiException.badRequest("El correo del laboratorio no es válido");
        l.caracterizar(Texto.opcional(r.codigo(), 20, "El código"), r.areaM2(), r.capacidad(), Texto.opcional(r.horario(), 200, "El horario"),
                Texto.opcional(r.telefono(), 30, "El teléfono"), correo, Texto.opcional(r.programas(), 500, "Los programas"),
                Texto.opcional(r.servicios(), 1500, "Los servicios"), Texto.opcional(r.normasSeguridad(), 2000, "Las normas de seguridad"),
                Texto.opcional(r.ubicacion(), 160, "La ubicación"), Texto.opcional(r.descripcion(), 300, "La descripción"),
                actor.getNombre(), mapper.ahora());
        return caracterizacion(actor, l);
    }

    @Transactional
    public CaracterizacionDto agregarDocumento(Long actorId, Long id, String tipo, String titulo, String descripcion, MultipartFile f) {
        Usuario actor = acceso.usuario(actorId);
        acceso.exigir(actor, Permiso.CARACTERIZACION_EDITAR);
        Laboratorio l = acceso.laboratorio(actor, id);
        String t = configuracion.validar(Catalogo.Tipo.TIPO_DOCUMENTO, tipo, "el tipo de documento");
        String tit = Texto.obligatorio(titulo, 3, 160, "El título");
        ArchivoInfo a = archivos.guardar(actor, f);
        documentos.save(new LaboratorioDocumento(l, t, tit, Texto.opcional(descripcion, 500, "La descripción"), a, actor.getNombre(), mapper.ahora()));
        return caracterizacion(actor, l);
    }

    @Transactional
    public CaracterizacionDto quitarDocumento(Long actorId, Long id, Long documentoId) {
        Usuario actor = acceso.usuario(actorId);
        acceso.exigir(actor, Permiso.CARACTERIZACION_EDITAR);
        Laboratorio l = acceso.laboratorio(actor, id);
        LaboratorioDocumento d = documentos.findById(documentoId == null ? -1L : documentoId)
                .filter(x -> x.getLaboratorio().getId().equals(l.getId()))
                .orElseThrow(() -> ApiException.notFound("Documento no encontrado"));
        documentos.delete(d);
        return caracterizacion(actor, l);
    }

    /**
     * Valida el laboratorio de un equipo: debe ser un laboratorio activo de la sede (o el que ya tenía) y estar entre los
     * laboratorios de la cuenta. Devuelve el nombre tal como está registrado.
     */
    @Transactional(readOnly = true)
    public String validar(Usuario actor, String nombre, String actual) {
        String n = nombre == null ? "" : nombre.trim();
        if (actual != null && actual.equalsIgnoreCase(n)) return actual;
        Sede sede = acceso.sede(actor);
        var disponibles = acceso.laboratorios(actor).stream().filter(Laboratorio::isActivo).toList();
        return disponibles.stream().filter(l -> l.getNombre().equalsIgnoreCase(n)).map(Laboratorio::getNombre).findFirst()
                .orElseThrow(() -> ApiException.badRequest(disponibles.isEmpty()
                        ? "La " + sede.getNombre() + " no tiene laboratorios: el Administrador debe crearlos primero"
                        : "Seleccione un laboratorio válido: " + String.join(", ", disponibles.stream().map(Laboratorio::getNombre).toList())));
    }

    private CaracterizacionDto caracterizacion(Usuario actor, Laboratorio l) {
        var docs = documentos.findByLaboratorioIdOrderBySubidoEnDesc(l.getId()).stream()
                .map(d -> new DocumentoLabDto(d.getId(), d.getTipo(), d.getTitulo(), d.getDescripcion(), ArchivoService.dto(d.getArchivo()),
                        d.getSubidoPor(), d.getSubidoEn())).toList();
        return new CaracterizacionDto(dto(l, equipos.findBySedeIdAndActivoTrueOrderByLaboratorioAscPlacaAsc(l.getSede().getId())),
                l.getCodigo(), l.getAreaM2(), l.getCapacidad(), l.getHorario(), l.getTelefono(), l.getCorreo(), l.getProgramas(),
                l.getServicios(), l.getNormasSeguridad(), l.getActualizadoEn(), l.getActualizadoPor(), docs,
                acceso.puede(actor, Permiso.CARACTERIZACION_EDITAR));
    }

    private LaboratorioDto dto(Laboratorio l, List<Equipo> inventario) {
        long eq = inventario.stream().filter(e -> l.getNombre().equalsIgnoreCase(e.getLaboratorio()) && !e.esInsumo()).count();
        long in = inventario.stream().filter(e -> l.getNombre().equalsIgnoreCase(e.getLaboratorio()) && e.esInsumo()).count();
        List<String> coord = usuarios.asignados(l.getId(), Rol.COORDINADOR_LAB).stream().map(Usuario::getNombre).sorted().toList();
        List<String> mon = usuarios.asignados(l.getId(), Rol.MONITOR).stream().map(Usuario::getNombre).sorted().toList();
        return new LaboratorioDto(l.getId(), l.getNombre(), l.getUbicacion(), l.getDescripcion(), l.isActivo(), eq, in, coord, mon);
    }

    private static String nombre(String s) {
        String n = s == null ? "" : s.trim().replaceAll("\\s+", " ");
        if (n.length() < 2) throw ApiException.badRequest("Escribe el nombre del laboratorio");
        if (n.length() > 40) throw ApiException.badRequest("El nombre del laboratorio admite máximo 40 caracteres");
        if (n.contains("|") || n.contains(";")) throw ApiException.badRequest("El nombre no puede contener | ni ;");
        return n;
    }
}
