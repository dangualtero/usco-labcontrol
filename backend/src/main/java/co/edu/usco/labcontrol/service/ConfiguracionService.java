package co.edu.usco.labcontrol.service;

import co.edu.usco.labcontrol.domain.Catalogo;
import co.edu.usco.labcontrol.repo.CatalogoRepository;
import co.edu.usco.labcontrol.web.ApiException;
import co.edu.usco.labcontrol.web.Dtos.*;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Parametrización del Tablero de control: catálogos (listas desplegables), parámetros (umbrales de alertas y plazos) y
 * plantillas de correo. Los valores por defecto se crean al arrancar ({@code ConfiguracionInicial}).
 */
@Service
public class ConfiguracionService {
    public record Parametro(String clave, String valor, String descripcion) {}
    public record Plantilla(String codigo, String nombre, String asunto, String cuerpo) {}

    public static final String UMBRAL_STOCK = "alertas.umbral_stock";
    public static final String DIAS_MANTENIMIENTO = "alertas.dias_mantenimiento";
    public static final String DIAS_NOVEDAD = "alertas.dias_novedad_sin_validar";
    public static final String DIAS_SOLICITUD = "alertas.dias_solicitud_sin_decision";

    /** Parámetros con su valor por defecto. */
    public static final List<Parametro> PARAMETROS = List.of(
            new Parametro(UMBRAL_STOCK, "25", "Porcentaje de existencias (respecto a la referencia) con el que un insumo entra en alerta"),
            new Parametro(DIAS_MANTENIMIENTO, "7", "Días de anticipación para alertar un mantenimiento programado"),
            new Parametro(DIAS_NOVEDAD, "2", "Días sin validar tras los cuales una novedad pasa a alerta crítica"),
            new Parametro(DIAS_SOLICITUD, "10", "Días que puede esperar una solicitud enviada sin decisión antes de alertar al Administrador"));

    private static final String PIE = "\n\nAbrir en USCO LabControl: {{enlace}}\n\nEste es un mensaje automático de USCO LabControl.";
    /** Plantillas de correo con su texto por defecto. */
    public static final List<Plantilla> PLANTILLAS = List.of(
            new Plantilla("NOVEDAD_POR_VALIDAR", "Novedad reportada por un monitor (al coordinador)",
                    "[LabControl] Novedad por validar · {{placa}} · {{laboratorio}}",
                    "Hola,\n\n{{reportadoPor}} reportó una novedad que necesita tu validación:\n\nElemento: {{placa}} · {{equipo}}\nLaboratorio: {{laboratorio}}\nTipo: {{tipo}}\nDescripción: {{descripcion}}" + PIE),
            new Plantilla("SOLICITUD_EN_REVISION", "Solicitud preparada por un monitor (al coordinador)",
                    "[LabControl] Solicitud {{codigo}} por validar · {{laboratorio}}",
                    "Hola,\n\n{{autor}} preparó la solicitud {{codigo}} «{{titulo}}» para el laboratorio {{laboratorio}}. Revísala, ajústala si hace falta y envíala al Administrador." + PIE),
            new Plantilla("SOLICITUD_ENVIADA", "Solicitud enviada (al Administrador)",
                    "[LabControl] Nueva solicitud {{codigo}} · {{laboratorio}}",
                    "Hola,\n\nEl laboratorio {{laboratorio}} envió la solicitud {{codigo}} «{{titulo}}».\nTipo: {{tipo}}\nPrioridad propuesta: {{prioridad}}\nValor estimado: {{valor}}" + PIE),
            new Plantilla("SOLICITUD_DEVUELTA", "Solicitud devuelta para ajuste",
                    "[LabControl] Solicitud {{codigo}} devuelta para ajuste",
                    "Hola,\n\nLa solicitud {{codigo}} «{{titulo}}» fue devuelta para ajuste por {{autor}}.\n\nObservación: {{comentario}}" + PIE),
            new Plantilla("SOLICITUD_DECIDIDA", "Decisión sobre una solicitud",
                    "[LabControl] Solicitud {{codigo}}: {{estado}}",
                    "Hola,\n\nLa solicitud {{codigo}} «{{titulo}}» del laboratorio {{laboratorio}} fue {{estado}}.\n\nMotivo: {{comentario}}" + PIE),
            new Plantilla("SOLICITUD_CERRADA", "Cierre de una solicitud",
                    "[LabControl] Solicitud {{codigo}} cerrada",
                    "Hola,\n\nLa solicitud {{codigo}} «{{titulo}}» quedó cerrada.\n\nNota de cierre: {{comentario}}" + PIE),
            new Plantilla("MANTENIMIENTO_PROGRAMADO", "Mantenimiento programado (a coordinadores y monitores del laboratorio)",
                    "[LabControl] Mantenimiento {{codigo}} programado · {{placa}}",
                    "Hola,\n\nSe programó el mantenimiento {{codigo}} ({{tipo}}) del elemento {{placa}} · {{equipo}} en {{laboratorio}} para el {{fecha}}.\nResponsable: {{responsable}}" + PIE),
            new Plantilla("COMPROMISO_ASIGNADO", "Compromiso asignado (al responsable)",
                    "[LabControl] Nuevo compromiso: {{titulo}}",
                    "Hola,\n\nSe te asignó el compromiso «{{titulo}}» ({{laboratorio}}) con fecha límite {{fecha}}.\n\n{{descripcion}}" + PIE));

