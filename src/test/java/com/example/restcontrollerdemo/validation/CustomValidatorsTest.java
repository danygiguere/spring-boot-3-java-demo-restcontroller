package com.example.restcontrollerdemo.validation;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Plain unit tests: no Spring context, no database. */
class CustomValidatorsTest {

    private final Validator validator =
            Validation.buildDefaultValidatorFactory().getValidator();

    record Sample(@SafeText String text, @NoDuplicates List<String> items) {}

    private boolean valid(String text, List<String> items) {
        return validator.validate(new Sample(text, items)).isEmpty();
    }

    @Test
    void safeText_acceptsPlainTextAndNull() {
        assertThat(valid("Hello, 1 < 2 and 3 > 2!\nSecond line\tTabbed", null)).isTrue();
        assertThat(valid(null, null)).isTrue();
    }

    @Test
    void safeText_rejectsHtmlAndControlCharacters() {
        assertThat(valid("<script>alert(1)</script>", null)).isFalse();
        assertThat(valid("hello <b>world</b>", null)).isFalse();
        assertThat(valid("<!-- comment -->", null)).isFalse();
        assertThat(valid("null\u0000byte", null)).isFalse();
    }

    @Test
    void noDuplicates_rejectsRepeatedElements() {
        assertThat(valid("ok", List.of("a", "b"))).isTrue();
        assertThat(valid("ok", List.of())).isTrue();
        assertThat(valid("ok", Arrays.asList("a", "b", "a"))).isFalse();
    }

    @Test
    void violations_carryTheMessageTemplates() {
        var messages = validator.validate(new Sample("<b>", List.of("a", "a"))).stream()
                .map(v -> v.getMessageTemplate())
                .toList();
        assertThat(messages).containsExactlyInAnyOrder("{validation.safeText}", "{validation.noDuplicates}");
    }
}
