package co.edu.usco.labcontrol.service;

import co.edu.usco.labcontrol.domain.*;
import co.edu.usco.labcontrol.repo.*;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Carga inicial en MySQL (solo si la tabla de usuarios está vacía): cuentas reales con contraseña cifrada con BCrypt,
 * inventario de los 4 laboratorios y, opcionalmente, préstamos e historial de ejemplo para poblar los paneles.
 * Las credenciales iniciales están documentadas en README.md; se pueden cambiar desde «Mi cuenta» o por el Coordinador.
 */
@Component
public class DataInitializer implements CommandLineRunner {
    private final UsuarioRepository usuarios;
    private final EquipoRepository equipos;
    private final PrestamoRepository prestamos;
    private final HistorialRepository historial;
    private final PasswordEncoder encoder;
    private final Clock clock;
    private final boolean habilitado;
    private final boolean historialEjemplo;
    private int consecutivo = 10001;

    public DataInitializer(UsuarioRepository usuarios, EquipoRepository equipos, PrestamoRepository prestamos,
                           HistorialRepository historial, PasswordEncoder encoder, Clock clock,
                           @Value("${labcontrol.seed.habilitado}") boolean habilitado,
                           @Value("${labcontrol.seed.historial-ejemplo}") boolean historialEjemplo) {
        this.usuarios = usuarios; this.equipos = equipos; this.prestamos = prestamos; this.historial = historial;
        this.encoder = encoder; this.clock = clock; this.habilitado = habilitado; this.historialEjemplo = historialEjemplo;
    }

    private Usuario u(String nombre, String correo, String clave, Rol rol, String doc, String codigo, String programa) {
        return new Usuario(nombre, correo, encoder.encode(clave), rol, doc, codigo, programa);
    }

