package co.edu.usco.labcontrol.service;

import co.edu.usco.labcontrol.domain.EstadoPrestamo;
import co.edu.usco.labcontrol.domain.Prestamo;
import co.edu.usco.labcontrol.repo.EquipoRepository;
import co.edu.usco.labcontrol.repo.PrestamoRepository;
import co.edu.usco.labcontrol.web.ApiException;
import co.edu.usco.labcontrol.web.Dtos.*;
import java.text.Normalizer;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CatalogoService {
    private final EquipoRepository equipos;
    private final PrestamoRepository prestamos;
    private final Mapper mapper;

    public CatalogoService(EquipoRepository equipos, PrestamoRepository prestamos, Mapper mapper) {
        this.equipos = equipos; this.prestamos = prestamos; this.mapper = mapper;
    }

    @Transactional(readOnly = true)
    public List<EquipoDto> listar(String laboratorio, String categoria, String q) {
        String nq = normalizar(q);
        return equipos.findByActivoTrueOrderByLaboratorioAscPlacaAsc().stream()
                .filter(e -> vacio(laboratorio) || e.getLaboratorio().equalsIgnoreCase(laboratorio))
                .filter(e -> vacio(categoria) || e.getCategoria().equalsIgnoreCase(categoria))
                .filter(e -> nq.isEmpty() || normalizar(e.getPlaca() + " " + e.getNombre() + " " + e.getMarca() + " "
                        + e.getModelo() + " " + e.getCategoria()).contains(nq))
                .map(mapper::equipo).toList();
    }

    /** Ficha técnica + historial PÚBLICO anonimizado: solo ID anónimo y fechas, sin datos del usuario (PS-05). */
    @Transactional(readOnly = true)
    public EquipoDetalleDto detalle(String placa) {
        var e = equipos.findByPlacaAndActivoTrue(placa)
                .orElseThrow(() -> ApiException.notFound("El equipo no existe en el inventario activo"));
        List<Prestamo> usos = prestamos.findByEquipoPlacaAndEstadoInOrderByFechaSolicitudDesc(placa,
                Set.of(EstadoPrestamo.ACTIVO, EstadoPrestamo.DEVUELTO));
        var publico = usos.stream().limit(5)
                .map(p -> new UsoPublicoDto("PR-" + Integer.toHexString(p.getCodigo().hashCode() & 0xFFFFF).toUpperCase(),
                        p.getFechaSalida(), p.getFechaEntrada()))
                .toList();
        return new EquipoDetalleDto(mapper.equipo(e), publico);
    }

    private static boolean vacio(String s) { return s == null || s.isBlank() || s.equalsIgnoreCase("todos"); }

    static String normalizar(String s) {
        if (s == null) return "";
        return Normalizer.normalize(s, Normalizer.Form.NFD).replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT).trim();
    }
}
