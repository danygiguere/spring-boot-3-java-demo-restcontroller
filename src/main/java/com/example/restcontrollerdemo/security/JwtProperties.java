package com.example.restcontrollerdemo.security;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * JWT settings ({@code app.jwt.*}). Validated at startup: a missing or short key stops the application instead of
 * producing forgeable tokens.
 */
@ConfigurationProperties("app.jwt")
public record JwtProperties(String secret, String issuer, String audience, Duration ttl) {

    /** HS256 requires a key of at least 256 bits. */
    static final int MIN_SECRET_BYTES = 32;

    public JwtProperties {
        if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < MIN_SECRET_BYTES) {
            throw new IllegalStateException(
                    "app.jwt.secret (env JWT_SECRET) must be at least " + MIN_SECRET_BYTES + " bytes.");
        }
        if (issuer == null || issuer.isBlank()) {
            throw new IllegalStateException("app.jwt.issuer must be set.");
        }
        if (audience == null || audience.isBlank()) {
            throw new IllegalStateException("app.jwt.audience must be set.");
        }
        if (ttl == null) {
            ttl = Duration.ofMinutes(15);
        }
    }

    byte[] secretBytes() {
        return secret.getBytes(StandardCharsets.UTF_8);
    }
}
