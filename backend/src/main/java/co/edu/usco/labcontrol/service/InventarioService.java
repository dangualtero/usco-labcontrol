package co.edu.usco.labcontrol.service;

import co.edu.usco.labcontrol.domain.*;
import co.edu.usco.labcontrol.repo.*;
import co.edu.usco.labcontrol.web.ApiException;
import co.edu.usco.labcontrol.web.Dtos.*;
import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Inventario de los laboratorios: equipos (bienes con hoja de vida) e insumos (se consumen por cantidad).
 * <ul>
 *   <li>Consultar (INVENTARIO_CONSULTAR): todos los roles, cada uno en sus laboratorios.</li>
 *   <li>Registrar, editar la ficha completa y dar de baja (INVENTARIO_GESTIONAR): Administrador y Coordinador.</li>
 *   <li>Actualizar la información operativa (INVENTARIO_OPERATIVO): ubicación, estado físico y observaciones.</li>
 * </ul>
 * La placa define el tipo: USCO-EQ-000 es un equipo, USCO-IN-000 es un insumo.
 */
@Service
public class InventarioService {
    /** Placas de inventario: equipos y insumos. */
    public static final Pattern FORMATO_INVENTARIO = Pattern.compile("^USCO-(EQ|IN)-\\d{3}$");
    public static final String MSG_FORMATO = "Usa el formato USCO-EQ-000 para equipos o USCO-IN-000 para insumos. Ejemplo: USCO-EQ-102";
    private static final BigDecimal MAXIMO = new BigDecimal("1000000000");

    private final EquipoRepository equipos;
    private final HistorialRepository historial;
    private final MantenimientoRepository mantenimientos;
    private final ReporteNovedadRepository novedades;
    private final LaboratorioService laboratorios;
    private final ConfiguracionService configuracion;
    private final Acceso acceso;
    private final Mapper mapper;

    public InventarioService(EquipoRepository equipos, HistorialRepository historial, MantenimientoRepository mantenimientos,
                             ReporteNovedadRepository novedades, LaboratorioService laboratorios, ConfiguracionService configuracion,
                             Acceso acceso, Mapper mapper) {
        this.equipos = equipos; this.historial = historial; this.mantenimientos = mantenimientos; this.novedades = novedades;
        this.laboratorios = laboratorios; this.configuracion = configuracion; this.acceso = acceso; this.mapper = mapper;
    }

    // ------------------------------------------------------------------ Consulta

    /**
     * Inventario de los laboratorios de la cuenta. tipo: EQUIPO, INSUMO o vacío. bajas = incluir los dados de baja
     * (solo con INVENTARIO_GESTIONAR).
     */
    @Transactional(readOnly = true)
    public List<EquipoAdminDto> listar(Long actorId, String tipo, String laboratorio, String q, boolean bajas) {
        Usuario actor = acceso.usuario(actorId);
        acceso.exigir(actor, Permiso.INVENTARIO_CONSULTAR);
        boolean conBajas = bajas && acceso.puede(actor, Permiso.INVENTARIO_GESTIONAR);
        String nq = Texto.normalizar(q);
        var nombres = acceso.nombres(actor);
        return equipos.findBySedeIdOrderByLaboratorioAscPlacaAsc(acceso.sede(actor).getId()).stream()
                .filter(e -> conBajas || e.isActivo())
                .filter(e -> actor.esAdministrador() || nombres.contains(e.getLaboratorio().toLowerCase(Locale.ROOT)))
                .filter(e -> Texto.vacio(tipo) || e.getTipo().name().equalsIgnoreCase(tipo.trim()))
                .filter(e -> Texto.vacio(laboratorio) || e.getLaboratorio().equalsIgnoreCase(laboratorio.trim()))
                .filter(e -> nq.isEmpty() || Texto.normalizar(e.getPlaca() + " " + e.getNombre() + " " + nz(e.getMarca()) + " "
                        + nz(e.getModelo()) + " " + e.getCategoria() + " " + nz(e.getNumeroSerie())).contains(nq))
                .map(mapper::equipoAdmin).toList();
    }

    /** Hoja de vida: ficha, mantenimientos, novedades y todos los eventos del historial del elemento. */
    @Transactional(readOnly = true)
    public HojaVidaDto hojaVida(Long actorId, String placa) {
        Usuario actor = acceso.usuario(actorId);
        acceso.exigir(actor, Permiso.INVENTARIO_CONSULTAR);
        Equipo e = equipos.findByPlaca(placa == null ? "" : placa.trim().toUpperCase()).orElse(null);
        if (e == null || !acceso.ve(actor, e)) throw ApiException.notFound("El elemento no existe en el inventario de tus laboratorios");
        return new HojaVidaDto(mapper.equipoAdmin(e),
                mantenimientos.findByEquipoIdOrderByCreadoEnDesc(e.getId()).stream().map(mapper::mantenimiento).toList(),
                novedades.findByEquipoIdOrderByCreadoEnDesc(e.getId()).stream().map(mapper::novedad).toList(),
                historial.dePlaca(e.getPlaca()).stream().limit(300).map(mapper::historial).toList());
    }

