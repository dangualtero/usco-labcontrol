package co.edu.usco.labcontrol.service;

import co.edu.usco.labcontrol.domain.*;
import co.edu.usco.labcontrol.repo.*;
import co.edu.usco.labcontrol.web.ApiException;
import co.edu.usco.labcontrol.web.Dtos.*;
import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Alta, edición y cambio de estado de equipos e insumos (CRUD de inventario). La baja lógica está en CoordinadorService.
 * La placa define el tipo: USCO-EQ-000 es un equipo (se presta), USCO-IN-000 es un insumo (se consume por cantidades).
 */
@Service
public class InventarioService {
    private static final Set<String> ESTADOS_FISICOS = Set.of("Excelente", "Bueno", "Regular", "Deteriorado");
    /** Placas de inventario: equipos y insumos. */
    public static final Pattern FORMATO_INVENTARIO = Pattern.compile("^USCO-(EQ|IN)-\\d{3}$");
    public static final String MSG_FORMATO = "Usa el formato USCO-EQ-000 para equipos o USCO-IN-000 para insumos. Ejemplo: USCO-EQ-102";
    private static final BigDecimal MAXIMO = new BigDecimal("1000000000");

    private final EquipoRepository equipos;
    private final UsuarioRepository usuarios;
    private final HistorialRepository historial;
    private final Mapper mapper;
    private final SedeContexto sedes;
    private final LaboratorioService laboratorios;

    public InventarioService(EquipoRepository equipos, UsuarioRepository usuarios, HistorialRepository historial, Mapper mapper,
                             SedeContexto sedes, LaboratorioService laboratorios) {
        this.equipos = equipos; this.usuarios = usuarios; this.historial = historial; this.mapper = mapper; this.sedes = sedes;
        this.laboratorios = laboratorios;
    }

    @Transactional
    public EquipoAdminDto crear(Long actorId, EquipoRequest r) {
        Usuario actor = usuarios.findById(actorId).orElseThrow();
        String placa = r.placa() == null ? "" : r.placa().trim().toUpperCase();
        if (!FORMATO_INVENTARIO.matcher(placa).matches()) throw ApiException.badRequest(MSG_FORMATO);
        if (equipos.existsByPlaca(placa)) throw ApiException.conflict("Ya existe un elemento con la placa " + placa);
        TipoElemento tipo = TipoElemento.dePlaca(placa);
        EstadoEquipo estado = tipo == TipoElemento.EQUIPO && "MANTENIMIENTO".equalsIgnoreCase(r.estado()) ? EstadoEquipo.MANTENIMIENTO : EstadoEquipo.DISPONIBLE;
        Sede sede = sedes.de(actor);
        Datos d = validar(r, sede, null);
        Existencias x = existencias(tipo, r);
        Equipo e = new Equipo(placa, d.nombre, d.marca, d.modelo, d.serie, d.categoria, d.laboratorio, d.ubicacion,
                d.descripcion, d.specs, d.accesorios, d.estadoFisico, d.foto, estado);
        e.setSede(sede);
        e.definirCantidad(tipo, x.cantidad, x.unidad, x.referencia);
        equipos.save(e);
        String que = tipo == TipoElemento.INSUMO ? "Insumo registrado (" + Mapper.cantidad(e.getCantidad(), e.getUnidad()) + ")" : "Equipo registrado";
        historial.save(new HistorialEvento(mapper.ahora(), "ALTA", e, null, null, actor.getNombre(),
                que + " en el inventario de la " + sede.getNombre() + " (" + estado.name().toLowerCase() + ")"));
        return new EquipoAdminDto(mapper.equipo(e), true, null, null, null);
    }