    @Override
    @Transactional
    public void run(String... args) {
        if (!habilitado || usuarios.count() > 0) return;
        LocalDateTime n = LocalDateTime.now(clock).withSecond(0).withNano(0);

        // ---------------- Usuarios (contraseñas iniciales: ver README.md)
        Usuario coord = u("Sandra Milena Vargas", "coordinador@usco.edu.co", "Coord#USCO2026", Rol.COORDINADOR, "36500100", null, "Coordinación de Laboratorios");
        Usuario daniel = u("Daniel Felipe Gualtero", "daniel.gualtero@usco.edu.co", "Daniel#USCO2026", Rol.COORDINADOR, "1075100001", null, "Ingeniería de Software");
        Usuario carlos = u("Carlos David Neira Vega", "carlos.neira@usco.edu.co", "Carlos#USCO2026", Rol.AUXILIAR, "1075100002", null, "Ingeniería de Software");
        Usuario cesar = u("Cesar Sneider Pimiento Torres", "cesar.pimiento@usco.edu.co", "Cesar#USCO2026", Rol.ESTUDIANTE, "1075100003", "20221100003", "Ingeniería de Software");
        Usuario aux1 = u("Juan Pablo Trujillo", "auxiliar@usco.edu.co", "Aux#USCO2026", Rol.AUXILIAR, "1075300101", null, "Ing. Electrónica");
        Usuario aux2 = u("Marcela Ortiz", "auxiliar2@usco.edu.co", "Aux2#USCO2026", Rol.AUXILIAR, "1075300102", null, "Ing. Civil");
        Usuario perdomo = u("Mauricio Perdomo", "docente@usco.edu.co", "Doc#USCO2026", Rol.DOCENTE, "36600200", null, "Ing. Agrícola");
        Usuario laura = u("Laura Gómez", "estudiante@usco.edu.co", "Est#USCO2026", Rol.ESTUDIANTE, "1075123456", "20211100456", "Ing. Electrónica");
        Usuario andres = u("Andrés Ramírez", "andres.ramirez@usco.edu.co", "Andres#USCO2026", Rol.ESTUDIANTE, "1075200011", "20201100011", "Ing. Civil");
        Usuario valentina = u("Valentina Cuéllar", "valentina.cuellar@usco.edu.co", "Valentina#USCO2026", Rol.ESTUDIANTE, "1075200012", "20211100012", "Ing. de Petróleos");
        Usuario camila = u("Camila Rojas", "camila.rojas@usco.edu.co", "Camila#USCO2026", Rol.ESTUDIANTE, "1075200013", "20221100013", "Ing. Agrícola");
        Usuario sebastian = u("Sebastián Medina", "sebastian.medina@usco.edu.co", "Sebastian#USCO2026", Rol.ESTUDIANTE, "1075200014", "20211100014", "Ing. Electrónica");
        usuarios.saveAll(List.of(coord, daniel, carlos, cesar, aux1, aux2, perdomo, laura, andres, valentina, camila, sebastian));

        // ---------------- Inventario
        Map<String, Equipo> eq = new LinkedHashMap<>();
        for (Equipo e : catalogo()) eq.put(e.getPlaca(), e);
        equipos.saveAll(eq.values());
        if (!historialEjemplo) return;

        // ---------------- Historial de ejemplo (últimos 28 días)
        List<Usuario> solicitantes = List.of(laura, andres, valentina, camila, sebastian, perdomo, cesar);
        List<Equipo> prestables = eq.values().stream().filter(e -> e.isActivo() && e.getEstado() != EstadoEquipo.MANTENIMIENTO).toList();
        Random rnd = new Random(2026);
        devuelto(eq.get("USCO-EQ-103"), laura, aux1, "Circuitos Electrónicos II", n.minusDays(2).withHour(9).withMinute(0), 2, 0, null, "[Buen estado]");
        devuelto(eq.get("USCO-EQ-100"), laura, aux1, "Circuitos Electrónicos II", n.minusDays(6).withHour(14).withMinute(0), 2, 0, null, "[Buen estado]");
        devuelto(eq.get("USCO-EQ-203"), laura, aux2, "Química Analítica", n.minusDays(11).withHour(10).withMinute(0), 3, 25, null,
                "[Buen estado · Daños menores preexistentes] Rayón leve en el cuerpo del hidrómetro, ya existía en el registro anterior.");
        devuelto(eq.get("USCO-EQ-301"), laura, aux2, "Topografía Básica", n.minusDays(17).withHour(8).withMinute(0), 4, 0, null, "[Buen estado]");
        devuelto(eq.get("USCO-EQ-105"), sebastian, aux1, "Instrumentación Electrónica", n.minusDays(4).withHour(15).withMinute(0), 2, 0, "INC-2026-001",
                "[Presenta daños] Puerto de entrada RF dañado; el equipo no enciende la pantalla.");
        devuelto(eq.get("USCO-EQ-202"), camila, aux2, "Fluidos de Perforación", n.minusDays(9).withHour(11).withMinute(0), 3, 0, "INC-2026-002",
                "[Presenta daños] Fuga en la celda de filtración; la tapa no sella.");
        devuelto(eq.get("USCO-EQ-302"), andres, aux2, "Concretos", n.minusDays(14).withHour(7).withMinute(30), 4, 0, "INC-2026-003",
                "[Accesorios incompletos] Falta el yunque de calibración.");
        for (int i = 0; i < 38; i++) {
            Equipo e = prestables.get(rnd.nextInt(prestables.size()));
            Usuario s = solicitantes.get(rnd.nextInt(solicitantes.size()));
            Usuario a = rnd.nextBoolean() ? aux1 : aux2;
            LocalDateTime salida = n.minusDays(1 + rnd.nextInt(27)).withHour(7 + rnd.nextInt(9)).withMinute(rnd.nextBoolean() ? 0 : 30);
            int retraso = rnd.nextInt(100) < 14 ? 20 + rnd.nextInt(90) : 0;
            devuelto(e, s, a, asignaturaDe(e), salida, 1 + rnd.nextInt(4), retraso, null, "[Buen estado]");
        }

        activo(eq.get("USCO-EQ-102"), andres, aux1, "Instrumentación Electrónica", n.minusHours(5), n.minusMinutes(95));   // vencido
        activo(eq.get("USCO-EQ-300"), andres, aux1, "Topografía", n.minusHours(1), n.plusHours(2));
        activo(eq.get("USCO-EQ-402"), andres, aux2, "Mecánica de Suelos", n.minusHours(2), n.plusHours(1));               // 3 activos: límite
        activo(eq.get("USCO-EQ-201"), valentina, aux2, "Fluidos de Perforación", n.minusHours(3), n.plusHours(1));
        activo(eq.get("USCO-EQ-401"), perdomo, aux2, "Climatología Agrícola", n.minusHours(26), n.minusHours(24));         // vencido ayer

        reserva(eq.get("USCO-EQ-100"), laura, "Circuitos Electrónicos II", "Ing. Marcela Ortiz", n.plusHours(1), n.plusHours(3));
        reserva(eq.get("USCO-EQ-303"), valentina, "Materiales de Construcción", null, n.plusHours(2), n.plusHours(4));
    }

