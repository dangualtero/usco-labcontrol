package co.edu.usco.labcontrol.domain;

public enum Rol {
    ESTUDIANTE, DOCENTE, AUXILIAR, COORDINADOR;

    /** Grupo de interfaz: Estudiante y Docente comparten el panel de usuario final. */
    public String grupo() {
        return switch (this) {
            case ESTUDIANTE, DOCENTE -> "USUARIO";
            case AUXILIAR -> "AUXILIAR";
            case COORDINADOR -> "COORDINADOR";
        };
    }
}
