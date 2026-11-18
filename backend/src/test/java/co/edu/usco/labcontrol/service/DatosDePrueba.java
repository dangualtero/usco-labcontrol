package co.edu.usco.labcontrol.service;

import co.edu.usco.labcontrol.domain.*;
import co.edu.usco.labcontrol.repo.*;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * SOLO PARA LAS PRUEBAS AUTOMÁTICAS (src/test): crea la sede, los laboratorios, las cuentas de los tres roles y el
 * inventario de prueba en la base H2 en memoria. No forma parte de la aplicación: el .jar que se despliega no lo incluye
 * y la base real no tiene usuarios quemados.
 * <ul>
 *   <li>admin@usco.edu.co · Administrador general</li>
 *   <li>coordinador@usco.edu.co · Coordinador de Electrónica y Petróleos</li>
 *   <li>monitor@usco.edu.co · Monitor de Electrónica</li>
 *   <li>coordinador.civil@usco.edu.co · Coordinador de Civil</li>
 *   <li>monitor.civil@usco.edu.co · Monitor de Civil</li>
 * </ul>
 */
@Component
@org.springframework.core.annotation.Order(0)
public class DatosDePrueba implements CommandLineRunner {
    public static final String CLAVE = "Prueba#USCO2026";

    private final UsuarioRepository usuarios;
    private final EquipoRepository equipos;
    private final SedeRepository sedes;
    private final LaboratorioRepository laboratorios;
    private final PasswordEncoder encoder;
    private final Clock clock;
    private final boolean habilitado;

    public DatosDePrueba(UsuarioRepository usuarios, EquipoRepository equipos, SedeRepository sedes, LaboratorioRepository laboratorios,
                         PasswordEncoder encoder, Clock clock, @Value("${labcontrol.seed.habilitado:true}") boolean habilitado) {
        this.usuarios = usuarios; this.equipos = equipos; this.sedes = sedes; this.laboratorios = laboratorios;
        this.encoder = encoder; this.clock = clock; this.habilitado = habilitado;
    }

    private Usuario u(String nombre, String correo, Rol rol, String doc, Sede sede, Laboratorio... labs) {
        Usuario x = new Usuario(nombre, correo, encoder.encode(CLAVE), rol, doc, "Ingeniería");
        if (rol != Rol.ADMINISTRADOR) { x.setSede(sede); x.getLaboratorios().addAll(List.of(labs)); }
        return x;
    }

    @Override
    @Transactional
    public void run(String... args) {
        if (!habilitado || usuarios.count() > 0) return;
        LocalDateTime n = LocalDateTime.now(clock);
        Sede neiva = sedes.findBySlug("neiva").orElseGet(() -> sedes.save(new Sede("neiva", "Sede Neiva", "Neiva", "Av. Pastrana Borrero, Cra. 1", n)));
        Map<String, Laboratorio> labs = new LinkedHashMap<>();
        for (String nombre : List.of("Electrónica", "Petróleos", "Civil", "Agrícola")) {
            labs.put(nombre, laboratorios.findBySedeIdAndNombreIgnoreCase(neiva.getId(), nombre)
                    .orElseGet(() -> laboratorios.save(new Laboratorio(neiva, nombre, null, null, n))));
        }
        usuarios.saveAll(List.of(
                u("Sandra Milena Vargas", "admin@usco.edu.co", Rol.ADMINISTRADOR, "36500100", neiva),
                u("Mauricio Perdomo", "coordinador@usco.edu.co", Rol.COORDINADOR_LAB, "36600200", neiva, labs.get("Electrónica"), labs.get("Petróleos")),
                u("Juan Pablo Trujillo", "monitor@usco.edu.co", Rol.MONITOR, "1075300101", neiva, labs.get("Electrónica")),
                u("Marcela Ortiz", "coordinador.civil@usco.edu.co", Rol.COORDINADOR_LAB, "36600201", neiva, labs.get("Civil")),
                u("Andrés Ramírez", "monitor.civil@usco.edu.co", Rol.MONITOR, "1075300102", neiva, labs.get("Civil"))));

        List<Equipo> inv = catalogo();
        Equipo resistencias = new Equipo("USCO-IN-100", "Resistencias 1 kΩ (bolsa)", null, null, null, "Reactivos", "Electrónica",
                "Bloque de Ingeniería · Laboratorio de Electrónica · Gaveta 3", "Resistencias de 1/4 W para prácticas", null, null, "Bueno", null,
                EstadoEquipo.DISPONIBLE);
        resistencias.definirCantidad(TipoElemento.INSUMO, new BigDecimal("200"), Unidad.UNIDAD, new BigDecimal("200"));
        inv.add(resistencias);
        Equipo alcohol = new Equipo("USCO-IN-101", "Alcohol isopropílico", null, null, null, "Reactivos", "Electrónica",
                "Bloque de Ingeniería · Laboratorio de Electrónica · Armario", "Limpieza de tarjetas", null, null, "Bueno", null,
                EstadoEquipo.DISPONIBLE);
        alcohol.definirCantidad(TipoElemento.INSUMO, new BigDecimal("2"), Unidad.L, new BigDecimal("2"));
        inv.add(alcohol);
        inv.forEach(e -> e.setSede(neiva));
        equipos.saveAll(inv);
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