    /** Valores iniciales de cada catálogo. */
    public static final Map<Catalogo.Tipo, List<String>> CATALOGOS = new LinkedHashMap<>();
    static {
        CATALOGOS.put(Catalogo.Tipo.CATEGORIA_EQUIPO, List.of("Equipos de medición", "Fuentes y generadores", "Osciloscopios", "Multímetros",
                "Soldadura", "Análisis de fluidos", "Topografía", "Ensayos de materiales", "Reactivos", "Materiales de construcción",
                "Cableado", "Protección personal", "Otro"));
        CATALOGOS.put(Catalogo.Tipo.ESTADO_FISICO, List.of("Excelente", "Bueno", "Regular", "Deteriorado"));
        CATALOGOS.put(Catalogo.Tipo.TIPO_SOLICITUD, List.of("Compra de equipos", "Insumos y reactivos", "Mantenimiento o calibración",
                "Repuestos", "Software y licencias", "Adecuación de infraestructura", "Servicio técnico", "Otro"));
        CATALOGOS.put(Catalogo.Tipo.TIPO_DOCUMENTO, List.of("Manual de uso", "Guía o protocolo de práctica", "Ficha técnica",
                "Certificado de calibración", "Plan de mantenimiento", "Inventario o acta", "Evidencia fotográfica",
                "Normas de seguridad", "Otro"));
        CATALOGOS.put(Catalogo.Tipo.FUENTE_FINANCIACION, List.of("Recursos propios", "Estampilla Pro-Universidad", "Proyecto de investigación",
                "Regalías", "Convenio", "Otro"));
        CATALOGOS.put(Catalogo.Tipo.MODALIDAD_CONTRATACION, List.of("Contratación directa", "Mínima cuantía", "Invitación pública",
                "Orden de compra", "Convenio", "Otro"));
    }

    private static final Pattern VARIABLE = Pattern.compile("\\{\\{\\s*([a-zA-Z]+)\\s*}}");

    private final CatalogoRepository catalogos;
    private final JdbcTemplate jdbc;

    public ConfiguracionService(CatalogoRepository catalogos, JdbcTemplate jdbc) { this.catalogos = catalogos; this.jdbc = jdbc; }

    // ------------------------------------------------------------------ Catálogos

    @Transactional(readOnly = true)
    public List<CatalogoDto> catalogos(String tipo) {
        List<Catalogo> l = tipo == null || tipo.isBlank() ? catalogos.findAllByOrderByTipoAscOrdenAscValorAsc()
                : catalogos.findByTipoOrderByOrdenAscValorAsc(tipoCatalogo(tipo));
        return l.stream().map(ConfiguracionService::dto).toList();
    }

    /** Valores activos de un catálogo (para los formularios). */
    @Transactional(readOnly = true)
    public List<String> valores(Catalogo.Tipo tipo) {
        return catalogos.findByTipoOrderByOrdenAscValorAsc(tipo).stream().filter(Catalogo::isActivo).map(Catalogo::getValor).toList();
    }

