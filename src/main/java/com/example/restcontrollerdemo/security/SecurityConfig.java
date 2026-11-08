package com.example.restcontrollerdemo.security;

import com.example.restcontrollerdemo.exception.AppException;
import jakarta.servlet.DispatcherType;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimNames;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.web.servlet.HandlerExceptionResolver;

/**
 * Stateless bearer-token API. This filter chain only AUTHENTICATES; whether the caller may touch a given resource
 * (same enterprise, author) is checked in the service at the point of action.
 */
@Configuration
@EnableWebSecurity
@EnableConfigurationProperties(JwtProperties.class)
public class SecurityConfig {

    /** The only routes reachable without a token. Everything else is denied by default. */
    static final String[] PUBLIC_PATHS = {
        // API docs (disabled outside the dev profile)
        "/swagger-ui.html",
        "/swagger-ui/**",
        "/webjars/**",
        "/v3/api-docs/**",
        // Dev token endpoint (exists only in the dev profile)
        "/auth/dev-token"
    };

    private static final Pattern NUMERIC = Pattern.compile("[1-9][0-9]{0,18}");

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http, @Qualifier("handlerExceptionResolver") HandlerExceptionResolver exceptionResolver)
            throws Exception {
        // 401/403 go through the same @RestControllerAdvice as every other error: same RFC 9457 shape, locale and
        // correlationId, although they are raised by a filter, before any controller.
        AuthenticationEntryPoint unauthorized = (request, response, ex) ->
                exceptionResolver.resolveException(request, response, null, new AppException.Unauthorized("error.401"));
        AccessDeniedHandler forbidden = (request, response, ex) ->
                exceptionResolver.resolveException(request, response, null, new AppException.Forbidden("error.403"));

        return http
                // Bearer tokens, no cookies: the browser never attaches credentials on its own, so CSRF does not apply.
                .csrf(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(requests -> requests.requestMatchers(PUBLIC_PATHS)
                        .permitAll()
                        // Error dispatches (forwarded by the container to /error) carry no token of their own.
                        .dispatcherTypeMatchers(DispatcherType.ERROR)
                        .permitAll()
                        .anyRequest()
                        .authenticated())
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(Customizer.withDefaults())
                        .authenticationEntryPoint(unauthorized)
                        .accessDeniedHandler(forbidden))
                .exceptionHandling(handling ->
                        handling.authenticationEntryPoint(unauthorized).accessDeniedHandler(forbidden))
                .build();
    }

    /**
     * Verifies the signature with the pinned algorithm (HS256 only — the token's own {@code alg} header is not
     * trusted), then {@code exp} (required), {@code nbf}, {@code iss}, {@code aud}, and the shape of the claims this
     * application reads. Any failure is a 401.
     */
    @Bean
    public JwtDecoder jwtDecoder(JwtProperties properties) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(secretKey(properties))
                .macAlgorithm(MacAlgorithm.HS256)
                .build();
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                JwtValidators.createDefaultWithIssuer(properties.issuer()),
                new JwtClaimValidator<>(JwtClaimNames.EXP, Objects::nonNull),
                new JwtClaimValidator<List<String>>(
                        JwtClaimNames.AUD, aud -> aud != null && aud.contains(properties.audience())),
                claimShapeValidator()));
        return decoder;
    }

    /** {@code sub} is a positive user id and {@code enterpriseId} a number: a malformed claim is a 401, not a 500. */
    static OAuth2TokenValidator<Jwt> claimShapeValidator() {
        return jwt -> {
            String subject = jwt.getSubject();
            Object enterpriseId = jwt.getClaims().get(AuthenticatedUser.ENTERPRISE_ID_CLAIM);
            boolean valid = subject != null
                    && NUMERIC.matcher(subject).matches()
                    && enterpriseId instanceof Number number
                    && number.longValue() > 0;
            return valid
                    ? OAuth2TokenValidatorResult.success()
                    : OAuth2TokenValidatorResult.failure(
                            new OAuth2Error("invalid_token", "Missing or malformed sub/enterpriseId claim", null));
        };
    }

    static SecretKey secretKey(JwtProperties properties) {
        return new SecretKeySpec(properties.secretBytes(), "HmacSHA256");
    }
}
