package space.qu4nt.thirdparty.mcpainter.ui;

import java.util.Locale;
import java.util.Optional;

enum Language {
    KOREAN("ko", Locale.KOREAN),
    ENGLISH("en", Locale.ENGLISH);

    static final Language DEFAULT = KOREAN;

    private final String code;
    private final Locale locale;

    Language(String code, Locale locale) {
        this.code = code;
        this.locale = locale;
    }

    String code() {
        return code;
    }

    Locale locale() {
        return locale;
    }

    String messageKey() {
        return "language." + code;
    }

    static Optional<Language> fromCode(String code) {
        for (Language language : values()) {
            if (language.code.equals(code)) {
                return Optional.of(language);
            }
        }
        return Optional.empty();
    }
}