    /** Valida que el valor exista y esté activo en el catálogo; devuelve el valor tal como está registrado. */
    @Transactional(readOnly = true)
    public String validar(Catalogo.Tipo tipo, String valor, String campo) {
        String v = valor == null ? "" : valor.trim();
        if (v.isEmpty()) throw ApiException.badRequest("Selecciona " + campo);
        return catalogos.findByTipoAndValorIgnoreCase(tipo, v).filter(Catalogo::isActivo).map(Catalogo::getValor)
                .orElseThrow(() -> ApiException.badRequest("«" + v + "» no está en el catálogo de " + tipo.texto().toLowerCase()
                        + ". Agrégalo en Parametrización → Catálogos"));
    }

    @Transactional
    public CatalogoDto crearCatalogo(CatalogoRequest r) {
        Catalogo.Tipo t = tipoCatalogo(r.tipo());
        String v = valorCatalogo(r.valor());
        if (catalogos.findByTipoAndValorIgnoreCase(t, v).isPresent()) throw ApiException.conflict("«" + v + "» ya existe en " + t.texto());
        return dto(catalogos.save(new Catalogo(t, v, r.orden() == null ? 100 : r.orden())));
    }

    @Transactional
    public CatalogoDto actualizarCatalogo(Long id, CatalogoRequest r) {
        Catalogo c = catalogos.findById(id).orElseThrow(() -> ApiException.notFound("Valor de catálogo no encontrado"));
        if (r.valor() != null) {
            String v = valorCatalogo(r.valor());
            catalogos.findByTipoAndValorIgnoreCase(c.getTipo(), v).filter(o -> !o.getId().equals(id))
                    .ifPresent(o -> { throw ApiException.conflict("«" + v + "» ya existe en " + c.getTipo().texto()); });
            c.setValor(v);
        }
        if (r.orden() != null) c.setOrden(r.orden());
        if (r.activo() != null) c.setActivo(r.activo());
        return dto(c);
    }

    private static CatalogoDto dto(Catalogo c) {
        return new CatalogoDto(c.getId(), c.getTipo().name(), c.getTipo().texto(), c.getValor(), c.getOrden(), c.isActivo());
    }

    private static Catalogo.Tipo tipoCatalogo(String s) {
        try { return Catalogo.Tipo.valueOf(s == null ? "" : s.trim().toUpperCase()); }
        catch (IllegalArgumentException e) { throw ApiException.badRequest("Tipo de catálogo no válido"); }
    }

    private static String valorCatalogo(String s) {
        String v = s == null ? "" : s.trim().replaceAll("\\s+", " ");
        if (v.length() < 2 || v.length() > 80) throw ApiException.badRequest("El valor debe tener entre 2 y 80 caracteres");
        if (v.contains("|")) throw ApiException.badRequest("El valor no puede contener |");
        return v;
    }

    // ------------------------------------------------------------------ Parámetros

    public List<ParametroDto> parametros() {
        return PARAMETROS.stream().map(p -> new ParametroDto(p.clave(), parametro(p.clave()), p.descripcion())).toList();
    }

    public String parametro(String clave) {
        List<String> v = jdbc.queryForList("SELECT valor FROM parametros WHERE clave = ?", String.class, clave);
        if (!v.isEmpty()) return v.get(0);
        return PARAMETROS.stream().filter(p -> p.clave().equals(clave)).map(Parametro::valor).findFirst().orElse("");
    }

    public int entero(String clave) {
        try { return Integer.parseInt(parametro(clave).trim()); }
        catch (NumberFormatException e) {
            return Integer.parseInt(PARAMETROS.stream().filter(p -> p.clave().equals(clave)).map(Parametro::valor).findFirst().orElse("0"));
        }
    }

