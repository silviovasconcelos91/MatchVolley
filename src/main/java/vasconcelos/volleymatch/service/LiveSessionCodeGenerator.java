package vasconcelos.volleymatch.service;

import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.util.Locale;
import java.util.Optional;

/**
 * Generates and normalizes public live codes: 6 chars from an alphabet without ambiguous glyphs (0/O/1/I/L).
 */
@Component
public class LiveSessionCodeGenerator {

    public static final String ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZ23456789";
    public static final int CODE_LENGTH = 6;

    private final SecureRandom random = new SecureRandom();

    public String generate() {
        StringBuilder sb = new StringBuilder(CODE_LENGTH);
        for (int i = 0; i < CODE_LENGTH; i++) {
            sb.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
        }
        return sb.toString();
    }

    /**
     * Upper-cases the raw code; empty when it cannot be a valid code (avoids a DB round-trip).
     */
    public static Optional<String> normalize(String raw) {
        if (raw == null || raw.length() != CODE_LENGTH) {
            return Optional.empty();
        }
        String upper = raw.toUpperCase(Locale.ROOT);
        for (int i = 0; i < upper.length(); i++) {
            if (ALPHABET.indexOf(upper.charAt(i)) < 0) {
                return Optional.empty();
            }
        }
        return Optional.of(upper);
    }
}
