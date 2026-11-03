package space.qu4nt.thirdparty.mcpainter.ui;

import java.security.SecureRandom;
import java.util.random.RandomGenerator;

final class Seeds {

    private static final RandomGenerator RANDOM = new SecureRandom();

    private Seeds() {
    }

    static long random() {
        return RANDOM.nextLong();
    }

    static long parse(String text, RandomGenerator random) {
        String trimmed = text == null ? "" : text.strip();
        if (trimmed.isEmpty()) {
            return random.nextLong();
        }
        try {
            return Long.parseLong(trimmed);
        } catch (NumberFormatException e) {
            return trimmed.hashCode();
        }
    }

    static long parse(String text) {
        return parse(text, RANDOM);
    }
}
