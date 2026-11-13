package co.edu.usco.labcontrol.service;

import co.edu.usco.labcontrol.domain.*;
import co.edu.usco.labcontrol.repo.EquipoRepository;
import co.edu.usco.labcontrol.repo.LaboratorioRepository;
import co.edu.usco.labcontrol.web.ApiException;
import co.edu.usco.labcontrol.web.Dtos.*;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Laboratorios de cada sede. El Coordinador los crea, edita y desactiva; los equipos e insumos se asignan a un
 * laboratorio activo de su misma sede.
 */
@Service
public class LaboratorioService {
    private final LaboratorioRepository labs;
    private final EquipoRepository equipos;
    private final SedeContexto sedes;
    private final Mapper mapper;

    public LaboratorioService(LaboratorioRepository labs, EquipoRepository equipos, SedeContexto sedes, Mapper mapper) {
        this.labs = labs; this.equipos = equipos; this.sedes = sedes; this.mapper = mapper;
    }

    /** Administración: todos los laboratorios de la sede (activos e inactivos) con su número de equipos e insumos. */
    @Transactional(readOnly = true)
    public List<LaboratorioDto> listar(Long actorId) {
        Sede sede = sedes.de(actorId);
        var inventario = equipos.findBySedeIdAndActivoTrueOrderByLaboratorioAscPlacaAsc(sede.getId());
        return labs.findBySedeIdOrderByNombreAsc(sede.getId()).stream().map(l -> {
            long eq = inventario.stream().filter(e -> l.getNombre().equalsIgnoreCase(e.getLaboratorio()) && !e.esInsumo()).count();
            long in = inventario.stream().filter(e -> l.getNombre().equalsIgnoreCase(e.getLaboratorio()) && e.esInsumo()).count();
            return new LaboratorioDto(l.getId(), l.getNombre(), l.getUbicacion(), l.getDescripcion(), l.isActivo(), eq, in);
        }).toList();
    }

    /** Nombres de los laboratorios activos de la sede (filtros del catálogo y formulario de equipos). */
    @Transactional(readOnly = true)
    public List<String> nombres(Long actorId) {
        return labs.findBySedeIdAndActivoTrueOrderByNombreAsc(sedes.de(actorId).getId()).stream().map(Laboratorio::getNombre).toList();
    }

    @Transactional
    public LaboratorioDto crear(Long actorId, LaboratorioRequest r) {
        Sede sede = sedes.de(actorId);
        String nombre = nombre(r.nombre());
        if (labs.findBySedeIdAndNombreIgnoreCase(sede.getId(), nombre).isPresent()) {
            throw ApiException.conflict("Ya existe el laboratorio «" + nombre + "» en la " + sede.getNombre());
        }
        Laboratorio l = labs.save(new Laboratorio(sede, nombre, corto(r.ubicacion(), 160), corto(r.descripcion(), 300), mapper.ahora()));
        return new LaboratorioDto(l.getId(), l.getNombre(), l.getUbicacion(), l.getDescripcion(), true, 0, 0);
    }

    /** Si cambia el nombre, los equipos e insumos de la sede que lo usaban pasan al nombre nuevo. */
    @Transactional
    public LaboratorioDto actualizar(Long actorId, Long id, LaboratorioRequest r) {
        Sede sede = sedes.de(actorId);
        Laboratorio l = buscar(sede, id);
        String nombre = nombre(r.nombre());
        labs.findBySedeIdAndNombreIgnoreCase(sede.getId(), nombre).filter(o -> !o.getId().equals(l.getId())).ifPresent(o -> {
            throw ApiException.conflict("Ya existe el laboratorio «" + nombre + "» en la " + sede.getNombre());
        });
        String anterior = l.getNombre();
        l.actualizar(nombre, corto(r.ubicacion(), 160), corto(r.descripcion(), 300));
        if (!anterior.equals(nombre)) {
            equipos.findBySedeIdOrderByLaboratorioAscPlacaAsc(sede.getId()).stream()
                    .filter(e -> anterior.equalsIgnoreCase(e.getLaboratorio()))
                    .forEach(e -> e.cambiarLaboratorio(nombre));
        }
        return listar(actorId).stream().filter(x -> x.id().equals(l.getId())).findFirst().orElseThrow();
    }

    /** Un laboratorio con equipos o insumos activos no se puede desactivar (primero hay que moverlos). */
    @Transactional
    public LaboratorioDto activar(Long actorId, Long id, boolean activo) {
        Sede sede = sedes.de(actorId);
        Laboratorio l = buscar(sede, id);
        if (!activo) {
            long enUso = equipos.findBySedeIdAndActivoTrueOrderByLaboratorioAscPlacaAsc(sede.getId()).stream()
                    .filter(e -> l.getNombre().equalsIgnoreCase(e.getLaboratorio())).count();
            if (enUso > 0) throw ApiException.conflict("«" + l.getNombre() + "» tiene " + enUso + " elementos activos. Muévelos a otro laboratorio antes de desactivarlo");
        }
        l.setActivo(activo);
        return listar(actorId).stream().filter(x -> x.id().equals(l.getId())).findFirst().orElseThrow();
    }

    /**
     * Valida el laboratorio de un equipo: debe ser un laboratorio activo de su sede (o el que ya tenía).
     * Devuelve el nombre tal como está registrado.
     */
    @Transactional(readOnly = true)
    public String validar(Sede sede, String nombre, String actual) {
        String n = nombre == null ? "" : nombre.trim();
        if (actual != null && actual.equalsIgnoreCase(n)) return actual;
        return labs.findBySedeIdAndNombreIgnoreCase(sede.getId(), n).filter(Laboratorio::isActivo).map(Laboratorio::getNombre)
                .orElseThrow(() -> {
                    var disponibles = labs.findBySedeIdAndActivoTrueOrderByNombreAsc(sede.getId()).stream().map(Laboratorio::getNombre).toList();
                    return ApiException.badRequest(disponibles.isEmpty()
                            ? "La " + sede.getNombre() + " no tiene laboratorios: créalos primero en «Laboratorios»"
                            : "Seleccione un laboratorio válido de la " + sede.getNombre() + ": " + String.join(", ", disponibles));
                });
    }

    private Laboratorio buscar(Sede sede, Long id) {
        return labs.findById(id == null ? -1L : id).filter(l -> l.getSede().es(sede))
                .orElseThrow(() -> ApiException.notFound("El laboratorio no existe en la " + sede.getNombre()));
    }

    private static String nombre(String s) {
        String n = s == null ? "" : s.trim().replaceAll("\\s+", " ");
        if (n.length() < 2) throw ApiException.badRequest("Escribe el nombre del laboratorio");
        if (n.length() > 40) throw ApiException.badRequest("El nombre del laboratorio admite máximo 40 caracteres");
        if (n.contains("|") || n.contains(";")) throw ApiException.badRequest("El nombre no puede contener | ni ;");
        return n;
    }

    private static String corto(String s, int max) {
        if (s == null || s.isBlank()) return null;
        String t = s.trim();
        if (t.length() > max) throw ApiException.badRequest("El texto admite máximo " + max + " caracteres");
        return t;
    }
}
