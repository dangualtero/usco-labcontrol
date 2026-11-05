package co.edu.usco.labcontrol.service;

import co.edu.usco.labcontrol.domain.MovimientoPresupuesto;
import co.edu.usco.labcontrol.domain.MovimientoPresupuesto.Tipo;
import co.edu.usco.labcontrol.domain.Sede;
import co.edu.usco.labcontrol.domain.Usuario;
import co.edu.usco.labcontrol.repo.MovimientoPresupuestoRepository;
import co.edu.usco.labcontrol.repo.SedeRepository;
import co.edu.usco.labcontrol.repo.UsuarioRepository;
import co.edu.usco.labcontrol.web.ApiException;
import co.edu.usco.labcontrol.web.Dtos.*;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Presupuesto de laboratorios POR SEDE. El saldo es la suma de los ingresos menos los gastos que no estén anulados.
 * No se permite registrar un gasto mayor que el saldo disponible de la sede.
 */
@Service
public class PresupuestoService {
    public static final List<String> CATEGORIAS = List.of("Asignación", "Compra de equipos", "Mantenimiento",
            "Calibración", "Insumos y repuestos", "Otro");
    private static final long MONTO_MAXIMO = 100_000_000_000L;   // 100 mil millones COP

    private final MovimientoPresupuestoRepository movimientos;
    private final SedeRepository sedes;
    private final UsuarioRepository usuarios;
    private final SedeContexto contexto;
    private final Mapper mapper;

    public PresupuestoService(MovimientoPresupuestoRepository movimientos, SedeRepository sedes, UsuarioRepository usuarios,
                              SedeContexto contexto, Mapper mapper) {
        this.movimientos = movimientos; this.sedes = sedes; this.usuarios = usuarios; this.contexto = contexto; this.mapper = mapper;
    }

    /** Presupuesto de la sede seleccionada: totales, gastos por categoría y todos los movimientos (los más recientes primero). */
    @Transactional(readOnly = true)
    public PresupuestoDto deSede(Long actorId) {
        Sede sede = contexto.de(actorId);
        var lista = movimientos.findBySedeIdOrderByFechaDescIdDesc(sede.getId());
        long ingresos = suma(lista, Tipo.INGRESO);
        long gastos = suma(lista, Tipo.GASTO);
        Map<String, Long> porCat = lista.stream().filter(m -> !m.isAnulado() && m.getTipo() == Tipo.GASTO)
                .collect(Collectors.groupingBy(MovimientoPresupuesto::getCategoria, TreeMap::new, Collectors.summingLong(MovimientoPresupuesto::getMonto)));
        var categorias = porCat.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .map(e -> new CategoriaTotalDto(e.getKey(), e.getValue())).toList();
        return new PresupuestoDto(mapper.sede(sede), ingresos, gastos, ingresos - gastos, categorias,
                lista.stream().map(mapper::movimiento).toList());
    }

    /** Resumen de todas las sedes (vista consolidada del Coordinador general). */
    @Transactional(readOnly = true)
    public ResumenPresupuestoDto resumen() {
        Map<Long, List<MovimientoPresupuesto>> porSede = movimientos.findAllByOrderByFechaDescIdDesc().stream()
                .collect(Collectors.groupingBy(m -> m.getSede().getId()));
        List<PresupuestoSedeDto> filas = new ArrayList<>();
        long ti = 0, tg = 0;
        for (Sede s : sedes.findAllByOrderByIdAsc()) {
            var l = porSede.getOrDefault(s.getId(), List.of());
            long i = suma(l, Tipo.INGRESO), g = suma(l, Tipo.GASTO);
            ti += i; tg += g;
            filas.add(new PresupuestoSedeDto(mapper.sede(s), i, g, i - g, l.stream().filter(m -> !m.isAnulado()).count()));
        }
        return new ResumenPresupuestoDto(filas, ti, tg, ti - tg);
    }