    @Transactional
    public EquipoAdminDto actualizar(Long actorId, String placa, EquipoRequest r) {
        Usuario actor = usuarios.findById(actorId).orElseThrow();
        Sede sede = sedes.de(actor);
        Equipo e = equipos.findActivoForUpdate(placa).filter(x -> sedes.pertenece(x, sede))
                .orElseThrow(() -> ApiException.notFound("El equipo no existe en el inventario activo"));
        Datos d = validar(r, sede, e.getLaboratorio());
        BigDecimal antes = e.getCantidad();
        Unidad unidadAntes = e.getUnidad();
        Existencias x = r.cantidad() == null && r.unidad() == null && r.cantidadReferencia() == null
                ? new Existencias(e.getCantidad(), e.getUnidad(), e.getCantidadReferencia()) : existencias(e.getTipo(), r);
        e.actualizar(d.nombre, d.marca, d.modelo, d.serie, d.categoria, d.laboratorio, d.ubicacion, d.descripcion,
                d.specs, d.accesorios, d.estadoFisico, d.foto);
        e.definirCantidad(e.getTipo(), x.cantidad, x.unidad, x.referencia);
        String detalle = "Ficha " + (e.esInsumo() ? "del insumo" : "del equipo") + " actualizada";
        if (antes == null || antes.compareTo(e.getCantidad()) != 0 || unidadAntes != e.getUnidad()) {
            detalle += ". Existencias: " + Mapper.cantidad(antes, unidadAntes) + " → " + Mapper.cantidad(e.getCantidad(), e.getUnidad());
        }
        historial.save(new HistorialEvento(mapper.ahora(), "ESTADO", e, null, null, actor.getNombre(), detalle));
        return new EquipoAdminDto(mapper.equipo(e), true, null, null, null);
    }

    /** Envía el equipo a mantenimiento o lo vuelve a poner disponible. Un equipo prestado no puede cambiar de estado. */
    @Transactional
    public EquipoDto cambiarEstado(Long actorId, String placa, EstadoEquipoRequest r) {
        Usuario actor = usuarios.findById(actorId).orElseThrow();
        Equipo e = equipos.findActivoForUpdate(placa).filter(x -> sedes.pertenece(x, sedes.de(actor)))
                .orElseThrow(() -> ApiException.notFound("El equipo no existe en el inventario activo"));
        if (e.getEstado() == EstadoEquipo.PRESTADO) throw ApiException.conflict("No se puede cambiar el estado de un equipo que está prestado");
        if (e.esInsumo()) throw ApiException.conflict("Los insumos no se envían a mantenimiento: ajusta sus existencias o repórtalo como novedad");
        EstadoEquipo nuevo;
        if ("MANTENIMIENTO".equalsIgnoreCase(r.estado())) nuevo = EstadoEquipo.MANTENIMIENTO;
        else if ("DISPONIBLE".equalsIgnoreCase(r.estado())) nuevo = EstadoEquipo.DISPONIBLE;
        else throw ApiException.badRequest("Estado no válido: use DISPONIBLE o MANTENIMIENTO");
        if (nuevo == e.getEstado()) throw ApiException.conflict("El equipo ya se encuentra en ese estado");
        if (nuevo == EstadoEquipo.MANTENIMIENTO && (r.motivo() == null || r.motivo().trim().length() < 5)) {
            throw ApiException.badRequest("Indique el motivo del mantenimiento");
        }
        e.setEstado(nuevo);
        historial.save(new HistorialEvento(mapper.ahora(), "ESTADO", e, null, null, actor.getNombre(),
                nuevo == EstadoEquipo.MANTENIMIENTO ? "Enviado a mantenimiento: " + r.motivo().trim() : "Mantenimiento finalizado: equipo disponible"));
        return mapper.equipo(e);
    }

    private record Datos(String nombre, String marca, String modelo, String serie, String categoria, String laboratorio,
                         String ubicacion, String descripcion, String specs, String accesorios, String estadoFisico, String foto) {}

    private record Existencias(BigDecimal cantidad, Unidad unidad, BigDecimal referencia) {}