    private void devuelto(Equipo e, Usuario s, Usuario aux, String asignatura, LocalDateTime salida, int horas,
                          int minutosRetraso, String incidencia, String observaciones) {
        LocalDateTime limite = salida.plusHours(horas);
        Prestamo p = new Prestamo("PR-" + consecutivo++, e, s, EstadoPrestamo.DEVUELTO, asignatura, salida, limite, salida.minusHours(3));
        p.setFechaSalida(salida);
        p.setFechaLimite(limite);
        p.setFechaEntrada(limite.plusMinutes(minutosRetraso - (minutosRetraso == 0 ? 12 : 0)));
        p.setAuxiliarSalida(aux); p.setAuxiliarEntrada(aux); p.setCustodio(aux);
        p.setEstadoEntrada(incidencia != null ? "CON_NOVEDAD" : "BUEN_ESTADO");
        p.setObservaciones(observaciones);
        p.setIncidencia(incidencia);
        prestamos.save(p);
        historial.save(new HistorialEvento(salida, "SALIDA", e, p.getCodigo(), s.getNombre(), aux.getNombre(),
                "Salida registrada. Devolución límite " + limite.toLocalTime()));
        historial.save(new HistorialEvento(p.getFechaEntrada(), "ENTRADA", e, p.getCodigo(), s.getNombre(), aux.getNombre(),
                "Devolución registrada. " + observaciones));
        if (incidencia != null) {
            historial.save(new HistorialEvento(p.getFechaEntrada(), "INCIDENCIA", e, p.getCodigo(), s.getNombre(), aux.getNombre(),
                    "Incidencia " + incidencia));
        }
    }

    private void activo(Equipo e, Usuario s, Usuario aux, String asignatura, LocalDateTime salida, LocalDateTime limite) {
        Prestamo p = new Prestamo("PR-" + consecutivo++, e, s, EstadoPrestamo.ACTIVO, asignatura, salida, limite, salida.minusHours(1));
        p.setFechaSalida(salida); p.setFechaLimite(limite);
        p.setAuxiliarSalida(aux); p.setCustodio(aux);
        prestamos.save(p);
        e.setEstado(EstadoEquipo.PRESTADO);
        historial.save(new HistorialEvento(salida, "SALIDA", e, p.getCodigo(), s.getNombre(), aux.getNombre(),
                "Salida registrada. Devolución límite " + limite.toLocalTime()));
    }

    private void reserva(Equipo e, Usuario s, String asignatura, String docente, LocalDateTime ini, LocalDateTime fin) {
        Prestamo p = new Prestamo("RES-" + consecutivo++, e, s, EstadoPrestamo.RESERVADO, asignatura, ini, fin, LocalDateTime.now(clock).minusMinutes(40));
        p.setDocente(docente);
        prestamos.save(p);
        historial.save(new HistorialEvento(p.getFechaSolicitud(), "RESERVA", e, p.getCodigo(), s.getNombre(), null,
                "Reserva " + p.getCodigo() + " para " + asignatura));
    }

    private static String asignaturaDe(Equipo e) {
        return switch (e.getLaboratorio()) {
            case "Electrónica" -> "Circuitos y Medidas Eléctricas";
            case "Petróleos" -> "Propiedades de los Fluidos";
            case "Civil" -> "Topografía y Geodesia";
            default -> "Agrometeorología";
        };
    }

    private static Equipo eq(String placa, String nombre, String marca, String modelo, String serie, String categoria, String lab,
                             String ubicacion, String desc, String specs, String accesorios, String fisico, EstadoEquipo estado) {
        String foto = "/images/equipos/" + placa.toLowerCase() + ".jpg";
        return new Equipo(placa, nombre, marca, modelo, serie, categoria, lab, ubicacion, desc, specs, accesorios, fisico, foto, estado);
    }

