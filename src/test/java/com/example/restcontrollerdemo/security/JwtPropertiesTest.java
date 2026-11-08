package com.example.restcontrollerdemo.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class JwtPropertiesTest {

    @Test
    void missingSecretFailsStartup() {
        assertThatThrownBy(() -> new JwtProperties("", "iss", "aud", Duration.ofMinutes(5)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("JWT_SECRET");
    }

    @Test
    void shortSecretFailsStartup() {
        assertThatThrownBy(() -> new JwtProperties("too-short", "iss", "aud", Duration.ofMinutes(5)))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void missingAudienceFailsStartup() {
        assertThatThrownBy(() -> new JwtProperties("x".repeat(32), "iss", " ", null))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void validSecretIsAcceptedWithDefaultTtl() {
        assertThat(new JwtProperties("x".repeat(32), "iss", "aud", null).ttl()).isEqualTo(Duration.ofMinutes(15));
    }
}
