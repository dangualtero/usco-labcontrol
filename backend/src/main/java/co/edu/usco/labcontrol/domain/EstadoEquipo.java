package co.edu.usco.labcontrol.domain;

/** DISPONIBLE (operativo), MANTENIMIENTO o FUERA_SERVICIO. */
public enum EstadoEquipo {
    DISPONIBLE("Operativo"), MANTENIMIENTO("En mantenimiento"), FUERA_SERVICIO("Fuera de servicio");

    private final String texto;
    EstadoEquipo(String texto) { this.texto = texto; }
    public String texto() { return texto; }
}
