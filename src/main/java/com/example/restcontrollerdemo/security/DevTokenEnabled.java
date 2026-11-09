package com.example.restcontrollerdemo.security;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

/** Registers the bean only when {@code app.dev-token.enabled} (env {@code DEV_TOKEN_ENABLED}) is true. Off by default. */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@ConditionalOnProperty(name = "app.dev-token.enabled", havingValue = "true")
public @interface DevTokenEnabled {}
