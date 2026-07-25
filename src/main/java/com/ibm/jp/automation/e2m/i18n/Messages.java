package com.ibm.jp.automation.e2m.i18n;

import java.text.MessageFormat;
import java.util.Locale;
import java.util.ResourceBundle;

public final class Messages {

    private static final String BUNDLE_NAME = "messages";
    private static final Locale ENGLISH = Locale.ENGLISH;

    private Messages() {
    }

    public static String get(String key, Object... args) {
        String pattern = bundle().getString(key);
        if (args.length == 0) {
            return pattern;
        }
        return MessageFormat.format(pattern, args);
    }

    public static String localeName() {
        return isJapanese() ? "ja" : "en";
    }

    private static ResourceBundle bundle() {
        Locale locale = isJapanese() ? Locale.JAPANESE : ENGLISH;
        return ResourceBundle.getBundle(BUNDLE_NAME, locale);
    }

    private static boolean isJapanese() {
        return Locale.JAPANESE.getLanguage().equals(defaultLocale().getLanguage());
    }

    private static Locale defaultLocale() {
        Locale locale = Locale.getDefault();
        if (locale == null) {
            return ENGLISH;
        }
        return locale;
    }
}
