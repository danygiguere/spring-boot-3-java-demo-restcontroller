package com.example.restcontrollerdemo.security;

import com.example.restcontrollerdemo.exception.AppException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

/**
 * The authenticated caller. In Spring MVC the security context is bound to the request thread
 * ({@link SecurityContextHolder}), so this is a plain method call.
 */
@Component
public class CurrentUser {

    public AuthenticatedUser get() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication instanceof JwtAuthenticationToken token) {
            return AuthenticatedUser.from(token.getToken());
        }
        throw new AppException.Unauthorized("error.401");
    }
}
