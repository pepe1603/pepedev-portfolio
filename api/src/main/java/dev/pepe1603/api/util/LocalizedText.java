package dev.pepe1603.api.util;

import java.util.Locale;
import java.util.Map;

public final class LocalizedText {

    public static final String DEFAULT_LANG = "es";

    private LocalizedText() {
    }

    /**
     * El proyecto solo tiene español e inglés, así que cualquier otro idioma cae en español en vez
     * de dejar la cadena vacía.
     */
    public static Locale toLocale(Locale locale) {
        return Locale.forLanguageTag(normalizeLang(locale != null ? locale.getLanguage() : null));
    }

    public static String resolve(Map<String, String> text, String lang) {
        if (text == null || text.isEmpty()) {
            return null;
        }
        String normalized = normalizeLang(lang);
        String value = text.get(normalized);
        if (value != null) {
            return value;
        }
        return text.get(opposite(normalized));
    }

    public static String normalizeLang(String lang) {
        if ("en".equalsIgnoreCase(lang)) {
            return "en";
        }
        return DEFAULT_LANG;
    }

    private static String opposite(String lang) {
        return lang.equals(DEFAULT_LANG) ? "en" : DEFAULT_LANG;
    }
}