    /** Inventario inicial. Series y ubicaciones son datos de referencia: reemplazar por los del inventario físico. */
    private static List<Equipo> catalogo() {
        var D = EstadoEquipo.DISPONIBLE;
        var M = EstadoEquipo.MANTENIMIENTO;
        String ELE = "Bloque de Ingeniería · Laboratorio de Electrónica";
        String PET = "Bloque de Ingeniería · Laboratorio de Petróleos";
        String CIV = "Bloque de Ingeniería · Laboratorio de Civil";
        String AGR = "Sede Neiva · Laboratorio de Agrícola";
        List<Equipo> l = new ArrayList<>(List.of(
            eq("USCO-EQ-100", "Fuente de Poder DC Programable", "Rigol", "DP832", "DP8A242100101", "Fuentes y generadores", "Electrónica", ELE + " · Mesa 1",
                "Fuente lineal de 3 canales para alimentar prototipos y circuitos de práctica.", "3 canales · 30 V / 3 A|Resolución 1 mV / 1 mA|Protección OVP/OCP",
                "Cable de poder|Juego de cables banana-banana|Manual rápido", "Excelente", D),
            eq("USCO-EQ-101", "Generador de Funciones", "Rigol", "DG1022Z", "DG1Z242200202", "Fuentes y generadores", "Electrónica", ELE + " · Mesa 1",
                "Generador arbitrario de dos canales para señales senoidales, cuadradas y de pulso.", "2 canales · 25 MHz|Muestreo 200 MSa/s|Modulación AM/FM/PM",
                "Cable de poder|2 cables BNC-BNC|Cable USB", "Bueno", D),
            eq("USCO-EQ-102", "Osciloscopio Digital Tektronix", "Tektronix", "TBS1052B", "C0192210302", "Osciloscopios", "Electrónica", ELE + " · Mesa 2",
                "Osciloscopio digital de almacenamiento de 2 canales para análisis de señales en el dominio del tiempo.", "50 MHz · 2 canales|Muestreo 1 GS/s|Pantalla TFT 7 pulgadas",
                "Cable de poder|2 puntas de prueba P2220|Cable USB", "Bueno", D),
            eq("USCO-EQ-103", "Multímetro Fluke 179", "Fluke", "179", "44830015", "Multímetros", "Electrónica", ELE + " · Mesa 2",
                "Multímetro digital True-RMS con medición de temperatura y capacitancia.", "True-RMS 6000 cuentas|CAT III 1000 V / CAT IV 600 V|Temperatura y capacitancia",
                "Puntas de prueba rojo/negro|Termopar tipo K|Estuche", "Excelente", D),
            eq("USCO-EQ-104", "Estación de Soldadura", "Hakko", "FX-888D", "FX8D-0415", "Soldadura", "Electrónica", ELE + " · Mesa 4",
                "Estación digital con control de temperatura para ensamble y reparación de circuitos.", "70 W|Rango 200–480 °C|Control digital de temperatura",
                "Cable de poder|Soporte con esponja|Punta de repuesto", "Bueno", D),
            eq("USCO-EQ-105", "Analizador de Espectro", "Rigol", "DSA815", "DSA8A242300505", "Osciloscopios", "Electrónica", ELE + " · Estante de instrumentos",
                "Analizador de espectro para caracterización de señales de radiofrecuencia.", "9 kHz – 1.5 GHz|RBW 100 Hz – 1 MHz|Generador de seguimiento opcional",
                "Cable de poder|Cable BNC-SMA|Cable USB", "Regular", M),
            eq("USCO-EQ-200", "Viscosímetro Rotacional Fann Modelo 35", "Fann", "35SA", "F35-8841", "Análisis de fluidos", "Petróleos", PET + " · Mesa de fluidos 1",
                "Determinación de viscosidad plástica, punto cedente y geles de fluidos de perforación.", "6 velocidades (3–600 rpm)|Lectura directa de dial|Taza térmica incluida",
                "Taza de 350 mL|Juego de bob y rotor R1-B1|Cable de poder", "Bueno", D),
            eq("USCO-EQ-201", "Balanza de Lodos Presurizada", "Fann", "Mud Balance 140", "FMB-2217", "Análisis de fluidos", "Petróleos", PET + " · Mesa de fluidos 1",
                "Medición de la densidad real del lodo presurizado, eliminando el aire incorporado.", "Rango 6–24 lb/gal|Precisión ±0.1 lb/gal|Copa presurizada",
                "Estuche|Émbolo de presurización|Contrapeso", "Bueno", D),
            eq("USCO-EQ-202", "Filtro Prensa API de Baja Presión", "Fann", "Serie 300", "FFP-0932", "Análisis de fluidos", "Petróleos", PET + " · Mesa de fluidos 2",
                "Medición de pérdida de filtrado y espesor del revoque de fluidos de perforación a 100 psi.", "Presión 100 psi|Área de filtración 45 cm²|Celda en acero inoxidable",
                "Celda de lodo|Papel filtro Whatman (caja)|Cilindro graduado 25 mL|Regulador y manguera", "Regular", M),
            eq("USCO-EQ-203", "Hidrómetro API / Densímetro", "Cole-Parmer", "ASTM 3H", "H3H-5520", "Análisis de fluidos", "Petróleos", PET + " · Armario de vidriería",
                "Determinación de la gravedad API y la densidad de crudos y derivados del petróleo.", "Escala 29–41 °API|Termómetro incorporado|Norma ASTM D287",
                "Probeta de vidrio 500 mL|Estuche rígido|Certificado de calibración", "Bueno", D),
            eq("USCO-EQ-300", "Estación Total", "Topcon", "GM-55", "GM55-7B2210", "Topografía", "Civil", CIV + " · Gabinete de topografía",
                "Estación total electrónica para levantamientos y replanteo topográfico.", "Precisión angular 5\"|Alcance 500 m sin prisma|Bluetooth y memoria interna",
                "Trípode de aluminio|Prisma con bastón|2 baterías y cargador|Estuche rígido", "Excelente", D),
            eq("USCO-EQ-301", "Nivel Automático Óptico", "Bosch", "GOL 26 D", "GOL26D-3318", "Topografía", "Civil", CIV + " · Gabinete de topografía",
                "Nivel óptico automático de 26 aumentos para nivelación de precisión.", "26 aumentos|Precisión 1.6 mm / 30 m|Compensador de péndulo",
                "Trípode|Mira de aluminio 5 m|Estuche|Plomada", "Bueno", D),
            eq("USCO-EQ-302", "Esclerómetro Digital de Concreto (Martillo Schmidt)", "Controls", "Digi-Schmidt 2000", "DS2-1129", "Ensayos de materiales", "Civil", CIV + " · Laboratorio de concretos",
                "Estimación no destructiva de la resistencia a compresión del concreto.", "Energía de impacto 2.207 J|Lectura digital|Memoria de ensayos",
                "Yunque de calibración|Piedra de pulido|Estuche", "Bueno", D),
            eq("USCO-EQ-303", "Tamizadora Eléctrica de Agregados", "Humboldt", "H-4325", "HT-4325-208", "Ensayos de materiales", "Civil", CIV + " · Laboratorio de agregados",
                "Agitador de tamices para análisis granulométrico de agregados finos y gruesos.", "Capacidad hasta 8 tamices de 8 pulg|Temporizador digital|Vibración horizontal y vertical",
                "Cable de poder|Tapa y fondo|Cepillo de limpieza", "Bueno", D),
            eq("USCO-EQ-400", "Medidor Multiparámetro de Suelos (pH/Conductividad)", "Hanna", "HI 8314", "H8314-6021", "Medición de suelos", "Agrícola", AGR + " · Mesa de suelos",
                "Medición de pH, conductividad y temperatura en muestras de suelo y soluciones nutritivas.", "pH 0–14|Conductividad 0–4000 µS/cm|Compensación de temperatura",
                "Electrodo de pH|Sonda de temperatura|Soluciones buffer 4 y 7|Estuche", "Bueno", D),
            eq("USCO-EQ-401", "Estación Meteorológica Portátil", "Davis", "Vantage Vue", "VV-6250-2210", "Climatología", "Agrícola", AGR + " · Bodega de campo",
                "Estación inalámbrica para temperatura, humedad, viento y precipitación.", "Alcance inalámbrico 300 m|Consola con registro|Pluviómetro de cazoleta",
                "Consola|Sensor integrado (ISS)|Soporte y mástil|Baterías", "Excelente", D),
            eq("USCO-EQ-402", "Tensiómetro de Humedad de Suelo", "Irrometer", "Modelo SR", "IRR-SR-3305", "Medición de suelos", "Agrícola", AGR + " · Bodega de campo",
                "Medición de la tensión de humedad del suelo para programar el riego.", "Rango 0–100 centibares|Manómetro de vacío|Cápsula de cerámica",
                "Tubo de 60 cm|Bomba de vacío manual|Agua desaireada", "Bueno", D),
            eq("USCO-EQ-403", "GPS Submétrico", "Garmin", "GPSMAP 64st", "GM64-5521", "Topografía", "Agrícola", AGR + " · Bodega de campo",
                "Receptor GNSS portátil para levantamiento de lotes y puntos de muestreo.", "GPS + GLONASS|Precisión submétrica con corrección|Pantalla de 2.6 pulg",
                "Cable USB|Baterías AA|Correa|Tarjeta microSD", "Bueno", D)
        ));
        Equipo viejo = eq("USCO-EQ-299", "Viscosímetro Analógico (obsoleto)", "Fann", "35A", "F35A-0099", "Análisis de fluidos", "Petróleos", PET + " · Bodega",
                "Equipo retirado del inventario por obsolescencia.", "Lectura analógica", null, "Deteriorado", D);
        viejo.darDeBaja("Obsolescencia técnica; reemplazado por USCO-EQ-200", LocalDateTime.now().minusDays(40));
        l.add(viejo);
        return l;
    }
}
