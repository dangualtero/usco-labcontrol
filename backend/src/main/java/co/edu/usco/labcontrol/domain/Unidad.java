package co.edu.usco.labcontrol.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Unidades de medida del inventario. Cada una pertenece a una familia y tiene un factor respecto a la unidad más
 * pequeña de la familia, para poder convertir (un estudiante puede pedir 250 mL de un insumo que se guarda en litros).
 */
public enum Unidad {
    UNIDAD(Familia.CONTEO, "1", "und"),
    ML(Familia.VOLUMEN, "1", "mL"), L(Familia.VOLUMEN, "1000", "L"),
    MG(Familia.MASA, "1", "mg"), G(Familia.MASA, "1000", "g"), KG(Familia.MASA, "1000000", "kg"),
    CM(Familia.LONGITUD, "1", "cm"), M(Familia.LONGITUD, "100", "m");

    /** Piezas (implementos), líquidos, sólidos o longitudes. */
    public enum Familia { CONTEO, VOLUMEN, MASA, LONGITUD }

    private final Familia familia;
    private final BigDecimal factor;
    private final String simbolo;

    Unidad(Familia familia, String factor, String simbolo) {
        this.familia = familia; this.factor = new BigDecimal(factor); this.simbolo = simbolo;
    }

    public Familia familia() { return familia; }
    public String simbolo() { return simbolo; }

    public boolean compatible(Unidad otra) { return otra != null && otra.familia == familia; }

    /** Convierte una cantidad de esta unidad a la unidad destino (misma familia), con 3 decimales. */
    public BigDecimal convertirA(BigDecimal cantidad, Unidad destino) {
        if (!compatible(destino)) throw new IllegalArgumentException("No se puede convertir " + simbolo + " a " + destino.simbolo);
        return cantidad.multiply(factor).divide(destino.factor, 3, RoundingMode.HALF_UP);
    }

    public static Unidad de(String s) {
        if (s == null || s.isBlank()) return null;
        try { return valueOf(s.trim().toUpperCase()); } catch (IllegalArgumentException e) { return null; }
    }
}
