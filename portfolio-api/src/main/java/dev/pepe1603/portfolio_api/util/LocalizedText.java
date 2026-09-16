package dev.pepe1603.portfolio_api.util;

import java.util.Map;

public final class LocalizedText {

    public static final String DEFAULT_LANG = "es";

    private LocalizedText() {
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