    // ------------------------------------------------------------------ Gestión (ficha completa)

    @Transactional
    public EquipoAdminDto crear(Long actorId, EquipoRequest r) {
        Usuario actor = acceso.usuario(actorId);
        acceso.exigir(actor, Permiso.INVENTARIO_GESTIONAR);
        String placa = r.placa() == null ? "" : r.placa().trim().toUpperCase();
        if (!FORMATO_INVENTARIO.matcher(placa).matches()) throw ApiException.badRequest(MSG_FORMATO);
        if (equipos.existsByPlaca(placa)) throw ApiException.conflict("Ya existe un elemento con la placa " + placa);
        TipoElemento tipo = TipoElemento.dePlaca(placa);
        EstadoEquipo estado = tipo == TipoElemento.EQUIPO && "MANTENIMIENTO".equalsIgnoreCase(r.estado()) ? EstadoEquipo.MANTENIMIENTO : EstadoEquipo.DISPONIBLE;
        Sede sede = acceso.sede(actor);
        Datos d = validar(actor, r, null, null, null);
        Existencias x = existencias(tipo, r);
        Equipo e = new Equipo(placa, d.nombre, d.marca, d.modelo, d.serie, d.categoria, d.laboratorio, d.ubicacion,
                d.descripcion, d.specs, d.accesorios, d.estadoFisico, d.foto, estado);
        e.setSede(sede);
        e.definirCantidad(tipo, x.cantidad, x.unidad, x.referencia);
        equipos.save(e);
        String que = tipo == TipoElemento.INSUMO ? "Insumo registrado (" + Mapper.cantidad(e.getCantidad(), e.getUnidad()) + ")" : "Equipo registrado";
        historial.save(new HistorialEvento(mapper.ahora(), "ALTA", e, null, null, actor.getNombre(),
                que + " en el laboratorio " + e.getLaboratorio() + " (" + estado.texto().toLowerCase(Locale.ROOT) + ")"));
        return mapper.equipoAdmin(e);
    }

    @Transactional
    public EquipoAdminDto actualizar(Long actorId, String placa, EquipoRequest r) {
        Usuario actor = acceso.usuario(actorId);
        acceso.exigir(actor, Permiso.INVENTARIO_GESTIONAR);
        Equipo e = acceso.equipoParaEditar(actor, placa);
        Datos d = validar(actor, r, e.getLaboratorio(), e.getCategoria(), e.getEstadoFisico());
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
        return mapper.equipoAdmin(e);
    }

    /** Información operativa (coordinador y monitor): ubicación, estado físico y observaciones. Queda en la hoja de vida. */
    @Transactional
    public EquipoAdminDto operativo(Long actorId, String placa, OperativoRequest r) {
        Usuario actor = acceso.usuario(actorId);
        acceso.exigir(actor, Permiso.INVENTARIO_OPERATIVO);
        Equipo e = acceso.equipoParaEditar(actor, placa);
        String fisico = Texto.limpio(r.estadoFisico()) == null ? e.getEstadoFisico()
                : (r.estadoFisico().trim().equals(e.getEstadoFisico()) ? e.getEstadoFisico()
                   : configuracion.validar(Catalogo.Tipo.ESTADO_FISICO, r.estadoFisico(), "el estado físico"));
        String ubicacion = Texto.opcional(r.ubicacion(), 160, "La ubicación");
        String obs = Texto.opcional(r.observaciones(), 1000, "Las observaciones");
        StringBuilder cambios = new StringBuilder();
        if (!eq(ubicacion, e.getUbicacion())) cambios.append("Ubicación: ").append(nz(ubicacion)).append(". ");
        if (!eq(fisico, e.getEstadoFisico())) cambios.append("Estado físico: ").append(nz(e.getEstadoFisico())).append(" → ").append(nz(fisico)).append(". ");
        if (!eq(obs, e.getObservaciones())) cambios.append("Observaciones: ").append(nz(obs));
        if (cambios.isEmpty()) throw ApiException.badRequest("No hay cambios para guardar");
        e.operativo(ubicacion, fisico, obs);
        String det = cambios.toString().trim();
        historial.save(new HistorialEvento(mapper.ahora(), "OPERATIVO", e, null, null, actor.getNombre(),
                det.length() > 600 ? det.substring(0, 599) + "…" : det));
        return mapper.equipoAdmin(e);
    }

