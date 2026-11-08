package com.example.restcontrollerdemo.security;

import org.springframework.security.oauth2.jwt.Jwt;

/**
 * The caller, as described by its VERIFIED JWT — the only trusted source of "who is asking" and "which tenant". A user
 * or enterprise id read from the path, body or a custom header is client input.
 *
 * @param userId claim {@code sub}
 * @param enterpriseId claim {@value #ENTERPRISE_ID_CLAIM}: the tenant every query is scoped to
 */
public record AuthenticatedUser(long userId, long enterpriseId) {

    public static final String ENTERPRISE_ID_CLAIM = "enterpriseId";

    /** Claim shapes are already checked by {@link SecurityConfig#claimShapeValidator()}. */
    static AuthenticatedUser from(Jwt jwt) {
        Number enterpriseId = jwt.getClaim(ENTERPRISE_ID_CLAIM);
        return new AuthenticatedUser(Long.parseLong(jwt.getSubject()), enterpriseId.longValue());
    }
}