    /** Equipos: piezas enteras (1, 2, 3…). Insumos: cualquier unidad (L, mL, kg, g, mg, m, cm o unidades). */
    private static Existencias existencias(TipoElemento tipo, EquipoRequest r) {
        Unidad u = tipo == TipoElemento.EQUIPO ? Unidad.UNIDAD : Unidad.de(r.unidad());
        if (u == null) throw ApiException.badRequest("Selecciona la unidad de medida del insumo (unidades, mL, L, mg, g, kg, cm o m)");
        if (tipo == TipoElemento.EQUIPO && r.unidad() != null && !r.unidad().isBlank() && Unidad.de(r.unidad()) != Unidad.UNIDAD) {
            throw ApiException.badRequest("Los equipos se cuentan por unidades");
        }
        BigDecimal c = r.cantidad() == null ? (tipo == TipoElemento.EQUIPO ? BigDecimal.ONE : null) : r.cantidad();
        if (c == null) throw ApiException.badRequest("Indica la cantidad disponible del insumo");
        validarCantidad(c, u, tipo == TipoElemento.EQUIPO ? 1 : 0, "La cantidad");
        BigDecimal ref = r.cantidadReferencia();
        if (tipo == TipoElemento.EQUIPO) ref = null;
        else if (ref != null) validarCantidad(ref, u, 0, "La cantidad de referencia (100 %)");
        if (ref != null && ref.signum() == 0) ref = null;
        if (tipo == TipoElemento.INSUMO && ref == null && c.signum() == 0) {
            throw ApiException.badRequest("Indica la cantidad de referencia (100 %) del insumo para poder calcular las alertas");
        }
        return new Existencias(c, u, ref);
    }

    static void validarCantidad(BigDecimal c, Unidad u, int minimo, String campo) {
        if (c.compareTo(BigDecimal.valueOf(minimo)) < 0) throw ApiException.badRequest(campo + " debe ser " + (minimo == 0 ? "cero o mayor" : "mínimo " + minimo));
        if (c.compareTo(MAXIMO) > 0) throw ApiException.badRequest(campo + " es demasiado grande");
        if (c.stripTrailingZeros().scale() > 3) throw ApiException.badRequest(campo + " admite máximo 3 decimales");
        if (u == Unidad.UNIDAD && c.stripTrailingZeros().scale() > 0) throw ApiException.badRequest(campo + " en unidades debe ser un número entero (1, 2, 3…)");
    }

    private Datos validar(EquipoRequest r, Sede sede, String laboratorioActual) {
        String nombre = limpio(r.nombre());
        if (nombre == null) throw ApiException.badRequest("El nombre del equipo es obligatorio");
        String categoria = limpio(r.categoria());
        if (categoria == null) throw ApiException.badRequest("La categoría es obligatoria");
        String lab = laboratorios.validar(sede, limpio(r.laboratorio()), laboratorioActual);
        String fisico = limpio(r.estadoFisico()) == null ? "Bueno" : limpio(r.estadoFisico());
        if (!ESTADOS_FISICOS.contains(fisico)) throw ApiException.badRequest("Estado físico no válido");
        String foto = limpio(r.fotoUrl());
        if (foto != null && !(foto.startsWith("/images/") || foto.startsWith("https://") || foto.startsWith("http://")
                || ImagenService.esRutaPropia(foto))) {
            throw ApiException.badRequest("La fotografía debe ser una imagen subida, una URL http(s) o una ruta /images/…");
        }
        if (foto != null && foto.length() > 400) throw ApiException.badRequest("La URL de la fotografía es demasiado larga");
        return new Datos(nombre, limpio(r.marca()), limpio(r.modelo()), limpio(r.numeroSerie()), categoria, lab,
                limpio(r.ubicacion()), limpio(r.descripcion()), pipe(r.specs()), pipe(r.accesorios()), fisico, foto);
    }

    private static String limpio(String s) { return s == null || s.isBlank() ? null : s.trim(); }

    private static String pipe(List<String> l) {
        if (l == null) return null;
        String j = l.stream().map(String::trim).filter(x -> !x.isEmpty()).map(x -> x.replace("|", "/")).reduce((a, b) -> a + "|" + b).orElse(null);
        return j;
    }
}
