package co.edu.usco.labcontrol.domain;

/**
 * Permisos del sistema, agrupados en los 8 módulos de la hoja de roles. Los valores por defecto de cada rol son los de
 * esa hoja; el Administrador puede cambiarlos en «Parametrización → Permisos» (salvo los que son solo del Administrador).
 * El Administrador siempre tiene todos los permisos.
 */
public enum Permiso {
    // 1. Tablero de control y parametrización
    TABLERO_VER(Modulo.TABLERO, "Ver el tablero con los indicadores de sus laboratorios", true, true, false),
    PARAMETRIZAR(Modulo.TABLERO, "Configurar permisos, catálogos, estados, alertas y plantillas", false, false, true),
    USUARIOS_GESTIONAR(Modulo.TABLERO, "Crear usuarios, asignar roles y laboratorios", false, false, true),
    LABORATORIOS_GESTIONAR(Modulo.TABLERO, "Crear, editar y desactivar laboratorios", false, false, true),
    SEDES_GESTIONAR(Modulo.TABLERO, "Crear y administrar sedes", false, false, true),

    // 2. Laboratorios, inventarios y mantenimientos
    INVENTARIO_GESTIONAR(Modulo.INVENTARIOS, "Registrar bienes e insumos, editar su ficha completa y darlos de baja", true, false, false),
    INVENTARIO_OPERATIVO(Modulo.INVENTARIOS, "Actualizar la información operativa de los bienes y su hoja de vida", true, true, false),
    NOVEDAD_VALIDAR(Modulo.INVENTARIOS, "Validar las novedades reportadas por los monitores", true, false, false),
    MANTENIMIENTO_REQUERIR(Modulo.INVENTARIOS, "Registrar requerimientos de mantenimiento", true, true, false),
    MANTENIMIENTO_GESTIONAR(Modulo.INVENTARIOS, "Programar, ejecutar y cerrar mantenimientos", true, false, false),
    HISTORIAL_VER(Modulo.INVENTARIOS, "Consultar el historial inmutable de sus laboratorios", true, false, false),

    // 3. Solicitudes y requerimientos
    SOLICITUD_CREAR(Modulo.SOLICITUDES, "Crear y editar solicitudes (borradores) con sus soportes", true, true, false),
    SOLICITUD_ENVIAR(Modulo.SOLICITUDES, "Enviar solicitudes directamente al Administrador (sin este permiso pasan primero por el coordinador)", true, false, false),
    SOLICITUD_VALIDAR(Modulo.SOLICITUDES, "Validar las solicitudes preparadas por los monitores y enviarlas", true, false, false),
    SOLICITUD_REVISAR(Modulo.SOLICITUDES, "Revisar, devolver, priorizar, decidir y cerrar solicitudes", false, false, true),

    // 4. Presupuestos y contratación
    PRESUPUESTO_CONSULTAR(Modulo.PRESUPUESTO, "Consultar el estado presupuestal y contractual de sus solicitudes", true, false, false),
    PRESUPUESTO_GESTIONAR(Modulo.PRESUPUESTO, "Registrar la información presupuestal y contractual", false, false, true),

    // 5. Reportes y seguimiento
    REPORTES_GENERAR(Modulo.REPORTES, "Consultar y generar reportes de sus laboratorios", true, true, false),
    AVANCES_REGISTRAR(Modulo.REPORTES, "Registrar avances y evidencias", true, true, false),
    COMPROMISOS_GESTIONAR(Modulo.REPORTES, "Crear y actualizar compromisos de sus laboratorios", true, false, false),
    AVANCES_VERIFICAR(Modulo.REPORTES, "Verificar avances, indicadores y compromisos de todos los laboratorios", false, false, true),

    // 6. Caracterización y documentación
    CARACTERIZACION_CONSULTAR(Modulo.CARACTERIZACION, "Consultar la caracterización y los documentos del laboratorio", true, true, false),
    CARACTERIZACION_EDITAR(Modulo.CARACTERIZACION, "Actualizar los datos del laboratorio y cargar documentos y evidencias", true, false, false),

    // 7. Consulta operativa
    INVENTARIO_CONSULTAR(Modulo.CONSULTA, "Consultar el inventario, las hojas de vida y los mantenimientos programados", true, true, false),
    ALERTAS_VER(Modulo.CONSULTA, "Ver las alertas de sus laboratorios", true, true, false),

    // 8. Registro preliminar
    NOVEDAD_REPORTAR(Modulo.REGISTRO, "Reportar fallas, novedades de equipos y existencias de insumos con soportes", true, true, false);

    public enum Modulo {
        TABLERO("Tablero de control y parametrización"), INVENTARIOS("Laboratorios, inventarios y mantenimientos"),
        SOLICITUDES("Solicitudes y requerimientos"), PRESUPUESTO("Presupuestos y contratación"),
        REPORTES("Reportes y seguimiento"), CARACTERIZACION("Caracterización y documentación"),
        CONSULTA("Consulta operativa"), REGISTRO("Registro preliminar");

        private final String texto;
        Modulo(String texto) { this.texto = texto; }
        public String texto() { return texto; }
    }

    private final Modulo modulo;
    private final String descripcion;
    private final boolean coordinador;
    private final boolean monitor;
    private final boolean soloAdministrador;

    Permiso(Modulo modulo, String descripcion, boolean coordinador, boolean monitor, boolean soloAdministrador) {
        this.modulo = modulo; this.descripcion = descripcion; this.coordinador = coordinador; this.monitor = monitor;
        this.soloAdministrador = soloAdministrador;
    }

    public Modulo modulo() { return modulo; }
    public String descripcion() { return descripcion; }
    public boolean soloAdministrador() { return soloAdministrador; }

    /** Valor por defecto (el de la hoja de roles). */
    public boolean porDefecto(Rol rol) {
        return switch (rol) {
            case ADMINISTRADOR -> true;
            case COORDINADOR_LAB -> coordinador;
            case MONITOR -> monitor;
        };
    }
}
