package co.edu.usco.labcontrol.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;

@Entity
@Table(name = "equipos")
public class Equipo {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Placa de inventario, formato USCO-EQ-000 (PU-07, PA-09). */
    private String placa;

    private String nombre;
    private String marca;
    private String modelo;
    private String numeroSerie;
    private String categoria;
    private String laboratorio;
    /** Ubicación física exacta dentro del laboratorio (ej. «Bloque B · Lab. Electrónica · Mesa 3»). */
    private String ubicacion;
    private String descripcion;
    /** Especificaciones separadas por el caracter pipe. */
    private String specs;
    /** Accesorios incluidos en el préstamo, separados por el caracter pipe. */
    private String accesorios;
    /** Condición física: Excelente, Bueno, Regular o Deteriorado. */
    private String estadoFisico = "Bueno";
    /** Ruta local (/images/equipos/...) o URL de la fotografía. */
    private String fotoUrl;

    @Enumerated(EnumType.STRING)
    private EstadoEquipo estado = EstadoEquipo.DISPONIBLE;

    /** Soft delete (PS-08): el equipo dado de baja se conserva para auditoría. */
    private boolean activo = true;
    private String bajaMotivo;
    private LocalDateTime bajaFecha;

    /** Sede dueña del equipo: solo la ven y lo prestan los usuarios y auxiliares de esa sede. */
    @ManyToOne
    private Sede sede;

    /** EQUIPO (se presta) o INSUMO (se consume por cantidades). */
    @Enumerated(EnumType.STRING)
    private TipoElemento tipo = TipoElemento.EQUIPO;
    /** Existencias en la unidad del elemento (equipos: número de piezas). */
    private BigDecimal cantidad = BigDecimal.ONE;
    @Enumerated(EnumType.STRING)
    private Unidad unidad = Unidad.UNIDAD;
    /** Cantidad «llena» (100 %). Con 25 % o menos el insumo aparece en Alertas. */
    private BigDecimal cantidadReferencia;

    protected Equipo() {}

    public Equipo(String placa, String nombre, String marca, String modelo, String numeroSerie, String categoria,
                  String laboratorio, String ubicacion, String descripcion, String specs, String accesorios,
                  String estadoFisico, String fotoUrl, EstadoEquipo estado) {
        this.placa = placa; this.nombre = nombre; this.marca = marca; this.modelo = modelo;
        this.numeroSerie = numeroSerie; this.categoria = categoria; this.laboratorio = laboratorio;
        this.ubicacion = ubicacion; this.descripcion = descripcion; this.specs = specs;
        this.accesorios = accesorios; this.estadoFisico = estadoFisico; this.fotoUrl = fotoUrl; this.estado = estado;
    }

    public void actualizar(String nombre, String marca, String modelo, String numeroSerie, String categoria,
                           String laboratorio, String ubicacion, String descripcion, String specs, String accesorios,
                           String estadoFisico, String fotoUrl) {
        this.nombre = nombre; this.marca = marca; this.modelo = modelo; this.numeroSerie = numeroSerie;
        this.categoria = categoria; this.laboratorio = laboratorio; this.ubicacion = ubicacion;
        this.descripcion = descripcion; this.specs = specs; this.accesorios = accesorios;
        this.estadoFisico = estadoFisico; this.fotoUrl = fotoUrl;
    }

    /** Define tipo, existencias y unidad. Si se repone por encima del 100 %, la referencia sube a la nueva cantidad. */
    public void definirCantidad(TipoElemento tipo, BigDecimal cantidad, Unidad unidad, BigDecimal referencia) {
        this.tipo = tipo; this.cantidad = cantidad; this.unidad = unidad;
        if (tipo != TipoElemento.INSUMO) { this.cantidadReferencia = null; return; }
        BigDecimal ref = referencia == null ? cantidad : referencia;
        if (ref.compareTo(cantidad) < 0) ref = cantidad;
        this.cantidadReferencia = ref.signum() > 0 ? ref : null;
    }

    public void cambiarLaboratorio(String laboratorio) { this.laboratorio = laboratorio; }

    /** Descuenta un consumo (en la unidad del elemento). */
    public void descontar(BigDecimal consumo) {
        if (consumo.compareTo(cantidad) > 0) throw new IllegalStateException("Existencias insuficientes");
        this.cantidad = cantidad.subtract(consumo);
    }

    public boolean esInsumo() { return tipo == TipoElemento.INSUMO; }

    /** Porcentaje de existencias respecto a la referencia (null si no hay referencia). */
    public Integer porcentaje() {
        if (cantidadReferencia == null || cantidadReferencia.signum() <= 0 || cantidad == null) return null;
        return cantidad.multiply(BigDecimal.valueOf(100)).divide(cantidadReferencia, 0, RoundingMode.HALF_UP).intValue();
    }

    public void darDeBaja(String motivo, LocalDateTime cuando) {
        this.activo = false;
        this.bajaMotivo = motivo;
        this.bajaFecha = cuando;
    }

    public Long getId() { return id; }
    public String getPlaca() { return placa; }
    public String getNombre() { return nombre; }
    public String getMarca() { return marca; }
    public String getModelo() { return modelo; }
    public String getNumeroSerie() { return numeroSerie; }
    public String getCategoria() { return categoria; }
    public String getLaboratorio() { return laboratorio; }
    public String getUbicacion() { return ubicacion; }
    public String getDescripcion() { return descripcion; }
    public String getSpecs() { return specs; }
    public String getAccesorios() { return accesorios; }
    public String getEstadoFisico() { return estadoFisico; }
    public String getFotoUrl() { return fotoUrl; }
    public EstadoEquipo getEstado() { return estado; }
    public void setEstado(EstadoEquipo estado) { this.estado = estado; }
    public boolean isActivo() { return activo; }
    public String getBajaMotivo() { return bajaMotivo; }
    public LocalDateTime getBajaFecha() { return bajaFecha; }
    public Sede getSede() { return sede; }
    public void setSede(Sede sede) { this.sede = sede; }
    public TipoElemento getTipo() { return tipo == null ? TipoElemento.EQUIPO : tipo; }
    public BigDecimal getCantidad() { return cantidad; }
    public Unidad getUnidad() { return unidad == null ? Unidad.UNIDAD : unidad; }
    public BigDecimal getCantidadReferencia() { return cantidadReferencia; }
}