    @Transactional
    public List<ParametroDto> cambiarParametro(String clave, ParametroRequest r) {
        Parametro p = PARAMETROS.stream().filter(x -> x.clave().equals(clave)).findFirst()
                .orElseThrow(() -> ApiException.notFound("Parámetro no encontrado"));
        String v = r.valor() == null ? "" : r.valor().trim();
        int n;
        try { n = Integer.parseInt(v); } catch (NumberFormatException e) { throw ApiException.badRequest("Escribe un número entero"); }
        if (UMBRAL_STOCK.equals(clave) && (n < 1 || n > 99)) throw ApiException.badRequest("El umbral debe estar entre 1 y 99 %");
        if (!UMBRAL_STOCK.equals(clave) && (n < 0 || n > 365)) throw ApiException.badRequest("Escribe un número de días entre 0 y 365");
        if (jdbc.update("UPDATE parametros SET valor = ? WHERE clave = ?", String.valueOf(n), clave) == 0) {
            jdbc.update("INSERT INTO parametros (clave, valor, descripcion) VALUES (?, ?, ?)", clave, String.valueOf(n), p.descripcion());
        }
        return parametros();
    }

    // ------------------------------------------------------------------ Plantillas

    public List<PlantillaDto> plantillas() {
        List<PlantillaDto> out = new ArrayList<>();
        for (Plantilla d : PLANTILLAS) {
            var filas = jdbc.queryForList("SELECT asunto, cuerpo, activa FROM plantillas WHERE codigo = ?", d.codigo());
            String asunto = filas.isEmpty() ? d.asunto() : (String) filas.get(0).get("asunto");
            String cuerpo = filas.isEmpty() ? d.cuerpo() : (String) filas.get(0).get("cuerpo");
            boolean activa = filas.isEmpty() || Boolean.TRUE.equals(booleano(filas.get(0).get("activa")));
            out.add(new PlantillaDto(d.codigo(), d.nombre(), asunto, cuerpo, activa, variables(d.asunto() + " " + d.cuerpo())));
        }
        return out;
    }

    public PlantillaDto plantilla(String codigo) {
        return plantillas().stream().filter(p -> p.codigo().equals(codigo)).findFirst()
                .orElseThrow(() -> ApiException.notFound("Plantilla no encontrada"));
    }

    @Transactional
    public PlantillaDto cambiarPlantilla(String codigo, PlantillaRequest r) {
        Plantilla d = PLANTILLAS.stream().filter(p -> p.codigo().equals(codigo)).findFirst()
                .orElseThrow(() -> ApiException.notFound("Plantilla no encontrada"));
        PlantillaDto actual = plantilla(codigo);
        String asunto = r.asunto() == null ? actual.asunto() : r.asunto().trim();
        String cuerpo = r.cuerpo() == null ? actual.cuerpo() : r.cuerpo().trim();
        boolean activa = r.activa() == null ? actual.activa() : r.activa();
        if (asunto.length() < 5 || asunto.length() > 200) throw ApiException.badRequest("El asunto debe tener entre 5 y 200 caracteres");
        if (cuerpo.length() < 10 || cuerpo.length() > 4000) throw ApiException.badRequest("El cuerpo debe tener entre 10 y 4000 caracteres");
        if (jdbc.update("UPDATE plantillas SET asunto = ?, cuerpo = ?, activa = ? WHERE codigo = ?", asunto, cuerpo, activa, codigo) == 0) {
            jdbc.update("INSERT INTO plantillas (codigo, nombre, asunto, cuerpo, activa) VALUES (?, ?, ?, ?, ?)", codigo, d.nombre(), asunto, cuerpo, activa);
        }
        return plantilla(codigo);
    }

    /** Reemplaza las variables {{nombre}} por sus valores (las que no existen quedan vacías). */
    public static String llenar(String texto, Map<String, String> vars) {
        Matcher m = VARIABLE.matcher(texto == null ? "" : texto);
        StringBuilder sb = new StringBuilder();
        while (m.find()) m.appendReplacement(sb, Matcher.quoteReplacement(vars.getOrDefault(m.group(1), "")));
        m.appendTail(sb);
        return sb.toString();
    }

    private static List<String> variables(String texto) {
        List<String> out = new ArrayList<>();
        Matcher m = VARIABLE.matcher(texto);
        while (m.find()) if (!out.contains(m.group(1))) out.add(m.group(1));
        return out;
    }

    private static Boolean booleano(Object o) {
        if (o instanceof Boolean b) return b;
        if (o instanceof Number n) return n.intValue() != 0;
        return o != null && Boolean.parseBoolean(o.toString());
    }
}
