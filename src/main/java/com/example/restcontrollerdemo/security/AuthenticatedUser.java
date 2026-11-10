package com.example.restcontrollerdemo.security;

import org.springframework.security.oauth2.jwt.Jwt;

/**
 * The caller, as described by its VERIFIED JWT — the only trusted source of "who is asking". A user id read from the
 * path, body or a custom header is client input.
 *
 * @param userId claim {@code sub}
 */
public record AuthenticatedUser(long userId) {

    /** The claim shape is already checked by {@link SecurityConfig#claimShapeValidator()}. */
    static AuthenticatedUser from(Jwt jwt) {
        return new AuthenticatedUser(Long.parseLong(jwt.getSubject()));
    }
}
