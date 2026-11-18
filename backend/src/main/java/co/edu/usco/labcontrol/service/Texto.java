package co.edu.usco.labcontrol.service;

import co.edu.usco.labcontrol.web.ApiException;
import java.text.Normalizer;
import java.util.Locale;

/** Utilidades de texto: búsquedas sin tildes, limpieza y validación de longitudes. */
public final class Texto {
    private Texto() {}

    public static String normalizar(String s) {
        if (s == null) return "";
        return Normalizer.normalize(s, Normalizer.Form.NFD).replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT).trim();
    }

    /** null si viene vacío; si no, el texto sin espacios sobrantes. */
    public static String limpio(String s) { return s == null || s.isBlank() ? null : s.trim(); }

    /** Texto opcional con longitud máxima. */
    public static String opcional(String s, int max, String campo) {
        String t = limpio(s);
        if (t != null && t.length() > max) throw ApiException.badRequest(campo + " admite máximo " + max + " caracteres");
        return t;
    }

    /** Texto obligatorio entre min y max caracteres. */
    public static String obligatorio(String s, int min, int max, String campo) {
        String t = s == null ? "" : s.trim();
        if (t.length() < min) throw ApiException.badRequest(min <= 1 ? campo + " es obligatorio" : campo + " debe tener al menos " + min + " caracteres");
        if (t.length() > max) throw ApiException.badRequest(campo + " admite máximo " + max + " caracteres");
        return t;
    }

    public static boolean vacio(String s) { return s == null || s.isBlank() || s.equalsIgnoreCase("todos"); }

    /** Escapa una celda CSV (separador ;) y neutraliza fórmulas de Excel (=, +, -, @). */
    public static String celda(Object v) {
        if (v == null) return "";
        String s = v.toString();
        if (!s.isEmpty() && "=+-@".indexOf(s.charAt(0)) >= 0) s = "'" + s;
        return s.contains(";") || s.contains("\"") || s.contains("\n") || s.contains("\r") ? "\"" + s.replace("\"", "\"\"") + "\"" : s;
    }
}
