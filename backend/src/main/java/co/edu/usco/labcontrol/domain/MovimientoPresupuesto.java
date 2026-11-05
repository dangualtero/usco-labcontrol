package co.edu.usco.labcontrol.domain;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Movimiento del presupuesto de una sede: un INGRESO suma al saldo y un GASTO lo resta. Los movimientos no se editan
 * ni se borran: un error se corrige anulándolo (queda visible con su motivo y deja de contar en el saldo).
 */
@Entity
@Table(name = "presupuesto_movimientos")
public class MovimientoPresupuesto {
    public enum Tipo { INGRESO, GASTO }

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    private Sede sede;

    @Enumerated(EnumType.STRING)
    private Tipo tipo;

    /** Ej. «Asignación presupuestal 2026-2», «Compra de osciloscopio», «Mantenimiento centrífuga». */
    private String concepto;
    /** Ej. Asignación, Compra de equipos, Mantenimiento, Insumos, Otro. */
    private String categoria;
    /** Valor en pesos colombianos (COP), siempre positivo; el signo lo da el tipo. */
    private long monto;
    /** Fecha del movimiento (la del soporte o factura). */
    private LocalDate fecha;
    /** Placa del equipo relacionado, si aplica (compra o mantenimiento de un equipo). */
    private String placa;

    private String registradoPor;
    private LocalDateTime registradoEn;

    private boolean anulado = false;
    private String anulacionMotivo;
    private String anuladoPor;
    private LocalDateTime anuladoEn;

    protected MovimientoPresupuesto() {}

    public MovimientoPresupuesto(Sede sede, Tipo tipo, String concepto, String categoria, long monto, LocalDate fecha,
                                 String placa, String registradoPor, LocalDateTime registradoEn) {
        this.sede = sede; this.tipo = tipo; this.concepto = concepto; this.categoria = categoria; this.monto = monto;
        this.fecha = fecha; this.placa = placa; this.registradoPor = registradoPor; this.registradoEn = registradoEn;
    }

    public void anular(String motivo, String por, LocalDateTime cuando) {
        this.anulado = true; this.anulacionMotivo = motivo; this.anuladoPor = por; this.anuladoEn = cuando;
    }

    /** Efecto sobre el saldo: +monto si es ingreso, −monto si es gasto, 0 si está anulado. */
    public long efecto() { return anulado ? 0 : (tipo == Tipo.INGRESO ? monto : -monto); }

    public Long getId() { return id; }
    public Sede getSede() { return sede; }
    public Tipo getTipo() { return tipo; }
    public String getConcepto() { return concepto; }
    public String getCategoria() { return categoria; }
    public long getMonto() { return monto; }
    public LocalDate getFecha() { return fecha; }
    public String getPlaca() { return placa; }
    public String getRegistradoPor() { return registradoPor; }
    public LocalDateTime getRegistradoEn() { return registradoEn; }
    public boolean isAnulado() { return anulado; }
    public String getAnulacionMotivo() { return anulacionMotivo; }
    public String getAnuladoPor() { return anuladoPor; }
    public LocalDateTime getAnuladoEn() { return anuladoEn; }
}
