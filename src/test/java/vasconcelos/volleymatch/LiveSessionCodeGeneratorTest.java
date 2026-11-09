package vasconcelos.volleymatch;

import org.junit.jupiter.api.Test;
import vasconcelos.volleymatch.service.LiveSessionCodeGenerator;

import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class LiveSessionCodeGeneratorTest {

    private final LiveSessionCodeGenerator generator = new LiveSessionCodeGenerator();

    @Test
    void should_generate_six_chars_from_unambiguous_alphabet() {
        for (int i = 0; i < 1_000; i++) {
            assertThat(generator.generate()).matches("^[ABCDEFGHJKMNPQRSTUVWXYZ23456789]{6}$");
        }
    }

    @Test
    void should_generate_distinct_codes() {
        Set<String> codes = new HashSet<>();
        for (int i = 0; i < 1_000; i++) {
            codes.add(generator.generate());
        }
        assertThat(codes).hasSizeGreaterThan(995);
    }

    @Test
    void should_uppercase_when_code_is_lowercase() {
        assertThat(LiveSessionCodeGenerator.normalize("k7p4qx")).contains("K7P4QX");
    }

    @Test
    void should_return_empty_when_code_has_wrong_length_or_forbidden_chars() {
        assertThat(LiveSessionCodeGenerator.normalize("K7P4Q")).isEmpty();
        assertThat(LiveSessionCodeGenerator.normalize("K7P4QO")).isEmpty();
        assertThat(LiveSessionCodeGenerator.normalize("K7P4Q1")).isEmpty();
        assertThat(LiveSessionCodeGenerator.normalize(null)).isEmpty();
    }
}
