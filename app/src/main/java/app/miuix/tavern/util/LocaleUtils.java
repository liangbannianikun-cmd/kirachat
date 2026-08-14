package app.miuix.tavern.util;

import java.util.Locale;

public final class LocaleUtils {
    private LocaleUtils() {
    }

    public static boolean isTraditionalChinese(Locale locale) {
        if (locale == null || !"zh".equals(locale.getLanguage())) return false;
        if ("Hant".equalsIgnoreCase(locale.getScript())) return true;
        String country = locale.getCountry();
        return "TW".equalsIgnoreCase(country)
                || "HK".equalsIgnoreCase(country)
                || "MO".equalsIgnoreCase(country);
    }

    public static String languageCode(Locale locale) {
        if (isTraditionalChinese(locale)) return "zh-Hant";
        return locale == null ? "" : locale.getLanguage();
    }

    public static String acceptLanguage(Locale locale) {
        Locale safe = locale == null ? Locale.getDefault() : locale;
        String tag = safe.toLanguageTag();
        String language = safe.getLanguage();
        if (tag == null || tag.isEmpty()) tag = "zh-CN";
        if (language == null || language.isEmpty()) language = "zh";
        return tag + "," + language + ";q=0.9,en;q=0.8";
    }
}
