package com.africe.backend.common.util;

import java.util.LinkedHashMap;
import java.util.Map;

public final class SlugUtils {

    private static final Map<String, String> TRANSLITERATION = new LinkedHashMap<>();

    static {
        // Multi-character mappings first (order matters)
        TRANSLITERATION.put("зг", "zgh");
        TRANSLITERATION.put("Зг", "Zgh");
        TRANSLITERATION.put("ЗГ", "ZGH");

        // Ukrainian Cyrillic → Latin (standard Ukrainian transliteration)
        TRANSLITERATION.put("а", "a");
        TRANSLITERATION.put("б", "b");
        TRANSLITERATION.put("в", "v");
        TRANSLITERATION.put("г", "h");
        TRANSLITERATION.put("ґ", "g");
        TRANSLITERATION.put("д", "d");
        TRANSLITERATION.put("е", "e");
        TRANSLITERATION.put("є", "ye");
        TRANSLITERATION.put("ж", "zh");
        TRANSLITERATION.put("з", "z");
        TRANSLITERATION.put("и", "y");
        TRANSLITERATION.put("і", "i");
        TRANSLITERATION.put("ї", "yi");
        TRANSLITERATION.put("й", "y");
        TRANSLITERATION.put("к", "k");
        TRANSLITERATION.put("л", "l");
        TRANSLITERATION.put("м", "m");
        TRANSLITERATION.put("н", "n");
        TRANSLITERATION.put("о", "o");
        TRANSLITERATION.put("п", "p");
        TRANSLITERATION.put("р", "r");
        TRANSLITERATION.put("с", "s");
        TRANSLITERATION.put("т", "t");
        TRANSLITERATION.put("у", "u");
        TRANSLITERATION.put("ф", "f");
        TRANSLITERATION.put("х", "kh");
        TRANSLITERATION.put("ц", "ts");
        TRANSLITERATION.put("ч", "ch");
        TRANSLITERATION.put("ш", "sh");
        TRANSLITERATION.put("щ", "shch");
        TRANSLITERATION.put("ь", "");
        TRANSLITERATION.put("ю", "yu");
        TRANSLITERATION.put("я", "ya");

        // Uppercase
        TRANSLITERATION.put("А", "A");
        TRANSLITERATION.put("Б", "B");
        TRANSLITERATION.put("В", "V");
        TRANSLITERATION.put("Г", "H");
        TRANSLITERATION.put("Ґ", "G");
        TRANSLITERATION.put("Д", "D");
        TRANSLITERATION.put("Е", "E");
        TRANSLITERATION.put("Є", "Ye");
        TRANSLITERATION.put("Ж", "Zh");
        TRANSLITERATION.put("З", "Z");
        TRANSLITERATION.put("И", "Y");
        TRANSLITERATION.put("І", "I");
        TRANSLITERATION.put("Ї", "Yi");
        TRANSLITERATION.put("Й", "Y");
        TRANSLITERATION.put("К", "K");
        TRANSLITERATION.put("Л", "L");
        TRANSLITERATION.put("М", "M");
        TRANSLITERATION.put("Н", "N");
        TRANSLITERATION.put("О", "O");
        TRANSLITERATION.put("П", "P");
        TRANSLITERATION.put("Р", "R");
        TRANSLITERATION.put("С", "S");
        TRANSLITERATION.put("Т", "T");
        TRANSLITERATION.put("У", "U");
        TRANSLITERATION.put("Ф", "F");
        TRANSLITERATION.put("Х", "Kh");
        TRANSLITERATION.put("Ц", "Ts");
        TRANSLITERATION.put("Ч", "Ch");
        TRANSLITERATION.put("Ш", "Sh");
        TRANSLITERATION.put("Щ", "Shch");
        TRANSLITERATION.put("Ь", "");
        TRANSLITERATION.put("Ю", "Yu");
        TRANSLITERATION.put("Я", "Ya");
    }

    private SlugUtils() {
    }

    public static String toSlug(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }

        String result = text;
        for (Map.Entry<String, String> entry : TRANSLITERATION.entrySet()) {
            result = result.replace(entry.getKey(), entry.getValue());
        }

        return result.toLowerCase()
                .replaceAll("[^a-z0-9\\s-]", "")
                .replaceAll("[\\s-]+", "-")
                .replaceAll("^-|-$", "");
    }
}
