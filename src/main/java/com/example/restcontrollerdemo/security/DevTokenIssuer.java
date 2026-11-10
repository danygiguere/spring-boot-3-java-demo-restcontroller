package com.example.restcontrollerdemo.security;

import com.example.restcontrollerdemo.domain.entity.User;
import com.example.restcontrollerdemo.dto.TokenResponse;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import java.net.InetAddress;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.boot.autoconfigure.web.ServerProperties;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.stereotype.Component;

/**
 * Signs tokens for local development only. In production, tokens come from the identity provider; this application
 * only verifies them (see {@link SecurityConfig#jwtDecoder}). Refuses to start unless the server binds to loopback.
 */
@Component
@DevTokenEnabled
public class DevTokenIssuer {

    private final JwtEncoder encoder;
    private final JwtProperties properties;
    private final Clock clock;

    public DevTokenIssuer(JwtProperties properties, Clock clock, ServerProperties serverProperties) {
        InetAddress address = serverProperties.getAddress();
        if (address == null || !address.isLoopbackAddress()) {
            throw new IllegalStateException("DEV_TOKEN_ENABLED requires a loopback SERVER_ADDRESS (127.0.0.1).");
        }
        this.encoder = new NimbusJwtEncoder(new ImmutableSecret<>(SecurityConfig.secretKey(properties)));
        this.properties = properties;
        this.clock = clock;
    }

    public TokenResponse issue(User user) {
        Instant now = clock.instant();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .id(UUID.randomUUID().toString())
                .issuer(properties.issuer())
                .audience(List.of(properties.audience()))
                .subject(user.id().toString())
                .issuedAt(now)
                .expiresAt(now.plus(properties.ttl()))
                .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        String token = encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
        return new TokenResponse(token, "Bearer", properties.ttl().toSeconds());
    }
}
