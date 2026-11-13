package co.edu.usco.labcontrol.domain;

/** EQUIPO: se presta y se devuelve (placa USCO-EQ-000). INSUMO: se consume por cantidades (placa USCO-IN-000). */
public enum TipoElemento {
    EQUIPO("USCO-EQ-"), INSUMO("USCO-IN-");

    private final String prefijo;
    TipoElemento(String prefijo) { this.prefijo = prefijo; }
    public String prefijo() { return prefijo; }

    /** El tipo se deduce de la placa: USCO-IN-… es insumo, el resto es equipo. */
    public static TipoElemento dePlaca(String placa) {
        return placa != null && placa.toUpperCase().startsWith(INSUMO.prefijo) ? INSUMO : EQUIPO;
    }
}
