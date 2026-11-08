package com.example.restcontrollerdemo.security;

import org.springframework.core.MethodParameter;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

/** Lets controller methods declare {@code AuthenticatedUser me}: the caller from the verified JWT, or a 401. */
@Component
public class AuthenticatedUserArgumentResolver implements HandlerMethodArgumentResolver {

    private final CurrentUser currentUser;

    public AuthenticatedUserArgumentResolver(CurrentUser currentUser) {
        this.currentUser = currentUser;
    }

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return AuthenticatedUser.class.equals(parameter.getParameterType());
    }

    @Override
    public AuthenticatedUser resolveArgument(
            MethodParameter parameter,
            ModelAndViewContainer mavContainer,
            NativeWebRequest webRequest,
            WebDataBinderFactory binderFactory) {
        return currentUser.get();
    }
}
