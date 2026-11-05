package co.edu.usco.labcontrol.domain;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Movimiento del presupuesto de una sede: un INGRESO suma al saldo y un GASTO lo resta. Los movimientos no se editan
 * ni se borran: un error se corrige anulándolo (queda visible con su motivo y deja de contar en el saldo).
 */
@Entity
@Table(name = "presupuesto_movimientos", indexes = @Index(name = "idx_presupuesto_sede_fecha", columnList = "sede_id,fecha"))
public class MovimientoPresupuesto {
    public enum Tipo { INGRESO, GASTO }

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER, optional = false) @JoinColumn(name = "sede_id")
    private Sede sede;

    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 10)
    private Tipo tipo;

    /** Ej. «Asignación presupuestal 2026-2», «Compra de osciloscopio», «Mantenimiento centrífuga». */
    @Column(nullable = false, length = 160) private String concepto;
    /** Ej. Asignación, Compra de equipos, Mantenimiento, Insumos, Otro. */
    @Column(nullable = false, length = 40) private String categoria;
    /** Valor en pesos colombianos (COP), siempre positivo; el signo lo da el tipo. */
    @Column(nullable = false) private long monto;
    /** Fecha del movimiento (la del soporte o factura). */
    @Column(nullable = false) private LocalDate fecha;
    /** Placa del equipo relacionado, si aplica (compra o mantenimiento de un equipo). */
    @Column(length = 20) private String placa;

    @Column(name = "registrado_por", nullable = false, length = 120) private String registradoPor;
    @Column(name = "registrado_en", nullable = false) private LocalDateTime registradoEn;

    @Column(nullable = false) private boolean anulado = false;
    @Column(name = "anulacion_motivo", length = 300) private String anulacionMotivo;
    @Column(name = "anulado_por", length = 120) private String anuladoPor;
    @Column(name = "anulado_en") private LocalDateTime anuladoEn;

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
