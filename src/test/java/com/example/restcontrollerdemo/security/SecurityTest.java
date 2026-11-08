package com.example.restcontrollerdemo.security;

import com.example.restcontrollerdemo.support.IntegrationTest;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

class SecurityTest extends IntegrationTest {

    @Autowired
    JwtProperties properties;

    @Test
    void missingToken_returns401ProblemWithCorrelationId() {
        webTestClient
                .get()
                .uri("/users/me")
                .exchange()
                .expectStatus()
                .isUnauthorized()
                .expectHeader()
                .exists("X-Correlation-Id")
                .expectBody()
                .jsonPath("$.status")
                .isEqualTo(401)
                .jsonPath("$.correlationId")
                .isNotEmpty();
    }

    @Test
    void validToken_isAccepted() {
        expectStatus(signed(claims -> {}), 200);
    }

    @Test
    void tokenSignedWithAnotherKey_returns401() {
        String otherKey = "another-key-that-is-at-least-32-bytes-long!";
        expectStatus(sign(otherKey, baseClaims().build()), 401);
    }

    @Test
    void tokenForAnotherAudience_returns401() {
        expectStatus(signed(claims -> claims.audience(List.of("another-api"))), 401);
    }

    @Test
    void tokenFromAnotherIssuer_returns401() {
        expectStatus(signed(claims -> claims.issuer("someone-else")), 401);
    }

    @Test
    void expiredToken_returns401() {
        expectStatus(
                signed(claims -> claims.issuedAt(Instant.now().minusSeconds(7200))
                        .expiresAt(Instant.now().minusSeconds(3600))),
                401);
    }

    @Test
    void tokenWithoutExpiry_returns401() {
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(properties.issuer())
                .audience(List.of(properties.audience()))
                .subject("1")
                .claim(AuthenticatedUser.ENTERPRISE_ID_CLAIM, 1)
                .build();
        expectStatus(sign(properties.secret(), claims), 401);
    }

    @Test
    void tokenWithMalformedSubject_returns401NotServerError() {
        expectStatus(signed(claims -> claims.subject("alice")), 401);
    }

    @Test
    void unsignedAlgNoneToken_returns401() {
        Base64.Encoder base64 = Base64.getUrlEncoder().withoutPadding();
        String header = base64.encodeToString("{\"alg\":\"none\"}".getBytes(StandardCharsets.UTF_8));
        String payload =
                base64.encodeToString(("{\"sub\":\"1\",\"enterpriseId\":1,\"iss\":\"%s\",\"aud\":\"%s\",\"exp\":%d}")
                        .formatted(
                                properties.issuer(),
                                properties.audience(),
                                Instant.now().plusSeconds(600).getEpochSecond())
                        .getBytes(StandardCharsets.UTF_8));
        expectStatus(header + "." + payload + ".", 401);
    }

    @Test
    void devToken_unknownEmail_returns401WithoutRevealingWhy() {
        webTestClient
                .post()
                .uri("/auth/dev-token")
                .bodyValue(Map.of("email", "nobody@example.com"))
                .exchange()
                .expectStatus()
                .isUnauthorized()
                .expectBody()
                .jsonPath("$.type")
                .isEqualTo("/errors/auth-invalid");
    }

    @Test
    void responses_carrySecurityHeaders() {
        as(ALICE)
                .get()
                .uri("/users/me")
                .exchange()
                .expectStatus()
                .isOk()
                .expectHeader()
                .valueEquals("X-Content-Type-Options", "nosniff")
                .expectHeader()
                .exists("Cache-Control");
    }

    private void expectStatus(String token, int status) {
        webTestClient
                .get()
                .uri("/users/me")
                .header("Authorization", "Bearer " + token)
                .exchange()
                .expectStatus()
                .isEqualTo(status);
    }

    private JwtClaimsSet.Builder baseClaims() {
        Instant now = Instant.now();
        return JwtClaimsSet.builder()
                .issuer(properties.issuer())
                .audience(List.of(properties.audience()))
                .subject("1")
                .issuedAt(now)
                .expiresAt(now.plusSeconds(600))
                .claim(AuthenticatedUser.ENTERPRISE_ID_CLAIM, 1);
    }

    private String signed(Consumer<JwtClaimsSet.Builder> customizer) {
        JwtClaimsSet.Builder claims = baseClaims();
        customizer.accept(claims);
        return sign(properties.secret(), claims.build());
    }

    private static String sign(String secret, JwtClaimsSet claims) {
        byte[] key = secret.getBytes(StandardCharsets.UTF_8);
        NimbusJwtEncoder encoder = new NimbusJwtEncoder(new ImmutableSecret<>(key));
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        return encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }
}
