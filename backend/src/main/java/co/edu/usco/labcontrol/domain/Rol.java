package co.edu.usco.labcontrol.domain;

/**
 * ADMINISTRADOR: administra el aplicativo y hace el seguimiento institucional (todos los laboratorios).
 * COORDINADOR_LAB: docente responsable de la información, los requerimientos y el seguimiento de sus laboratorios.
 * MONITOR: apoya el registro operativo bajo la supervisión del coordinador, solo en sus laboratorios.
 */
public enum Rol {
    ADMINISTRADOR("Administrador general"), COORDINADOR_LAB("Coordinador de Laboratorio"), MONITOR("Monitor de Laboratorio");

    private final String texto;
    Rol(String texto) { this.texto = texto; }
    public String texto() { return texto; }

    public static Rol de(String s) {
        try { return s == null ? null : valueOf(s.trim().toUpperCase()); } catch (IllegalArgumentException e) { return null; }
    }
}