    @Transactional
    public MovimientoDto registrar(Long actorId, MovimientoRequest r) {
        Usuario actor = usuarios.findById(actorId).orElseThrow();
        Sede sede = contexto.de(actor);
        Tipo tipo;
        try { tipo = Tipo.valueOf(r.tipo() == null ? "" : r.tipo().trim().toUpperCase()); }
        catch (IllegalArgumentException e) { throw ApiException.badRequest("Indica si es un INGRESO (suma) o un GASTO (resta)"); }
        String concepto = r.concepto() == null ? "" : r.concepto().trim().replaceAll("\\s+", " ");
        if (concepto.length() < 3) throw ApiException.badRequest("Describe el concepto del movimiento");
        if (concepto.length() > 160) throw ApiException.badRequest("El concepto es demasiado largo (máximo 160 caracteres)");
        String categoria = r.categoria() == null ? "" : r.categoria().trim();
        if (!CATEGORIAS.contains(categoria)) throw ApiException.badRequest("Categoría no válida: " + String.join(", ", CATEGORIAS));
        if (r.monto() == null || r.monto() <= 0) throw ApiException.badRequest("El valor debe ser mayor que cero");
        if (r.monto() > MONTO_MAXIMO) throw ApiException.badRequest("El valor supera el máximo permitido");
        LocalDate hoy = mapper.ahora().toLocalDate();
        LocalDate fecha = r.fecha() == null ? hoy : r.fecha();
        if (fecha.isAfter(hoy)) throw ApiException.badRequest("La fecha no puede ser futura");
        if (fecha.isBefore(hoy.minusYears(5))) throw ApiException.badRequest("La fecha es demasiado antigua");
        String placa = r.placa() == null || r.placa().isBlank() ? null : r.placa().trim().toUpperCase();
        if (placa != null && !PrestamoService.FORMATO_PLACA.matcher(placa).matches()) throw ApiException.badRequest("Usa el formato USCO-EQ-000 para la placa");

        if (tipo == Tipo.GASTO) {
            long saldo = saldo(sede);
            if (r.monto() > saldo) {
                throw ApiException.conflict("Saldo insuficiente en la " + sede.getNombre() + ": disponible " + pesos(saldo));
            }
        }
        var m = movimientos.save(new MovimientoPresupuesto(sede, tipo, concepto, categoria, r.monto(), fecha, placa,
                actor.getNombre(), mapper.ahora()));
        return mapper.movimiento(m);
    }

    /** Anular deja el movimiento visible (auditoría) pero sin efecto en el saldo. */
    @Transactional
    public MovimientoDto anular(Long actorId, Long id, String motivo) {
        Usuario actor = usuarios.findById(actorId).orElseThrow();
        Sede sede = contexto.de(actor);
        var m = movimientos.findById(id == null ? -1L : id).filter(x -> sede.es(x.getSede()))
                .orElseThrow(() -> ApiException.notFound("Movimiento no encontrado"));
        if (m.isAnulado()) throw ApiException.conflict("El movimiento ya está anulado");
        if (motivo == null || motivo.trim().length() < 5) throw ApiException.badRequest("Indica el motivo de la anulación");
        if (m.getTipo() == Tipo.INGRESO && saldo(sede) - m.getMonto() < 0) {
            throw ApiException.conflict("No se puede anular: el saldo de la sede quedaría negativo");
        }
        m.anular(motivo.trim(), actor.getNombre(), mapper.ahora());
        return mapper.movimiento(m);
    }

    @Transactional(readOnly = true)
    public long saldo(Sede s) {
        return movimientos.findBySedeIdOrderByFechaDescIdDesc(s.getId()).stream().mapToLong(MovimientoPresupuesto::efecto).sum();
    }

    private static long suma(List<MovimientoPresupuesto> l, Tipo t) {
        return l.stream().filter(m -> !m.isAnulado() && m.getTipo() == t).mapToLong(MovimientoPresupuesto::getMonto).sum();
    }

    static String pesos(long v) { return String.format(Locale.of("es", "CO"), "$%,d", v); }
}