    /** Estado del equipo: DISPONIBLE (operativo), MANTENIMIENTO o FUERA_SERVICIO. */
    @Transactional
    public EquipoAdminDto cambiarEstado(Long actorId, String placa, EstadoEquipoRequest r) {
        Usuario actor = acceso.usuario(actorId);
        acceso.exigirAlguno(actor, Permiso.INVENTARIO_GESTIONAR, Permiso.MANTENIMIENTO_GESTIONAR);
        Equipo e = acceso.equipoParaEditar(actor, placa);
        if (e.esInsumo()) throw ApiException.conflict("Los insumos no cambian de estado: ajusta sus existencias o reporta una novedad");
        EstadoEquipo nuevo;
        try { nuevo = EstadoEquipo.valueOf(r.estado() == null ? "" : r.estado().trim().toUpperCase()); }
        catch (IllegalArgumentException ex) { throw ApiException.badRequest("Estado no válido: DISPONIBLE, MANTENIMIENTO o FUERA_SERVICIO"); }
        if (nuevo == e.getEstado()) throw ApiException.conflict("El equipo ya se encuentra en ese estado");
        String motivo = Texto.limpio(r.motivo());
        if (nuevo != EstadoEquipo.DISPONIBLE && (motivo == null || motivo.length() < 5)) throw ApiException.badRequest("Indica el motivo (mínimo 5 caracteres)");
        EstadoEquipo antes = e.getEstado();
        e.setEstado(nuevo);
        historial.save(new HistorialEvento(mapper.ahora(), "ESTADO", e, null, null, actor.getNombre(),
                antes.texto() + " → " + nuevo.texto() + (motivo == null ? "" : ": " + motivo)));
        return mapper.equipoAdmin(e);
    }

    /** Dar de baja = soft delete: sale del inventario activo pero el registro se conserva. */
    @Transactional
    public EquipoAdminDto baja(Long actorId, String placa, String motivo) {
        Usuario actor = acceso.usuario(actorId);
        acceso.exigir(actor, Permiso.INVENTARIO_GESTIONAR);
        if (motivo == null || motivo.trim().length() < 5) throw ApiException.badRequest("Indique el motivo de la baja");
        Equipo e = acceso.equipoParaEditar(actor, placa);
        var ahora = mapper.ahora();
        e.darDeBaja(motivo.trim(), ahora);
        historial.save(new HistorialEvento(ahora, "BAJA", e, null, null, actor.getNombre(), "Baja (soft delete): " + motivo.trim()));
        return mapper.equipoAdmin(e);
    }

    // ------------------------------------------------------------------ Validaciones

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

    private Datos validar(Usuario actor, EquipoRequest r, String labActual, String categoriaActual, String fisicoActual) {
        String nombre = Texto.obligatorio(r.nombre(), 2, 120, "El nombre");
        String cat = Texto.limpio(r.categoria());
        if (cat == null) throw ApiException.badRequest("La categoría es obligatoria");
        String categoria = cat.equals(categoriaActual) ? cat : configuracion.validar(Catalogo.Tipo.CATEGORIA_EQUIPO, cat, "la categoría");
        String lab = laboratorios.validar(actor, Texto.limpio(r.laboratorio()), labActual);
        String f = Texto.limpio(r.estadoFisico()) == null ? "Bueno" : r.estadoFisico().trim();
        String fisico = f.equals(fisicoActual) ? f : configuracion.validar(Catalogo.Tipo.ESTADO_FISICO, f, "el estado físico");
        String foto = Texto.limpio(r.fotoUrl());
        if (foto != null && !(foto.startsWith("/images/") || foto.startsWith("https://") || foto.startsWith("http://")
                || ImagenService.esRutaPropia(foto))) {
            throw ApiException.badRequest("La fotografía debe ser una imagen subida, una URL http(s) o una ruta /images/…");
        }
        if (foto != null && foto.length() > 400) throw ApiException.badRequest("La URL de la fotografía es demasiado larga");
        return new Datos(nombre, Texto.opcional(r.marca(), 60, "La marca"), Texto.opcional(r.modelo(), 60, "El modelo"),
                Texto.opcional(r.numeroSerie(), 60, "El número de serie"), categoria, lab, Texto.opcional(r.ubicacion(), 160, "La ubicación"),
                Texto.opcional(r.descripcion(), 500, "La descripción"), pipe(r.specs()), pipe(r.accesorios()), fisico, foto);
    }

    private static String pipe(List<String> l) {
        if (l == null) return null;
        String j = l.stream().map(String::trim).filter(x -> !x.isEmpty()).map(x -> x.replace("|", "/")).reduce((a, b) -> a + "|" + b).orElse(null);
        if (j != null && j.length() > 600) throw ApiException.badRequest("Las especificaciones o accesorios son demasiado largos (máximo 600 caracteres)");
        return j;
    }

    private static String nz(String s) { return s == null ? "" : s; }
    private static boolean eq(String a, String b) { return nz(a).equals(nz(b)); }
}
